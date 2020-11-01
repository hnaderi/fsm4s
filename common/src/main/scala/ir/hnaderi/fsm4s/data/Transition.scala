package ir.hnaderi.fsm4s
package data

import cats.data.NonEmptyList

sealed trait Transition[+Er, +E, +IE] extends Serializable with Product

object Transition {

  final case class Next[E, IE](
      events: NonEmptyList[E],
      transitionEvents: List[IE]
  ) extends Transition[Nothing, E, IE]

  final case class Impossible[Er](error: Er)
      extends Transition[Er, Nothing, Nothing]
}
