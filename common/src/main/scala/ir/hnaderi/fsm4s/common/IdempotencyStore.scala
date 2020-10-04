package ir.hnaderi.fsm4s.common

trait IdempotencyStore[F[_], T] {
  def append(t: T): F[Boolean]
  def all: F[List[T]]
  def clean(maxToHold: Int): F[Unit]
}
