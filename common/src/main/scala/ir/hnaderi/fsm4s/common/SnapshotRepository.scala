package ir.hnaderi.fsm4s.common

trait SnapshotRepository[F[_], I, T] {
  def save(id: I, oldVersion: Long, newVersion: Long, t: T): F[Boolean]
  def load(id: I): F[Option[SnapshotData[T]]]
}

final case class SnapshotData[T](version: Long, data: T)
