package in.ommishra.ledgerline.journal;

import in.ommishra.ledgerline.common.Direction;

import java.util.List;
import java.util.UUID;

/** Event payload published for every journal entry. */
public record LedgerEntryPosted(
        UUID journalEntryId,
        EntryKind kind,
        String currency,
        String reference,
        List<Line> postings) {

    public record Line(UUID accountId, Direction direction, long amount) {
    }

    static LedgerEntryPosted from(JournalEntry entry) {
        return new LedgerEntryPosted(entry.getId(), entry.getKind(), entry.getCurrency(), entry.getReference(),
                entry.getPostings().stream()
                        .map(p -> new Line(p.getAccountId(), p.getDirection(), p.getAmount()))
                        .toList());
    }
}
