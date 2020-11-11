package ir.hnaderi.fsm4s.common.persistence.pg

import cats.implicits._
import io.circe.Codec
import io.circe.Codec
import ir.hnaderi.fsm4s.common.OutboxPublisher
import cats.effect.Sync
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import fs2.Stream
import doobie.Fragment
import java.time.ZonedDateTime
import io.circe.Json
import doobie.util.Put
import java.time.Instant

import doobie.implicits.javasql._
import java.sql.Timestamp
import org.postgresql.util.PGobject
import cats.data.NonEmptyList
import scala.concurrent.duration._
import ir.hnaderi.fsm4s.common.OutboxConsumer
import io.circe.Decoder
import io.circe.Encoder
import ir.hnaderi.fsm4s.common.SnapshotRepository
import ir.hnaderi.fsm4s.common.SnapshotData
import org.jline.utils.DiffHelper

object SnapshotStore {

  object Helpers {
    def tableName: Fragment = Fragment.const("snapshots")
    def indexName: Fragment =
      Fragment.const(s"snapshots_seqnr_idx")
  }

  def setup: ConnectionIO[Unit] =
    Queries.setup.run.void

  import scala.reflect.runtime.universe.TypeTag
  def setupApply[I: Put, T: Codec: TypeTag]
      : ConnectionIO[SnapshotRepository[ConnectionIO, I, T]] =
    setup.as(apply[I, T])

  def apply[I: Put, T: Codec: TypeTag]: SnapshotRepository[ConnectionIO, I, T] =
    new SnapshotRepository[ConnectionIO, I, T] {
      implicit val get: Get[T] = jsonDataGet[T]
      implicit val put: Put[T] = jsonPut.contramap(Encoder[T].apply)

      override def save(
          id: I,
          oldVersion: Long,
          newVersion: Long,
          t: T
      ): ConnectionIO[Boolean] =
        Queries.save(oldVersion, newVersion, t).run.map(_ === 1)

      override def load(id: I): ConnectionIO[Option[SnapshotData[T]]] =
        Queries.load[T].option

    }

  object Queries {
    import io.circe.syntax._

    def save[T: Put](lastVersion: Long, newVersion: Long, data: T): Update0 =
      sql"""
       update ${Helpers.tableName}
       set data = $data, version = $newVersion
       where version = $lastVersion
      """.update

    def load[T: Get]: Query0[SnapshotData[T]] =
      sql"""
      select version, data from ${Helpers.tableName} limit 1
      """.query[SnapshotData[T]]

    def setup: Update0 = {
      val table = Helpers.tableName
      val index = Helpers.indexName
      sql"""
      create table if not exists $table (
        version bigint not null,
        data jsonb not null
      );
      -- create index if not exists $index on $table (seqnr, time);
      """.update
    }
  }
}
