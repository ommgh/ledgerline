package in.ommishra.ledgerline.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

@Entity
@Table(name = "idempotency_keys")
class IdempotencyRecord implements Persistable<IdempotencyRecordId> {

    @EmbeddedId
    private IdempotencyRecordId id;

    @Column(nullable = false, updatable = false)
    private String requestHash;

    @Column(nullable = false, updatable = false)
    private String responseBody;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Forces save() to INSERT, so a concurrent duplicate fails on the primary key instead of merging. */
    @Transient
    private boolean isNew = true;

    protected IdempotencyRecord() {
    }

    IdempotencyRecord(IdempotencyRecordId id, String requestHash, String responseBody) {
        this.id = id;
        this.requestHash = requestHash;
        this.responseBody = responseBody;
        this.createdAt = Instant.now();
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        isNew = false;
    }

    @Override
    public IdempotencyRecordId getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    String getRequestHash() {
        return requestHash;
    }

    String getResponseBody() {
        return responseBody;
    }
}
