package ir.hnaderi.fsm4s.common.persistence

import cats.implicits._
import io.circe.Codec
import java.time.ZonedDateTime
import cats.data.NonEmptyList
import fs2.Stream
import cats.effect.Sync
import ir.hnaderi.fsm4s.common._
import doobie._
import doobie.implicits._

final class EventSourcedBackend[F[_]: Sync, S: Codec, E: Codec, I: Meta](
    trx: Transactor[F],
    publisher: EventStorePublisher[ConnectionIO, E],
    consumer: EventStoreConsumer[ConnectionIO, E],
    idempStore: ir.hnaderi.fsm4s.common.IdempotencyStore[ConnectionIO, I],
    snapshotRepo: SnapshotRepository[ConnectionIO, S]
) extends EventSourcedStorage[F, S, E, I] {
  def accepted(
      time: ZonedDateTime,
      events: NonEmptyList[E],
      cmdId: I
  ): F[Option[Long]] =
    idempStore
      .append(cmdId)
      .ifM(
        publisher.append(time, events).map(_.some),
        FC.pure(Option.empty[Long])
      )
      .transact(trx)
  def getAllProcessedMessageId: F[List[I]] = idempStore.all.transact(trx)
  def cleanUpProcessedIds(maxToHold: Int): F[Unit] =
    idempStore.clean(maxToHold).transact(trx)
  def getAllAfter(seqNr: Long): Stream[F, EventMessage[E]] =
    consumer.allAfter(seqNr).transact(trx)
  def saveSnapshot(oldVersion: Long, newVersion: Long, s: S): F[Boolean] =
    snapshotRepo
      .save(oldVersion, newVersion, s)
      .transact(trx)
  def loadSnapshot: F[Option[SnapshotData[S]]] =
    snapshotRepo.load.transact(trx)
}
