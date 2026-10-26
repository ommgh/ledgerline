package in.ommishra.ledgerline.outbox;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

/**
 * Records an event in the caller's transaction. Publishing straight to Kafka
 * from the service would be a dual write: the database commit and the Kafka send
 * can each fail on their own. Here the event exists if and only if the ledger
 * change committed, and {@link OutboxRelay} ships it afterwards.
 */
@Component
public class Outbox {

    private final EntityManager entityManager;
    private final ObjectMapper json;

    public Outbox(EntityManager entityManager, ObjectMapper json) {
        this.entityManager = entityManager;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String aggregateType, UUID aggregateId, String eventType, Object data) {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        String payload = json.writeValueAsString(new EventEnvelope(eventId, eventType, aggregateType, aggregateId, now, data));
        entityManager.persist(new OutboxEvent(eventId, aggregateType, aggregateId, eventType, payload, now));
    }
}
