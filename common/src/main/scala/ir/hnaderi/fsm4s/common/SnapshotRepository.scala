package ir.hnaderi.fsm4s.common

trait SnapshotRepository[F[_], T] {
  def save(oldVersion: Long, newVersion: Long, t: T): F[Boolean]
  def load: F[Option[SnapshotData[T]]]
}

final case class SnapshotData[T](version: Long, data: T)
