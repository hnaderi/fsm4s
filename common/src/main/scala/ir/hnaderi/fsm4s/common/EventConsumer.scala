package ir.hnaderi.fsm4s.common

import fs2.Stream

trait EventConsumer[F[_], I, E] {
  def messages: Stream[F, (Long, I, E)]
  def commit(offset: Long): F[Unit]
}
