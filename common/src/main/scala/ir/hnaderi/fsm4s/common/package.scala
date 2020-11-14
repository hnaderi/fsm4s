package ir.hnaderi.fsm4s

import cats.data._

package object common {
  type Decision[L, E] = ValidatedNec[L, NonEmptyChain[E]]
  type Decider[S, M, L, E] = (Option[S], M) => Decision[L, E]

}
