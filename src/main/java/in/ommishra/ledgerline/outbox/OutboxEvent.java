package in.ommishra.ledgerline.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    /** Also the event id consumers deduplicate on, since delivery is at-least-once. */
    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String aggregateType;

    @Column(nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(nullable = false, updatable = false)
    private String eventType;

    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String payload;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts;

    private String lastError;

    protected OutboxEvent() {
    }

    OutboxEvent(UUID id, String aggregateType, UUID aggregateId, String eventType, String payload, Instant createdAt) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    void markPublished() {
        attempts++;
        publishedAt = Instant.now();
        lastError = null;
    }

    void markFailed(Exception error) {
        attempts++;
        String message = String.valueOf(error.getMessage());
        lastError = message.length() > 1024 ? message.substring(0, 1024) : message;
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public int getAttempts() {
        return attempts;
    }
}
