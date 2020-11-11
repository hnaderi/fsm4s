package ir.hnaderi.fsm4s

import cats.data.ValidatedNec

package object eventsourcing {
  type Transitor[S, E, Er] = (Option[S], E) => Either[Er, S]
  type CommandHandler[F[_], CmdID, ID, CMD, REJ] =
    (CmdID, ID, CMD) => F[ValidatedNec[REJ, Unit]]
}
