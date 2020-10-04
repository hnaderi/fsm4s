package ir.hnaderi.fsm4s.common

trait FSMExecutor[F[_], T] {
  def goTo(state: T): F[Unit]
  // def persist

}
