package ir.hnaderi.fsm4s.common

sealed trait FSMApp {
  type State
  type Command
  type Event
  type Log

  def transition: Transition[State, Event]
  def decide: Decider[State, Command, Log, Event]
}

object FSMApp {
  type Aux[S, E, C, L] = FSMApp {
    type State = S
    type Event = E
    type Command = C
    type Log = L
  }

  def apply[S, E, C, L](t: Transition[S, E], d: Decider[S, C, L, E]): Aux[S, E, C, L] =
    new FSMApp {
      type State = S
      type Event = E
      type Command = C
      type Log = L

      def transition: Transition[S, E] = t
      def decide: Decider[S, C, L, E] = d
    }
}
