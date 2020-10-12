package ir.hnaderi.fsm4s

import cats.data._

package object common {
  // type Transitor[S, E, IE, Err] = (S, E) => Transition[S, Err, IE]

  type Decision[L, E] = ValidatedNec[L, NonEmptyList[E]]
  type Decider[S, M, L, E] = (S, M) => Decision[L, E]

}
