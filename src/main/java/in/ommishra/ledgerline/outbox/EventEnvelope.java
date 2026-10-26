package in.ommishra.ledgerline.outbox;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope(
        UUID eventId,
        String eventType,
        String aggregateType,
        UUID aggregateId,
        Instant occurredAt,
        Object data) {
}
