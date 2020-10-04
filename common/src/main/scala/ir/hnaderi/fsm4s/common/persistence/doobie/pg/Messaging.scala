package ir.hnaderi.fsm4s.common.persistence.pg

import cats.implicits._
import scala.concurrent.duration.FiniteDuration
import fs2.Stream
import Stream._
import doobie._
import doobie.postgres._
import doobie.implicits._
import doobie.postgres.implicits._
import cats.effect.Timer
import cats.effect.Resource
import org.postgresql.PGNotification
import cats.data.NonEmptyList

final class Messaging(timer: Timer[ConnectionIO]) {
  implicit val t: Timer[ConnectionIO] = timer

  /**
    * Maintains connection as a channel ready for Postgres notification
    */
  def session(name: String): Resource[ConnectionIO, Unit] =
    Resource.make(PHC.pgListen(name) *> HC.commit)(_ =>
      PHC.pgUnlisten(name) *> HC.commit
    )

  /**
    * Stream of notifications for channel name that've published max poll interval ago
    * Note that poll rate specifies commit rate.
    */
  def batchedNotifications(
      name: String,
      pollInterval: FiniteDuration
  ): Stream[ConnectionIO, NonEmptyList[PGNotification]] =
    for {
      _ <- resource(session(name))
      _ <- awakeEvery[ConnectionIO](pollInterval)
      _ <- eval(cats.effect.IO(println("Polling")).to[ConnectionIO])
      ns <- eval(PHC.pgGetNotifications <* HC.commit)
        .mapFilter(NonEmptyList.fromList)
    } yield ns

  /**
    * Like batchedNotifications, but flattened
    */
  def notifications(
      name: String,
      pollInterval: FiniteDuration
  ): Stream[ConnectionIO, PGNotification] =
    batchedNotifications(name, pollInterval).flatMap(nel => emits(nel.toList))

  /**
    * Receives published data on channel
    */
  def subscribe(
      name: String,
      pollInterval: FiniteDuration
  ): Stream[ConnectionIO, String] =
    notifications(name, pollInterval).map(_.getParameter())
}

object Messaging {
  def apply(timer: Timer[ConnectionIO]): Messaging =
    new Messaging(timer)
}
