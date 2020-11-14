package ir.hnaderi.fsm4s.common.persistence.pg

import cats.effect.Sync
import doobie._
import doobie.implicits._
import ir.hnaderi.fsm4s.common.EventStoreConsumer
import ir.hnaderi.fsm4s.common.ConsumedEvent
import fs2.Stream

final class PGEventConsumer[F[_]: Sync, I, T](
    consumer: EventStoreConsumer[ConnectionIO, I, T],
    trx: Transactor[F]
) extends EventStoreConsumer[F, I, T] {
  def messages: Stream[F, ConsumedEvent[I, T]] = consumer.messages.transact(trx)
  def allAfter(seqNr: Long): Stream[F, ConsumedEvent[I, T]] =
    consumer.allAfter(seqNr).transact(trx)
  def commit(seqNr: Long): F[Unit] = consumer.commit(seqNr).transact(trx)
}
