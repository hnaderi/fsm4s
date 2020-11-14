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
import fs2.Pipe
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.effect.Clock
import cats.effect.Resource
import cats.effect.Concurrent
import Resource._
import io.circe.Decoder
import cats.effect.Timer
import cats.arrow.FunctionK
import cats.effect.LiftIO
import scala.concurrent.duration.FiniteDuration
import cats.effect.Effect

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

  import cats.effect.implicits._
  import cats.~>
  private def fK[F[_]: Effect]: FunctionK[F, ConnectionIO] =
    new (F ~> ConnectionIO) {
      def apply[A](fa: F[A]): doobie.ConnectionIO[A] = fa.toIO.to[ConnectionIO]
    }

  private def timer[F[_]: Effect: Timer]: Timer[ConnectionIO] =
    Timer[F].mapK(fK[F])

  def consumer[F[
      _
  ]: Effect: Timer: LiftIO, ID: IdCodec: TypeTag, EVENT: Decoder: TypeTag](
      name: StoreName,
      config: ConsumerConfig
  )(trx: Transactor[F]): F[EventStoreConsumer[F, ID, EVENT]] = {
    implicit val timerInst: Timer[ConnectionIO] = timer
    for {
      _ <- EventStore.setupConsumers.transact(trx)
      logger = io.odin.consoleLogger[ConnectionIO]()
      m = Messaging(timerInst)
      c = EventStore.consumer(name, config.name, m, config.pollInterval, logger)
    } yield new PGEventConsumer[F, ID, EVENT](c, trx)
  }

  final case class ConsumerConfig(
      name: ConsumerName,
      pollInterval: FiniteDuration
  )

}
