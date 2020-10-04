package ir.hnaderi.fsm4s.common

import fs2.Stream
import fs2.Pipe
import DateTime._
import cats.data.NonEmptyList

trait EventStorePublisher[F[_], T] {
  def append(time: DateTime, events: NonEmptyList[T]): F[Long]
}

trait EventStoreConsumer[F[_], T] {
  def messages: Stream[F, EventMessage[T]]
  def allAfter(seqNr: Long): Stream[F, EventMessage[T]]
  def commit(seqNr: Long): F[Unit]
}

final case class EventMessage[T](
    seqNr: Long,
    time: DateTime,
    payload: T
)
