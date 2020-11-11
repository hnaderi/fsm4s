package ir.hnaderi.fsm4s
package eventsourcing

import cats.data.NonEmptyChain

trait EventSourcedBackend2[F[_], STATE, EVENT, ID, CmdID] {
  def accept(
      version: Int,
      aggId: ID,
      events: NonEmptyChain[EVENT],
      refCmd: CmdID
  ): F[Boolean]
  def read(id: ID): F[(Int, Option[STATE])]
  def history(id: ID): fs2.Stream[F, (Int, STATE)]
}
