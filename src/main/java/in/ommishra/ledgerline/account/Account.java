package in.ommishra.ledgerline.account;

import in.ommishra.ledgerline.common.BusinessRuleException;
import in.ommishra.ledgerline.common.Direction;
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
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, updatable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AccountType type;

    @Column(nullable = false, updatable = false)
    private String currency;

    /** Balance in minor units (paise), signed in the account's normal direction. */
    @Column(nullable = false)
    private long balance;

    @Column(nullable = false)
    private boolean overdraftAllowed;

    /** Optimistic lock: concurrent postings to the same account retry instead of losing an update. */
    @Version
    private long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() {
    }

    public Account(String code, String name, AccountType type, String currency, boolean overdraftAllowed) {
        this.code = code;
        this.name = name;
        this.type = type;
        this.currency = currency;
        this.overdraftAllowed = overdraftAllowed;
        this.createdAt = Instant.now();
    }

    /**
     * Applies one posting leg. Only the ledger should call this, inside the
     * transaction that writes the matching posting row.
     */
    public void apply(Direction direction, long amount) {
        long delta = direction == type.normalSide() ? amount : -amount;
        long next = Math.addExact(balance, delta);
        if (next < 0 && !overdraftAllowed) {
            throw new BusinessRuleException("insufficient_funds",
                    "Account %s has balance %d, cannot %s %d".formatted(code, balance, direction.name().toLowerCase(), amount));
        }
        balance = next;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public AccountType getType() {
        return type;
    }

    public String getCurrency() {
        return currency;
    }

    public long getBalance() {
        return balance;
    }

    public boolean isOverdraftAllowed() {
        return overdraftAllowed;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
