package ir.hnaderi.fsm4s.common

import fs2.Stream
import fs2.Pipe
import DateTime._

trait OutboxPublisher[F[_], T] {
  def append(msg: T, time: DateTime): F[Unit]
}

trait OutboxConsumer[F[_], T] {
  def messages: Stream[F, (Long, T)]
  def commit(id: Long): F[Unit]
}
