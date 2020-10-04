package ir.hnaderi.fsm4s.common

final case class CommandHandlerState[S, I](
    version: Long,
    lastState: S,
    processedIds: List[I]
) {
  def addProcessed(i: I): CommandHandlerState[S, I] =
    copy(processedIds = processedIds :+ i)
  def withState(s: S): CommandHandlerState[S, I] = copy(lastState = s)
  def withVersion(v: Long): CommandHandlerState[S, I] = copy(version = v)
}
