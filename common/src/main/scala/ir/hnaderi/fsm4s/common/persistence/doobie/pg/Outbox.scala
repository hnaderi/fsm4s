package ir.hnaderi.fsm4s.common.persistence.pg

import cats.implicits._
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

object Outbox {

  object Helpers {
    def tableNameFor(name: String): Fragment = Fragment.const(name)
    def channelNameFor(name: String): String = s"outbox_$name"
    def indexNameFor(name: String): Fragment =
      Fragment.const(s"${name}_seqnr_idx")
  }

  def setup(name: String): ConnectionIO[Unit] =
    Queries.setup(name: String).run.void

  def setupApply[T: Codec](
      name: String
  ): ConnectionIO[OutboxPublisher[ConnectionIO, T]] =
    setup(name).as(apply[T](name))

  def apply[T: Codec](name: String): OutboxPublisher[ConnectionIO, T] =
    new OutboxPublisher[ConnectionIO, T] {
      override def append(msg: T, time: ZonedDateTime): ConnectionIO[Unit] =
        Queries.append(name, msg, time).run.void *> PHC.pgNotify(
          Helpers.channelNameFor(name)
        )
    }

  import scala.reflect.runtime.universe.TypeTag
  def consumer[T: Decoder: TypeTag](
      name: String,
      pollInterval: FiniteDuration,
      messageing: Messaging
  ): OutboxConsumer[ConnectionIO, T] =
    new OutboxConsumer[ConnectionIO, T] {

      implicit val dataGetInstance: Get[T] = jsonDataGet

      override def messages: Stream[ConnectionIO, (Long, T)] =
        for {
          _ <- messageing.batchedNotifications(
            Helpers.channelNameFor(name),
            pollInterval
          )
          next <- getAllNew
        } yield next

      private def getAllNew: Stream[ConnectionIO, (Long, T)] =
        Queries.getAllNew[T](name).stream

      override def commit(id: Long): doobie.ConnectionIO[Unit] =
        Queries.commit(name, id).run.void
    }

  object Queries {
    import io.circe.syntax._

    def getAllNew[T: Get](table: String): Query0[(Long, T)] =
      sql"""
      select seqnr, data
      from ${Helpers.tableNameFor(table)}
      where processed = false
      order by (seqnr, time) asc
      """
        .query[(Long, T)]

    def commit(table: String, id: Long): Update0 = sql"""
      update ${Helpers.tableNameFor(table)}
      set processed = true
      where seqnr <= $id
      """.update

    def append[T: Encoder](
        table: String,
        msg: T,
        time: ZonedDateTime
    ): Update0 =
      sql"""insert into ${Helpers.tableNameFor(table)} (time, data)
            values ($time, ${msg.asJson})""".update

    def setup(name: String): Update0 = {
      val table = Helpers.tableNameFor(name)
      val index = Helpers.indexNameFor(name)
      sql"""
      create table if not exists $table (
        seqnr BIGSERIAL primary key,
        data jsonb not null,
        time timestamptz not null,
        processed bool not null default false
      );
      create index if not exists $index on $table (seqnr, time);
      """.update
    }
  }
}
