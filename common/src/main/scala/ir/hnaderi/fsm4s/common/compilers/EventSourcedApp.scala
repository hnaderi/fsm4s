package ir.hnaderi.fsm4s.common.compilers

import cats.implicits._
import fs2.Stream
import ir.hnaderi.fsm4s.common.FSMApp
import ir.hnaderi.fsm4s.common.EventStorePublisher
import ir.hnaderi.fsm4s.common.SnapshotRepository
import ir.hnaderi.fsm4s.common.SnapshotData
import cats.effect.Sync
import cats.effect.concurrent.Ref
import io.odin.Logger
import ir.hnaderi.fsm4s.common.FSM
import fs2.concurrent.SignallingRef
import cats.effect.ContextShift
import cats.effect.ConcurrentEffect
import cats.effect.Concurrent
import ir.hnaderi.fsm4s.common.Decision
import ir.hnaderi.fsm4s.common.EventSourcedStorage
import cats.mtl.syntax.state
import ir.hnaderi.fsm4s.common.CommandHandlerState
import cats.effect.Timer
import ir.hnaderi.fsm4s.common.DateTime
import fs2.Pipe
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.data._

object EventSourcedApp {
  def apply[F[_]: Concurrent: ContextShift: Timer, S, E, M, I](
      app: FSMApp.Aux[S, E, M, String],
      initial: S
  )(
      storage: EventSourcedStorage[F, S, E, I],
      logger: Logger[F]
  ): F[FSM[F, S, (I, M), E]] =
    for {
      last <- storage.loadSnapshot
      (initialVersion, initialState) <- last match {
        case Some(SnapshotData(version, snapshot)) =>
          logger
            .info(s"Snapshot with version ${version.show} loaded!")
            .as((version, snapshot))
        case None =>
          logger
            .info(s"No snapshot found! starting from initial state.")
            .as((Long.MinValue, initial))
      }

      state <- SignallingRef[F, CommandHandlerState[S, I]](
        CommandHandlerState(initialVersion, initialState, ???)
      )
    } yield new FSM[F, S, (I, M), E] {

      import Stream._

      override def updates: Stream[F, S] = state.discrete.map(_.lastState)

      override def input: Pipe[F, (I, M), ValidatedNec[String, NonEmptyList[E]]] =
        commands =>
          for {
            (id, cmd) <- commands
            CommandHandlerState(currentVersion, currentState, processedIds) <-
              eval(state.get)
            // _ = processedIds.dropRight((100 - processedIds.length) min 0)
            _ <- idempotencyCheck(processedIds, id)
            decision = app.decide(currentState, cmd)
            _ <- decision match {
	            case Invalid(reasons) =>
                eval(logger.debug(s"rejected command due to: ${reasons.mkString_("\n")}"))
	            case Valid(events) =>
                val newState = events.foldLeft(currentState)(app.transition)
                eval {
                  DateTime.now[F] >>=
                    (storage.accepted(_, events, id)) >>= {
                    case Some(latestVersion) =>
                      state.update(
                        _.withVersion(latestVersion)
                          .withState(newState)
                          .addProcessed(id)
                      )
                    case None =>
                      logger.warn(
                        """Transition rejected after acceptance due to idempotency!
                             This may occur when system crashes after processing, but before it can acknowledge message source!""".stripMargin
                      )
                  }
                }
            }
          } yield decision

      private def idempotencyCheck(processedIds: List[I], id: I) =
        if (processedIds.contains(id))
          eval(
            logger.info(s"Ignoring redundant message due to idempotency!")
          ) >> empty
        else
          eval(logger.info(s"Received new command!"))

    }
}
