package ir.hnaderi.fsm4s
package data

import cats.data.NonEmptyList

sealed trait Transition[+Er, +E] extends Serializable with Product

object Transition {

  final case class Next[E](
      events: NonEmptyList[E]
  ) extends Transition[Nothing, E]

  final case class Impossible[Er](error: Er) extends Transition[Er, Nothing]
}
