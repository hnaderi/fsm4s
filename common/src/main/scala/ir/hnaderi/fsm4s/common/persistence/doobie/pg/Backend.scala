package ir.hnaderi.fsm4s.common.persistence.pg

import cats.implicits._
import cats.effect.Sync
import doobie._
import doobie.implicits._
import ir.hnaderi.fsm4s.common._
import io.circe.Codec
import ir.hnaderi.fsm4s.common.persistence.EventSourcedBackend
import scala.reflect.runtime.universe.TypeTag

object Backend {
  def eventSourced[F[_]: Sync, S: Codec : TypeTag, E: Codec, I: Meta](
      name: StoreName
  )(
      trx: Transactor[F]
  ): F[EventSourcedStorage[F, S, E, I]] =
    for {
      publisher <- EventStore.setupPublisher[E](name).transact(trx)
      idempStore <- IdempotencyStore.applySetup[I](name).transact(trx)
      snapshotRepo <- SnapshotStore.setupApply[S].transact(trx)
    } yield new EventSourcedBackend(trx, publisher, idempStore, snapshotRepo)
}
