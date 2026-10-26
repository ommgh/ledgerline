package in.ommishra.ledgerline.journal;

import in.ommishra.ledgerline.common.Direction;

import java.time.Instant;
import java.util.UUID;

public record PostingResponse(
        UUID id,
        UUID journalEntryId,
        EntryKind kind,
        Direction direction,
        long amount,
        String reference,
        Instant createdAt) {

    static PostingResponse from(Posting posting) {
        JournalEntry entry = posting.getJournalEntry();
        return new PostingResponse(posting.getId(), entry.getId(), entry.getKind(), posting.getDirection(),
                posting.getAmount(), entry.getReference(), posting.getCreatedAt());
    }
}
