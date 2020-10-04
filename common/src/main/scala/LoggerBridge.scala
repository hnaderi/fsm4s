package org.slf4j.impl

import cats.effect.implicits._
import cats.effect.{Clock, ConcurrentEffect, ContextShift, IO, Timer}
import io.odin._
import io.odin.slf4j.OdinLoggerBinder

import scala.concurrent.ExecutionContext

//effect type should be specified inbefore
//log line will be recorded right after the call with no suspension
class StaticLoggerBinder extends OdinLoggerBinder[IO] {

  val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
  implicit val timer: Timer[IO] = IO.timer(ec)
  implicit val clock: Clock[IO] = timer.clock
  implicit val cs: ContextShift[IO] = IO.contextShift(ec)
  implicit val F: ConcurrentEffect[IO] = IO.ioConcurrentEffect

  val loggers: PartialFunction[String, Logger[IO]] = {
    case "some.external.package.SpecificClass" =>
      consoleLogger[IO](minLevel = Level.Warn) //disable noisy external logs
    case _ => //if wildcard case isn't provided, default logger is no-op
      consoleLogger[IO]()
  }
}

object StaticLoggerBinder extends StaticLoggerBinder {

  @SuppressWarnings(Array("org.wartremover.warts.Var"))
  var REQUESTED_API_VERSION: String = "1.7"

  def getSingleton: StaticLoggerBinder = this

}
