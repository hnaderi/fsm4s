// package ir.hnaderi.fsm4s.common

// import ir.hnaderi.fsm4s.common.Decision

// object DSL {
//   import cats.implicits._
//   import cats.data._

//   final class DecisionDSL[S, E, C, L] {
//     type F[T] = Action[S, C, L, T]
//     type D = F[Decision[E]]
//     type App = FSMApp.Aux[S, E, C, L]

//     def log(l: L): F[Unit] = RWS.tell(List(l))
//     def logAll(l: L*): F[Unit] = RWS.tell(l.toList)

//     def next: F[C] = RWS.ask

//     def read: F[S] = RWS.get

//     def pure[T](t: T): F[T] = RWS.pure(t)

//     def unit: F[Unit] = pure(())

//     def accept(ev: E, evs: E*): D = pure(Accept(NonEmptyList.of(ev, evs: _*)))
//     def reject(reason: String): D = pure(Reject(reason))
//   }

//   final case class Builder[S, E, C, L]() {
//     def withState[S2]: Builder[S2, E, C, L] = copy()
//     def withEvent[E2]: Builder[S, E2, C, L] = copy()
//     def withInput[C2]: Builder[S, E, C2, L] = copy()
//     def withLog[L2]: Builder[S, E, C, L2] = copy()

//     def build: DecisionDSL[S, E, C, L] = new DecisionDSL()
//   }

//   def builder: Builder[Nothing, Nothing, Nothing, String] = Builder()
// }
