package ir.hnaderi.fsm4s.common

import fs2.Stream
import fs2.Pipe
import cats.data.ValidatedNec
import cats.data.NonEmptyList

trait FSM[F[_], S, C, E] {
  def updates: Stream[F, S]
  def input: Pipe[F, C, ValidatedNec[String, NonEmptyList[E]]]
}
