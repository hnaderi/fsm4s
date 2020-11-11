package ir.hnaderi.fsm4s.common

import fs2.Stream
import fs2.Pipe
import DateTime._
import cats.data.NonEmptyChain

trait EventSourcedJournal[F[_], I, T] {
  def append(
      version: Int,
      time: DateTime,
      id: I,
      events: NonEmptyChain[T]
  ): F[Int]
  def read(id: I): Stream[F, EventMessage[T]]
  def readAfter(id: I, version: Int): Stream[F, EventMessage[T]]
}

trait EventStoreConsumer[F[_], I, T] {
  def messages: Stream[F, ConsumedEvent[I, T]]
  def allAfter(seqNr: Long): Stream[F, ConsumedEvent[I, T]]
  def commit(seqNr: Long): F[Unit]
}

final case class EventMessage[T](
    seqNr: Long,
    version: Int,
    time: DateTime,
    payload: T
)

final case class ConsumedEvent[I, T](
    seqNr: Long,
    version: Int,
    time: DateTime,
    id: I,
    payload: T
)
