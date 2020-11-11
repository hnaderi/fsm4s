package ir.hnaderi.fsm4s.common.persistence.pg

import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import io.circe.{Encoder, Decoder}
import ir.hnaderi.fsm4s.common.DateTime._
import io.circe.syntax._
import scala.reflect.macros.util.Helpers
import ir.hnaderi.fsm4s.common.EventStoreConsumer
import ir.hnaderi.fsm4s.common._
import fs2.Stream
import scala.concurrent.duration.FiniteDuration
import cats.effect.concurrent.Ref
import shapeless.test.TypeTrace
import io.odin.consoleLogger
import io.odin.Logger
import ir.hnaderi.fsm4s.common.persistence.pg.Outbox.Queries
import cats.data.NonEmptyChain
import doobie.refined.implicits._
import scala.reflect.runtime.universe.TypeTag
import io.circe.Codec
import cats.MonadError

object EventStore {
  private object Helpers {
    def tableStringNameFor(name: StoreName) = s"${name.value}_journal"
    def versionsTableStringNameFor(name: StoreName) =
      s"${name.value}_journal_versions"
    def tableFor(name: StoreName) = Fragment.const(tableStringNameFor(name))
    def versionsTableFor(name: StoreName) =
      Fragment.const(versionsTableStringNameFor(name))
    def channelNameFor(name: StoreName) = s"${name.value}_events"
    val consumersTable = Fragment.const("consumers")
    def seqIndexNameFor(name: StoreName) =
      Fragment.const(s"${name.value}_journal_seqnr_idx")
    def versionIndexNameFor(name: StoreName) =
      Fragment.const(s"${name.value}_journal_version_idx")
    def idIndexNameFor(name: StoreName) =
      Fragment.const(s"${name.value}_journal_id_idx")
    def timeIndexNameFor(name: StoreName) =
      Fragment.const(s"${name.value}_journal_time_idx")
  }

  object Queries {
    def setupJournal(name: StoreName): Update0 = {
      val table = Helpers.tableFor(name)
      val versionsTable = Helpers.versionsTableFor(name)
      val seqIndex = Helpers.seqIndexNameFor(name)
      val timeIndex = Helpers.timeIndexNameFor(name)
      val versionIndex = Helpers.versionIndexNameFor(name)
      val idIndex = Helpers.idIndexNameFor(name)

      sql"""
      create table if not exists $table (
       seqnr bigserial primary key,
       version integer,
       id varchar,
       "time" timestamptz NOT NULL,
       "event" jsonb NOT NULL
     );

      create index if not exists $seqIndex  ON $table (seqnr);
      create index if not exists $versionIndex  ON $table (version);
      create index if not exists $idIndex  ON $table (id);
      create index if not exists $timeIndex ON $table ("time");

      create table if not exists $versionsTable (
        id varchar NOT NULL primary key,
        version integer NOT NULL
      );
    """.update
    }

    def setupConsumers: Update0 = sql"""
    create table if not exist ${Helpers.consumersTable} (
     "name" varchar NOT NULL,
      seqnr bigint NOT NULL
    );
    create unique index if not exists consumers_name_idx ON ${Helpers.consumersTable} ("name");
    """.update

    def append[I: Put, T: Put](
        name: StoreName,
        id: I,
        time: DateTime
    ): Update[(Int, T)] = {
      val q = s"""
      insert into ${Helpers.tableStringNameFor(name)} (time, id, version, event)
      values (?, ?, ?, ?)
      """
      Update[(DateTime, I, Int, T)](q).contramap {
        case (version, event) => (time, id, version, event)
      }
    }

    /**
      * Optimistic locking
      * Insert a new entry if last version was 0
      * Updates otherwise
      *
     * returns number of rows updated
      *
     * possible outcomes:
      *   - primary key conflict, when tries to insert existing entry
      *   - return 0, no updated occurred, so it should be regarded as failure
      *   - return 1, exactly one entry updated and operation was successful!
      */
    def updateVersion[I: Put](
        name: StoreName,
        id: I,
        lastVersion: Int,
        newVersion: Int
    ): Update0 = {
      val table = Helpers.versionsTableFor(name)

      if (lastVersion === 0)
        sql"insert into $table (id, version) values ($id, $newVersion)".update
      else
        sql"""
      update $table
      set version = $newVersion
      where id = $id
        and version = $lastVersion
      """.update
    }

    def commit(name: ConsumerName, id: Long): Update0 =
      sql"""
      update ${Helpers.consumersTable}
      set id = $id
      where name = $name
      """.update

    def queryAfterForId[I: Put, T: Get](
        name: StoreName,
        id: I
    ): Query0[EventMessage[T]] =
      sql"""
      select seqnr, version, time, event
      from ${Helpers.tableFor(name)}
      where id = $id
      """.query[EventMessage[T]]

