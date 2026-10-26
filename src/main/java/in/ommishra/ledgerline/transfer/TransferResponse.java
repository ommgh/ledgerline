package in.ommishra.ledgerline.transfer;

import in.ommishra.ledgerline.journal.JournalEntry;

import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID journalEntryId,
        UUID debitAccountId,
        UUID creditAccountId,
        long amount,
        String currency,
        String reference,
        Instant createdAt) {

    static TransferResponse from(JournalEntry entry, TransferRequest request) {
        return new TransferResponse(entry.getId(), request.debitAccountId(), request.creditAccountId(),
                request.amount(), entry.getCurrency(), entry.getReference(), entry.getCreatedAt());
    }
}
