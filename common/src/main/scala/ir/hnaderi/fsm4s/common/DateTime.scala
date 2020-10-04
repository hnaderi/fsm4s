package ir.hnaderi.fsm4s.common

import cats.effect.Clock
import java.util.concurrent.TimeUnit
import cats.implicits._
import cats.Functor
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

object DateTime {
  type DateTime = ZonedDateTime
  def now[F[_]: Functor: Clock]: F[DateTime] =
    Clock[F]
      .realTime(TimeUnit.MILLISECONDS)
      .map(Instant.ofEpochMilli(_).atZone(ZoneOffset.UTC))
}
