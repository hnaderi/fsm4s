package ir.hnaderi.fsm4s.common

final case class CommandHandlerEnv[F[_], I, E](
    eventStore: EventStorePublisher[F, E],
    idempotencyStore: IdempotencyStore[F, I]
)
