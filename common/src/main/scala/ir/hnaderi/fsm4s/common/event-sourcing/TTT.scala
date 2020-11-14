package ir.hnaderi.fsm4s.eventsourcing

import ir.hnaderi.fsm4s.common._

import cats.implicits._
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.data._

import fs2.Pipe
import fs2.Stream
import Stream._

import fs2.concurrent.SignallingRef
import cats.effect.Concurrent
import cats.effect.concurrent.Semaphore
import io.odin.consoleLogger
import io.odin.Logger
import fs2.concurrent.Queue
import cats.effect.Sync
import cats.effect.Timer
import cats.Show
import cats.effect.IO

object TTT {
  import MyEvents.Added
  import MyEvents.Deleted
  import MyEvents.Created
  import MyCommand.New
  import MyCommand.Add
  import MyCommand.Delete

  final case class MyState(total: Int)
  sealed trait MyCommand extends Serializable with Product
  object MyCommand {
    final case class New(initial: Int) extends MyCommand
    final case class Add(delta: Int) extends MyCommand
    final case object Delete extends MyCommand
  }
  sealed trait MyEvents extends Serializable with Product
  object MyEvents {
    final case class Created(initial: Int) extends MyEvents
    final case class Added(delta: Int) extends MyEvents
    final case object Deleted extends MyEvents
  }

  type Id = String
  type ClientId = String

  val decider: Decider[MyState, MyCommand, String, MyEvents] =
    (state, command) => {
      state match {
        case Some(state @ _) =>
          ???
        case None =>
          command match {
            case New(initial) =>
              NonEmptyChain.one(MyEvents.Created(initial)).validNec
            case _ => "".invalidNec
          }
      }
    }

  val transitor: Transitor[MyState, MyEvents, Throwable] = {
    case (Some(state), Added(delta)) =>
      state.copy(total = state.total + delta).asRight
    case (None, MyEvents.Created(initial)) => MyState(initial).asRight
    case _                                 => new Exception().asLeft
  }

  val backend: EventSourcedBackend2[IO, MyState, MyEvents, Id, ClientId] = ???

  implicit val timer: Timer[IO] = ???
  implicit val c: Concurrent[IO] = ???

  private val app = CommandHandler[IO].from(decider)(backend)

  val consumer: Stream[IO, (Id, MyEvents)] = ???

  val subApp: CommandHandler[IO, ClientId, Id, Int, String] =
    ???

  val commands: Stream[IO, MyCommand] = ???

  val commandHandler: Stream[IO, Unit] =
    commands.evalMap(app("", "", _)).void

  private val logger = consoleLogger[IO]()

  val process1: Stream[IO, Unit] = consumer.evalMap {
    case (id, Created(initial)) =>
      for {
        res <- subApp(s"a-$id", s"child-$id", initial)
        _ <- res match {
          case Invalid(e) =>
            logger.error(e.show) >>
              app("pm-1", id, MyCommand.Delete)
          case Valid(_) => app("pm-1", id, MyCommand.Add(1))
        }
      } yield ()
    case (id, Added(delta)) =>
      logger.info(s"$id added ${delta.show}")
    case (id, Deleted) =>
      logger.info(s"$id has been deleted!")
  }

  val program: Stream[IO, Unit] =
    process1 concurrently
      commandHandler

}
