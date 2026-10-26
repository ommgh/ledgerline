package in.ommishra.ledgerline.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;

@Embeddable
public record IdempotencyRecordId(
        @Column(name = "scope") String scope,
        @Column(name = "idempotency_key") String key) implements Serializable {
}
