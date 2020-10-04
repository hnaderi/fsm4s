// package ir.hnaderi.fsm4s.common

// import CommandHandler._
// import cats.implicits._
// import fs2.Stream
// import Stream._
// import cats.effect.Sync
// import doobie.util.transactor.Transactor
// import fs2.Pipe
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

// final class CommandHandlerBuilder[
//     F[_]: Sync: Clock,
//     TRX[_]: MonadError[*[_], Exception]
// ](
//     trx: TRX ~> F,
//     logger: Logger[F]
// ) {

//   final class CommandHandler[S, E, C, I](
//       context: FSMApp.Aux[S, E, C, I]
//   )(
//       state: Ref[F, CommandHandlerState[S, I]],
//       eventStore: EventStorePublisher[TRX, E],
//       idempStore: IdempotencyStore[TRX, I]
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
//             case Left(_)      => Ior.left(s"Stale read! ${version.show}")
//           },
//           Ior.both("Ignored!", 0L).pure[TRX]
//         )

//     private def goToNextState(s: S, id: I, version: Long): F[Unit] =
//       state.update(_.withState(s).addProcessed(id).withVersion(version))

//     private def assertNewCommand(id: I): F[Boolean] =
//       state.get.map(_.processedIds.contains(id)).flatMap {
//         isProcessedRecently =>
//           if (isProcessedRecently)
//             logger
//               .warn(
//                 "Received command with id that was processed recently! ignoring due to idempotency."
//               )
//               .as(false)
//           else
//             logger.info("Received new command!").as(true)
//       }

//     private def handle(
//         id: I,
//         currentState: S,
//         currentVersion: Long
//     ): Decision[E] => F[Unit] = {
//       case Accept(events) =>
//         val newState = events.foldLeft(currentState)(context.fold)
//         for {
//           _ <- logger.info("Command accepted!")
//           now <- DateTime.now[F]
//           persistResult <- trx(persist(now, currentVersion, events, id))
//           _ <- persistResult match {
//             case Both(_, _) =>
//               logger.warn(
//                 "Command accepted but had no effect due to idempotency!"
//               )
//             case cats.data.Ior.Left(_) =>
//               logger.error(s"STALE READ!!!") >>
//                 Sync[F].raiseError(Error.StaleRead(currentVersion))
//             case cats.data.Ior.Right(latestVersion) =>
//               goToNextState(newState, id, latestVersion)
//           }
//         } yield ()
//       case Reject(reason) =>
//         logger.info(s"Command rejected! reason: $reason")
//     }

//     def func(cmd: C): F[(C, Decision[E])] =
//       for {
//         CommandHandlerState(version, currentState, _) <- state.get
//         id = context.getIdFor(cmd)
//         _ <- assertNewCommand(id)
//         res = context.decide(currentState, cmd)
//         _ <- handle(id, currentState, version)(res)
//       } yield (cmd, res)

//     def pipe: Pipe[F, C, (C, Decision[E])] =
//       _.evalMap(func)
//   }
// }

// // object FSMRunner {

// //   import cats.data.RWST
// //   def apply[F[_], S, E, C, I](
// //       context: FSMApp.Aux[S, E, C, I]
// //   ): FSMRunner[F, CommandHandlerState[S, I], C, E] =
// //     for {
// //       CommandHandlerState(version, currentState, _) <- RWST.get
// //       id = context.getIdFor(cmd)
// //       _ <- assertNewCommand(id)
// //       res = context.decide(currentState, cmd)
// //       _ <- handle(id, currentState, version)(res)
// //     } yield (cmd, res)
// // }

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
