package in.ommishra.ledgerline.journal;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** An immutable, balanced set of postings. Entries are never updated; mistakes are fixed with a reversing entry. */
@Entity
@Table(name = "journal_entries")
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private EntryKind kind;

    @Column(nullable = false, updatable = false)
    private String currency;

    @Column(updatable = false)
    private String reference;

    @Column(updatable = false)
    private String description;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.PERSIST)
    private List<Posting> postings = new ArrayList<>();

    protected JournalEntry() {
    }

    JournalEntry(EntryKind kind, String currency, String reference, String description) {
        this.kind = kind;
        this.currency = currency;
        this.reference = reference;
        this.description = description;
        this.createdAt = Instant.now();
    }

    void addPosting(Leg leg) {
        postings.add(new Posting(this, leg.accountId(), leg.direction(), leg.amount(), createdAt));
    }

    public UUID getId() {
        return id;
    }

    public EntryKind getKind() {
        return kind;
    }

    public String getCurrency() {
        return currency;
    }

    public String getReference() {
        return reference;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<Posting> getPostings() {
        return Collections.unmodifiableList(postings);
    }
}