    def queryAfterForIdAfter[I: Put, T: Get](
        name: StoreName,
        id: I,
        version: Int
    ): Query0[EventMessage[T]] =
      sql"""
      select seqnr, version, time, event
      from ${Helpers.tableFor(name)}
      where id = $id
      and   version > $version
      """.query[EventMessage[T]]

    def queryAllAfter[I: Get, T: Get](
        name: StoreName,
        seqNr: Long
    ): Query0[ConsumedEvent[I, T]] =
      sql"""
      select seqnr, version, time, id, event
      from ${Helpers.tableFor(name)}
      where seqnr > $seqNr
      """.query[ConsumedEvent[I, T]]

    def lastConsumerState(name: ConsumerName): Query0[Long] =
      sql"select seqnr from ${Helpers.consumersTable} where name=$name"
        .query[Long]
  }

  def publisher[I: IdCodec, T: Codec: TypeTag](
      name: StoreName
  ): EventSourcedJournal[ConnectionIO, I, T] =
    new EventSourcedJournal[ConnectionIO, I, T] {
      implicit val dataPut: Put[T] = jsonPut.contramap(_.asJson)
      implicit val dataGet: Get[T] = jsonDataGet[T]
      private val idCodec = implicitly[IdCodec[I]]
      implicit val idPut: Put[I] = Put[String].contramap(idCodec.encode)

      def append(
          version: Int,
          time: DateTime,
          id: I,
          events: NonEmptyChain[T]
      ): ConnectionIO[Int] = {
        val toInsert = events.zipWithIndex.map {
          case (event, index) => (version + index + 1, event)
        }
        for {
          // Notify listeners if transaction succeded
          _ <- PHC.pgNotify(Helpers.channelNameFor(name))
          totalAppended <-
            Queries
              .append[I, T](name, id, time)
              .updateMany(toInsert)
          expectedSize = events.size.toInt
          _ <-
            FC.pure(totalAppended) // ensure all events are appended
              .ensure(Errors.UnCompletedAppend(expectedSize, totalAppended))(
                _ === expectedSize
              )
          _ <-
            Queries
              .updateVersion(name, id, version, toInsert.last._1)
              .run
              .onUniqueViolation(FC.pure(0))
              .ensure(Errors.VersionConflict)(_ === 1)
        } yield totalAppended
      }

      def read(id: I): Stream[ConnectionIO, EventMessage[T]] =
        Queries.queryAfterForId[I, T](name, id).stream
      def readAfter(
          id: I,
          version: Int
      ): Stream[ConnectionIO, EventMessage[T]] =
        Queries.queryAfterForIdAfter[I, T](name, id, version).stream
    }

  def consumer[I: IdCodec: TypeTag, T: Decoder: TypeTag](
      source: StoreName,
      name: ConsumerName,
      messaging: Messaging,
      pollInterval: FiniteDuration,
      logger: Logger[ConnectionIO]
  ): EventStoreConsumer[ConnectionIO, I, T] =
    new EventStoreConsumer[ConnectionIO, I, T] {
      implicit val eventGet: Get[T] = jsonDataGet[T]
      private val idCodec = implicitly[IdCodec[I]]
      implicit val idGet: Get[I] = Get[String].temap(idCodec.decode)

      import Stream._
      override def messages: Stream[ConnectionIO, ConsumedEvent[I, T]] =
        for {
          _ <- eval(logger.debug("loading last state..."))
          initialState <- eval(getLastState)
          _ <- eval(
            logger
              .debug(s"loaded last state! sequence nr. ${initialState.show}")
          )
          state <- eval(Ref.of[ConnectionIO, Long](initialState))
          ns <- messaging.batchedNotifications(
            Helpers.channelNameFor(source),
            pollInterval
          )
          _ <- eval(
            logger.debug(s"received ${ns.size.show} update notification(s)!")
          )
          lastState <- eval(state.get)
          event <- allAfter(lastState)
          _ <- eval(logger.debug(s"received event ${event.seqNr.show}!"))
          _ <- eval(state.set(event.seqNr))
        } yield event

      private def getLastState: ConnectionIO[Long] =
        Queries.lastConsumerState(name).unique

      override def allAfter(
          seqNr: Long
      ): Stream[ConnectionIO, ConsumedEvent[I, T]] =
        Queries.queryAllAfter[I, T](source, seqNr).stream

      override def commit(id: Long): doobie.ConnectionIO[Unit] =
        Queries.commit(name, id).run.void
    }

  def setupPublisher[I: IdCodec, T: Codec: TypeTag](
      name: StoreName
  ): ConnectionIO[EventSourcedJournal[ConnectionIO, I, T]] =
    Queries.setupJournal(name).run.as(publisher(name))

  sealed trait Errors extends Throwable
  object Errors {
    final case class UnCompletedAppend(expected: Int, actual: Int)
        extends Exception(
          s"Append failed! expected to append all ${expected
            .toString()} items, but only ${actual.toString()} were appended."
        )
        with Errors
    final case object VersionConflict
        extends Exception("Operation failed due to version conflict!")
        with Errors

  }
}
