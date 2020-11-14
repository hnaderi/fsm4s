package ir.hnaderi.fsm4s
package eventsourcing

import ir.hnaderi.fsm4s.common._

import cats.implicits._
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.data._

import fs2.Pipe
import fs2.Stream
import Stream._

import fs2.concurrent.SignallingRef
import cats.effect.Concurrent
import cats.effect.concurrent.Semaphore
import io.odin.consoleLogger
import io.odin.Logger
import fs2.concurrent.Queue
import cats.effect.Sync
import cats.effect.Timer
import cats.Show
import ir.hnaderi.fsm4s.common.persistence.pg.Backend
import cats.effect.IO

object CommandHandler {
  def withLogger[F[_]: Sync](logger: Logger[F]): EDFSMAppBuilder[F] =
    new EDFSMAppBuilder[F](logger)
  def apply[F[_]: Sync: Timer]: EDFSMAppBuilder[F] =
    new EDFSMAppBuilder[F](consoleLogger[F]())

  final class EDFSMAppBuilder[F[_]: Sync](logger: Logger[F]) {

    def from[STATE, ID: Show, CMD, REJ: Show, EVENT](
        decider: Decider[STATE, CMD, REJ, EVENT]
    )(
        backend: EventSourcedBackend2[F, STATE, EVENT, ID, String]
    ): CommandHandler[F, String, ID, CMD, REJ] = {
      def handler: CommandHandler[F, String, ID, CMD, REJ] =
        (cmdId: String, id: ID, cmd: CMD) =>
          for {
            (version, state) <- backend.read(id)
            decision = decider(state, cmd)
            out <- decision match {
              case Invalid(errs) =>
                logger
                  .info(
                    s"Command rejected! [client id: $cmdId, aggregate id: ${id.show}]"
                  )
                  .as(errs.invalid)
              case Valid(a) =>
                backend
                  .accept(version, id, a, cmdId)
                  .as(UnitValue) // FIXME: Add Retry conflict
            }
          } yield out

      handler
    }
  }

  private val UnitValue = Validated.validNec(())
}
