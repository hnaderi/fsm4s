package ir.hnaderi.fsm4s.common

import eu.timepit.refined.types.string.NonEmptyString
import eu.timepit.refined.types.string

trait IdCodec[T] {
  def encode(t: T): String
  def decode(s: String): Either[String, T]
}

object IdCodec {
  def apply[T: IdCodec]: IdCodec[T] = implicitly

  implicit object stringInstance extends IdCodec[String] {

    @inline override def encode(t: String): String = t

    @inline override def decode(s: String): Either[String, String] = Right(s)

  }

  implicit object nesInstance extends IdCodec[NonEmptyString] {

    override def encode(t: string.NonEmptyString): String = t.value

    override def decode(s: String): Either[String, string.NonEmptyString] =
      eu.timepit.refined.refineV(s)

  }
}
