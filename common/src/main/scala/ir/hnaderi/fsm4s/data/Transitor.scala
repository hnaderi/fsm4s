package ir.hnaderi.fsm4s
package data

import cats.implicits._
import cats.data._

final class TransitionDSL[S, Ev, TEv]{
  type F[T] = RWS[Ev, Chain[TEv], S, T]
  def read: F[S] = RWS.get
  def emit(ev: TEv, evs: TEv*) : F[Unit] = RWS.tell(Chain(ev) ++ Chain.fromSeq(evs))
  def ask: F[Ev]= RWS.ask
  def pure[T](t: T): F[T] = RWS.pure(t)
  def goTo(s: S, evs: TEv*): F[Unit] = RWS.set[Ev, Chain[TEv], S](s).tell(Chain.fromSeq(evs))
  def unit: F[Unit] = pure(())
}

object Transitor {
  def dsl[S, Ev, TEv]: TransitionDSL[S, Ev, TEv] = new TransitionDSL[S, Ev, TEv]
}
