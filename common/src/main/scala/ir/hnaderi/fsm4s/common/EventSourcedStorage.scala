package ir.hnaderi.fsm4s.common

import cats.data.NonEmptyList
import java.time.ZonedDateTime
import fs2.Stream

trait EventSourcedStorage[F[_], S, E, I] {
  def accepted(
      time: ZonedDateTime,
      events: NonEmptyList[E],
      cmdId: I
  ): F[Option[Long]]
  def getAllProcessedMessageId: F[List[I]]
  def cleanUpProcessedIds(maxToHold: Int): F[Unit]
  def getAllAfter(seqNr: Long): Stream[F, EventMessage[E]]
  def saveSnapshot(oldVersion: Long, newVersion: Long, s: S): F[Boolean]
  def loadSnapshot: F[Option[SnapshotData[S]]]
}
