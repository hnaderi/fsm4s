package ir.hnaderi.fsm4s.common.persistence.pg

import cats.implicits._
import doobie._
import doobie.implicits._

import ir.hnaderi.fsm4s.common._
import scala.reflect.macros.util.Helpers
import ir.hnaderi.fsm4s.common.persistence.pg.Outbox.Queries
import cats.syntax.apply

object IdempotencyStore {
  def apply[T: Meta](name: StoreName): IdempotencyStore[ConnectionIO, T] =
    new IdempotencyStore[ConnectionIO, T] {

      override def append(t: T): ConnectionIO[Boolean] =
        Queries.append(name, t).run.map(_ === 1)

      override def all: ConnectionIO[List[T]] =
        Queries.getAll[T](name).stream.compile.toList

      override def clean(maxToHold: Int): ConnectionIO[Unit] =
        Queries.clean(name, maxToHold).run.void
    }

  def setup(name: StoreName): ConnectionIO[Unit] = Queries.setup(name).run.void

  def applySetup[T: Meta](
      name: StoreName
  ): ConnectionIO[IdempotencyStore[ConnectionIO, T]] =
    setup(name).as(apply[T](name))

  private object Helpers {
    def tableNameFor(name: StoreName) = Fragment.const(s"latest_${name.value}")
    def indexNameFor(name: StoreName) =
      Fragment.const(s"latest_${name.value}_seqnr_idx")
  }

  object Queries {
    def setup(name: StoreName): Update0 = {
      val tableName = Helpers.tableNameFor(name)
      val seqIndex = Helpers.indexNameFor(name)
      sql"""
      create table $tableName (
        seqnr bigserial primary key,
        value varchar   not null
      )

      create index if not exists $seqIndex  ON $tableName (seqnr, DESC);
      """.update
    }
    def append[T: Put](name: StoreName, t: T): Update0 = sql"""
      insert into ${Helpers.tableNameFor(name)} (value)
      values ($t)
    """.update
    def clean(name: StoreName, maxToHold: Int): Update0 = {
      val table = Helpers.tableNameFor(name)
      val offset = maxToHold - 1
      sql"""
      delete from $table
      where ( SELECT seqnr
              FROM $table
              order by seqnr desc
              offset $offset
              limit 1
      );
      """.update
    }
    def getAll[T: Get](name: StoreName): Query0[T] = sql"""
      select value from ${Helpers.tableNameFor(name)}
      order by seqnr desc
    """.query[T]
  }
}
