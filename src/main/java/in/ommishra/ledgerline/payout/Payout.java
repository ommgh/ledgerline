package in.ommishra.ledgerline.payout;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payouts")
public class Payout {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID walletAccountId;

    @Column(nullable = false, updatable = false)
    private long amount;

    @Column(nullable = false, updatable = false)
    private long fee;

    @Column(nullable = false, updatable = false)
    private String currency;

    @Column(nullable = false, updatable = false)
    private String beneficiary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayoutStatus status;

    private String providerPayoutId;

    private String failureReason;

    @Version
    private long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Payout() {
    }

    Payout(UUID walletAccountId, long amount, long fee, String currency, String beneficiary) {
        this.walletAccountId = walletAccountId;
        this.amount = amount;
        this.fee = fee;
        this.currency = currency;
        this.beneficiary = beneficiary;
        this.status = PayoutStatus.REQUESTED;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    /**
     * Moves to {@code next}. Returns false when the payout is already there, which
     * is how a webhook delivered twice becomes a no-op.
     */
    boolean moveTo(PayoutStatus next, String providerPayoutId, String failureReason) {
        if (status == next) {
            return false;
        }
        if (!status.canMoveTo(next)) {
            throw new IllegalPayoutTransitionException(id, status, next);
        }
        status = next;
        if (providerPayoutId != null) {
            this.providerPayoutId = providerPayoutId;
        }
        this.failureReason = failureReason;
        updatedAt = Instant.now();
        return true;
    }

    long totalDebited() {
        return Math.addExact(amount, fee);
    }

    public UUID getId() {
        return id;
    }

    public UUID getWalletAccountId() {
        return walletAccountId;
    }

    public long getAmount() {
        return amount;
    }

    public long getFee() {
        return fee;
    }

    public String getCurrency() {
        return currency;
    }

    public String getBeneficiary() {
        return beneficiary;
    }

    public PayoutStatus getStatus() {
        return status;
    }

    public String getProviderPayoutId() {
        return providerPayoutId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
