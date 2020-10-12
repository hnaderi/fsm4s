package ir.hnaderi.fsm4s

import cats.data.RWS
import cats.data.Chain
package object data {
  type Transitor[S, Ev, IE] = RWS[Ev, Chain[IE], S, Unit]
}
