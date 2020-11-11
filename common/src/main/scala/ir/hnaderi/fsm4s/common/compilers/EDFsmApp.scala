// package ir.hnaderi.fsm4s.compilers.eda

// import ir.hnaderi.fsm4s.common._
// import ir.hnaderi.fsm4s.data._

// import cats.implicits._
// import cats.data.Validated.Invalid
// import cats.data.Validated.Valid
// import cats.data._

// import fs2.Pipe
// import fs2.Stream
// import Stream._

// import fs2.concurrent.SignallingRef
// import cats.effect.Concurrent
// import cats.effect.concurrent.Semaphore
// import io.odin.consoleLogger
// import io.odin.Logger

// object EDFsmApp {

//   def apply[F[_]: Concurrent]: EDFSMAppBuilder[F] = new EDFSMAppBuilder[F]

//   final class EDFSMAppBuilder[F[_]: Concurrent] {
//     def from[S, I, C, L, E, IE](
//         decider: Decider[S, C, L, E],
//         transitor: Transitor[S, E, IE, ValidatedNec[L, Unit]]
//     )(initialState: S): F[FSM[F, S, C, Transition[L, E, IE]]] =
//       for {
//         s <- SignallingRef[F, S](initialState)
//         w <- Semaphore[F](1)
//       } yield new FSM[F, S, C, Transition[L, E, IE]] {

//         override def updates: Stream[F, S] = s.discrete

//         override def input: Pipe[F, C, Transition[L, E, IE]] =
//           commands =>
//             for {
//               cmd <- commands
//               _ <- Stream.bracket(w.acquire)(_ => w.release)
//               state <- eval(s.get)
//               decison = decider(state, cmd)
//               transition <- decison match {
//                 case Invalid(e @ _) => emit(Transition.Impossible(e.head))
//                 case Valid(a) =>
//                   val (nextState @ _, ievs, result) =
//                     a.foldLeft((state, Chain.empty[IE], ().validNec[L])) {
//                       case ((s, ies, lRes), e) =>
//                         val (ievs, ns, res) = transitor.run(e, s).value
//                         (ns, ies ++ ievs, lRes *> res)
//                     }

//                   result match {
//                     case Invalid(conflicts) =>
//                       emit(Transition.Impossible(conflicts.head))
//                     case Valid(_) =>
//                       eval(s.set(nextState)).as(Transition.Next(a, ievs.toList))
//                   }
//               }
//             } yield transition

//       }
//   }

// }
