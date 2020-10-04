package ir.hnaderi.fsm4s

import fs2.Pipe
import fs2.Stream
import cats.data._

package object common {
  type Transition[S, E] = (S, E) => S
  // type Action[S, M, L, T] = RWS[M, List[L], S, T]
  // type Decider[S, M, L, E] = Action[S, M, L, Decision[E]]

  type Decision[L, E] = ValidatedNec[L, NonEmptyList[E]]
  type Decider[S, M, L, E] = (S, M) => Decision[L, E]
}
