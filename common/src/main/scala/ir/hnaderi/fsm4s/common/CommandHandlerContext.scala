// package ir.hnaderi.fsm4s.trader

// import CommandHandler._
// import cats.implicits._
// import fs2.Stream
// import Stream._
// import cats.effect.Sync
// import doobie.util.transactor.Transactor
// import fs2.Pipe
// import ir.hnaderi.fsm4s.trader.domain.Decider
// import ir.hnaderi.fsm4s.common._
// import cats.~>
// import cats.Monad
// import cats.effect.concurrent.Ref
// import cats.effect.Clock
// import io.odin.Logger
// import cats.data.NonEmptyList
// import java.time.ZonedDateTime
// import cats.MonadError
// import cats.data.Ior
// import cats.data.Ior.Both

// final class CommandHandlerContext[
//     F[_]: Sync: Clock,
//     TRX[_]: MonadError[*[_], Exception],
//     W[_]
// ](
//     trx: TRX ~> F,
//     logger: Logger[F]
// ) {

//   final class CommandHandler[S, I, C, E](
//       decider: Decider[S, C, E],
//       projection: Projection[S, E]
//   )(
//       state: Ref[F, CommandHandlerState[S, I]],
//       eventStore: EventStorePublisher[TRX, E],
//       idempStore: IdempotencyStore[TRX, I]
//   )(
//       identifier: Identified[C, I],
//       wrapped: WrappedMessage[W, C]
//   ) {

//     private def persist(
//         time: ZonedDateTime,
//         version: Long,
//         events: NonEmptyList[E],
//         cmdId: I
//     ): TRX[Ior[String, Long]] =
//       idempStore
//         .append(cmdId)
//         .ifM(
//           eventStore.append(time, events).attempt.map {
//             case Right(value) => Ior.right(value)
//             case Left(_)      => Ior.left("Stale read!")
//           },
//           Ior.both("Ignored!", 0L).pure[TRX]
//         )

//     private def goToNextState(s: S, id: I, version: Long): F[Unit] =
//       state.update(_.withState(s).addProcessed(id).withVersion(version))

//     private def assertNewCommand(id: I): Stream[F, Unit] =
//       eval(state.get.map(_.processedIds.contains(id))).flatMap {
//         isProcessedRecently =>
//           if (isProcessedRecently)
//             eval(
//               logger.warn(
//                 "Received command with id that was processed recently! ignoring due to idempotency."
//               )
//             ) >> empty
//           else
//             eval(logger.info("Received new command!"))
//       }

//     private def handle(
//         id: I,
//         currentState: S,
//         currentVersion: Long,
//         projection: Projection[S, E]
//     ): Decision[E] => Stream[F, Unit] = {
//       case Accept(events) =>
//         val newState = events.foldLeft(currentState)(projection)

//         eval {
//           for {
//             _ <- logger.info("Command accepted!")
//             now <- DateTime.now[F]
//             persistResult <- trx(persist(now, currentVersion, events, id))
//             _ <- persistResult match {
//               case Both(_, _) =>
//                 logger.warn(
//                   "Command accepted but had no effect due to idempotency!"
//                 )
//               case cats.data.Ior.Left(_) =>
//                 logger.error(s"STALE READ!!!") >>
//                   Sync[F].raiseError(Error.StaleRead(currentVersion))
//               case cats.data.Ior.Right(latestVersion) =>
//                 goToNextState(newState, id, latestVersion)
//             }
//           } yield ()
//         }
//       case Reject(reason) =>
//         eval(logger.info(s"Command rejected! reason: $reason"))
//     }

//     def pipe: Pipe[F, W[C], (W[C], Decision[E])] =
//       commands =>
//         for {
//           cmd <- commands
//           CommandHandlerState(version, currentState, _) <- eval(state.get)
//           payload = wrapped.unwrap(cmd)
//           id = identifier.id(payload)
//           _ <- assertNewCommand(id)
//           res = decider(currentState, payload)
//           _ <- handle(id, currentState, version, projection)(res)
//         } yield (cmd, res)
//   }
// }

// object CommandHandler {
//   sealed trait Error extends Serializable with Product
//   object Error {
//     final case class StaleRead(version: Long)
//         extends Exception(
//           s"STALE READ! appending event with invalid version [value: ${version.show}] prevented!"
//         )

//   }

// }

// trait Identified[C, I] {
//   def id(c: C): I
// }

// sealed trait WrappedMessage[W[_], T] {
//   def unwrap(msg: W[T]): T
// }

// trait MessageAcknowledge[F[_], W[_]] {
//   def ack[T](msg: W[T]): F[Unit]
//   def nack[T](msg: W[T]): F[Unit]
// }
