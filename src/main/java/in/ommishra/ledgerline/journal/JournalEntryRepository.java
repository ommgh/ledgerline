package in.ommishra.ledgerline.journal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {
}
