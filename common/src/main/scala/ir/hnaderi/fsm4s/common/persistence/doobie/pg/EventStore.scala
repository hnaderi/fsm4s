package ir.hnaderi.fsm4s.common.persistence.pg

import cats.implicits._
import ir.hnaderi.fsm4s.common.EventStorePublisher
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
import cats.data.NonEmptyList
import doobie.refined.implicits._

object EventStore {
  private object Helpers {
    def tableStringNameFor(name: StoreName) = s"${name.value}_journal"
    def tableFor(name: StoreName) = Fragment.const(tableStringNameFor(name))
    def channelNameFor(name: StoreName) = s"${name.value}_events"
    val consumersTable = Fragment.const("consumers")
    def seqIndexNameFor(name: StoreName) =
      Fragment.const(s"${name.value}_journal_seqnr_idx")
    def timeIndexNameFor(name: StoreName) =
      Fragment.const(s"${name.value}_journal_time_idx")
  }

  object Queries {
    def setupJournal(name: StoreName): Update0 = {
      val table = Helpers.tableFor(name)
      val seqIndex = Helpers.seqIndexNameFor(name)
      val timeIndex = Helpers.timeIndexNameFor(name)

      sql"""
      create table if not exists ${Helpers.tableFor(name)} (
       seqnr bigserial primary key,
       "time" timestamptz NOT NULL,
       "event" jsonb NOT NULL
     );

      create index if not exists $seqIndex  ON $table (seqnr);
      create index if not exists $timeIndex ON $table ("time");
    """.update
    }

    def setupConsumers: Update0 = sql"""
    create table if not exist ${Helpers.consumersTable} (
     "name" varchar NOT NULL,
      seqnr bigint NOT NULL
    );
    create unique index if not exists consumers_name_idx ON ${Helpers.consumersTable} ("name");
    """.update

    def append[T: Put](name: StoreName, time: DateTime): Update[T] = {
      val q = s"""
      insert into ${Helpers.tableStringNameFor(name)} (time, event)
      values (? , ?)
      """
      Update[(DateTime, T)](q).contramap(t => (time, t))
    }

    def commit(name: ConsumerName, id: Long): Update0 =
      sql"""
      update ${Helpers.consumersTable}
      set id = $id
      where name = $name
      """.update

    def queryAfter[T: Get](
        name: StoreName,
        seqNr: Long
    ): Query0[EventMessage[T]] =
      sql"""
      select seqnr, time, event
      from ${Helpers.tableFor(name)}
      where seqnr > $seqNr
      """.query[EventMessage[T]]

    def lastConsumerState(name: ConsumerName): Query0[Long] =
      sql"select seqnr from ${Helpers.consumersTable} where name=$name"
        .query[Long]
  }

  def publisher[T: Encoder](
      name: StoreName
  ): EventStorePublisher[ConnectionIO, T] =
    new EventStorePublisher[ConnectionIO, T] {
      implicit val dataPut: Put[T] = jsonPut.contramap(_.asJson)

      override def append(
          time: DateTime,
          events: NonEmptyList[T]
      ): ConnectionIO[Long] =
        PHC.pgNotify(Helpers.channelNameFor(name)) >>
          Queries
            .append[T](name, time)
            .updateManyWithGeneratedKeys[Long]("seqnr")(events)
            .compile
            .toList
            .flatMap {
              case head :: tail =>
                FC.pure(tail.foldLeft(head)(_ max _))
              case Nil =>
                FC.raiseError(???)
            }
    }

  import scala.reflect.runtime.universe.TypeTag
  def consumer[T: Decoder: TypeTag](
      source: StoreName,
      name: ConsumerName,
      messaging: Messaging,
      pollInterval: FiniteDuration,
      logger: Logger[ConnectionIO]
  ): EventStoreConsumer[ConnectionIO, T] =
    new EventStoreConsumer[ConnectionIO, T] {

      implicit val eventGet: Get[T] = jsonDataGet[T]
      import Stream._
      override def messages: Stream[ConnectionIO, EventMessage[T]] =
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
      ): Stream[ConnectionIO, EventMessage[T]] =
        Queries.queryAfter[T](source, seqNr).stream

      override def commit(id: Long): doobie.ConnectionIO[Unit] =
        Queries.commit(name, id).run.void
    }

  def setupPublisher[T: Encoder](
      name: StoreName
  ): ConnectionIO[EventStorePublisher[ConnectionIO, T]] =
    Queries.setupJournal(name).run.as(publisher(name))
}
