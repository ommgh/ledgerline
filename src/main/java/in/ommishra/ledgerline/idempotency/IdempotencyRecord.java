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

    /** Empty while the claiming transaction runs; nobody else can see the row until it commits with a response. */
    @Column(nullable = false)
    private String responseBody;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Forces save() to INSERT, so a concurrent duplicate fails on the primary key instead of merging. */
    @Transient
    private boolean isNew = true;

    protected IdempotencyRecord() {
    }

    private IdempotencyRecord(IdempotencyRecordId id, String requestHash) {
        this.id = id;
        this.requestHash = requestHash;
        this.responseBody = "";
        this.createdAt = Instant.now();
    }

    /** A key taken before the work runs; {@link #complete} stores the response in the same transaction. */
    static IdempotencyRecord claim(IdempotencyRecordId id, String requestHash) {
        return new IdempotencyRecord(id, requestHash);
    }

    void complete(String responseBody) {
        this.responseBody = responseBody;
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
