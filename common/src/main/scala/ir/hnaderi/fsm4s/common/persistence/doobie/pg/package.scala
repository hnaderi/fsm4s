package ir.hnaderi.fsm4s.common.persistence

import cats.implicits._
import doobie.util.Put
import org.postgresql.util.PGobject
import cats.data.NonEmptyList
import io.circe.Json
import io.circe.syntax._
import io.circe.parser.parse
import java.time.ZonedDateTime
import java.sql.Timestamp

import doobie.implicits.javasql._
import doobie.util.Get
import cats.Show
import io.circe.Decoder
import scala.reflect.runtime.universe.{Type, TypeTag}
import java.time.ZoneOffset
import eu.timepit.refined.api.Refined

package object pg {
  private[pg] implicit val jsonPut: Put[Json] =
    Put.Advanced.other[PGobject](NonEmptyList.of("json")).tcontramap[Json] {
      j =>
        val o = new PGobject
        o.setType("json")
        o.setValue(j.noSpaces)
        o
    }
  private implicit object PGobjectShow extends Show[PGobject] {
    override def show(t: PGobject): String =
      s"PGObject[type: ${t.getType()}] value: ${t.getValue()}"
  }

  val jsonGet: Get[Json] =
    Get.Advanced.other[PGobject](NonEmptyList.of("json")).temap[Json] { o =>
      // it must be a valid json, as it is json type in database
      parse(o.getValue).leftMap(_.getMessage())
    }

  private[pg] implicit val dateTimePut: Put[ZonedDateTime] =
    Put[Timestamp].contramap(zdt => Timestamp.from(zdt.toInstant()))

  private[pg] implicit val dateTimeGet: Get[ZonedDateTime] =
    Get[Timestamp].map(_.toInstant().atZone(ZoneOffset.UTC))

  def jsonDataGet[T: Decoder: TypeTag]: Get[T] =
    jsonGet.temap(_.as[T].leftMap(_.getMessage()))

  import eu.timepit.refined.predicates.all._
  import eu.timepit.refined.W

  type StoreNamePredicate = MatchesRegex[W.`"""\\w[\\w0-9]+"""`.T]
  type StoreName = String Refined StoreNamePredicate

  type ConsumerNamePredicate = NonEmpty
  type ConsumerName = String Refined ConsumerNamePredicate
}
