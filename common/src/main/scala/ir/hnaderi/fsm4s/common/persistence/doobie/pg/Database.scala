package ir.hnaderi.fsm4s.common.persistence.pg

import cats.effect.Async
import cats.effect.ContextShift
import cats.effect.Resource
import doobie.util.ExecutionContexts
import cats.effect.Blocker
import doobie.hikari.HikariTransactor
import doobie.util.transactor.Transactor

object Database {
  def transactor[F[_]: Async: ContextShift]: Resource[F, Transactor[F]] =
    for {
      ce <- ExecutionContexts.fixedThreadPool[F](2)
      be <- Blocker[F]
      xa <- HikariTransactor.newHikariTransactor[F](
        "org.postgresql.Driver", // driver classname
        "jdbc:postgresql://localhost:5432/?currentSchema=trader", // connect URL (driver-specific)
        "postgres", // user
        "123a123A", // password
        ce,
        be
      )
    } yield xa
}
