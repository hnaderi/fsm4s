package ir.hnaderi.fsm4s.common.persistence.pg

import cats.implicits._
import cats.effect.Sync
import doobie._
import doobie.implicits._
import ir.hnaderi.fsm4s.common._
import io.circe.Codec
import scala.reflect.runtime.universe.TypeTag
import cats.data.NonEmptyChain
import ir.hnaderi.fsm4s.eventsourcing._
import fs2.Stream
import fs2.Pull
import fs2.Pipe
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.effect.Clock
import cats.effect.Resource
import cats.effect.Concurrent
import Resource._

object Backend {
  def eventsourced[
      F[_]: Concurrent: Clock,
      STATE: Codec: TypeTag,
      EVENT: Codec: TypeTag,
      ID: IdCodec,
      CmdID: Meta
  ](
      name: StoreName,
      transitor: Transitor[STATE, EVENT, Throwable]
  )(
      trx: Transactor[F]
  ): Resource[F, EventSourcedBackend2[F, STATE, EVENT, ID, CmdID]] = {
    // implicit val idPut: Put[ID] = Put[String].contramap(IdCodec[ID].encode)
    for {
      pub <- liftF(EventStore.setupPublisher[ID, EVENT](name).transact(trx))
      cmdIdStore <- liftF(
        IdempotencyStore.applySetup[CmdID](name).transact(trx)
      )
      // snapshot <- liftF(SnapshotStore.setupApply[ID, STATE].transact(trx))
    } yield new PGEventSourcedBackend[F, STATE, EVENT, ID, CmdID](
      transitor,
      pub,
      cmdIdStore,
      // snapshot,
      trx
    )
  }
}

final class PGEventSourcedBackend[F[_]: Sync: Clock, STATE, EVENT, ID, CmdID](
    transitor: Transitor[STATE, EVENT, Throwable],
    pub: EventSourcedJournal[ConnectionIO, ID, EVENT],
    cmdIdStore: IdempotencyStore[ConnectionIO, CmdID],
    // snapshot: SnapshotRepository[ConnectionIO, ID, STATE],
    trx: Transactor[F]
) extends EventSourcedBackend2[
      F,
      STATE,
      EVENT,
      ID,
      CmdID
    ] {
  def read(id: ID): F[(Int, Option[STATE])] =
    for {
      // lastSnapshot <- snapshot.load(id).transact(trx)
      // FIXME: add real snapshot mechanism!
      lastSnapshot <- Option.empty[SnapshotData[STATE]].pure[F]
      lastState = lastSnapshot.map(_.data)
      lastVersion = lastSnapshot.map(_.version.toInt).getOrElse(0)
      hydrated <-
        pub
          .read(id)
          .through(rehydrate(lastVersion, lastState))
          .compile
          .last
          .transact(trx)
      out = hydrated match {
        case Some((version, state)) => (version, state.some)
        case None                   => (0, None)
      }
    } yield out

  def history(id: ID): Stream[F, (Int, STATE)] =
    pub.read(id).through(rehydrate(0, None)).transact(trx)

  private def rehydrate[M[_]: Sync](
      initialVersion: Int,
      initialState: Option[STATE]
  ): Pipe[M, EventMessage[EVENT], (Int, STATE)] =
    _.evalScan((initialVersion, initialState)) {
      case ((version, state), msg) =>
        transitor(state, msg.payload) match {
          case Left(failure)    => Sync[M].raiseError(failure)
          case Right(nextState) => (version + 1, nextState.some).pure[M]
        }
    }.collect {
      case (version, Some(state)) => (version, state)
    }

  def accept(
      version: Int,
      aggId: ID,
      events: NonEmptyChain[EVENT],
      refCmd: CmdID
  ): F[Boolean] =
    for {
      now <- DateTime.now[F]
      persisted <- (
          pub.append(version, now, aggId, events) >>
            cmdIdStore
              .append(refCmd)
              .ifM(FC.pure(true), FC.rollback.as(false))
      ).transact(trx)
    } yield persisted
}
