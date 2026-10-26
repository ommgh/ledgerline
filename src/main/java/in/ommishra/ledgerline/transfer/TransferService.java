package in.ommishra.ledgerline.transfer;

import in.ommishra.ledgerline.common.BusinessRuleException;
import in.ommishra.ledgerline.idempotency.Idempotent;
import in.ommishra.ledgerline.idempotency.IdempotencyService;
import in.ommishra.ledgerline.journal.EntryKind;
import in.ommishra.ledgerline.journal.JournalEntry;
import in.ommishra.ledgerline.journal.LedgerService;
import in.ommishra.ledgerline.journal.Leg;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TransferService {

    private final IdempotencyService idempotency;
    private final LedgerService ledger;

    public TransferService(IdempotencyService idempotency, LedgerService ledger) {
        this.idempotency = idempotency;
        this.ledger = ledger;
    }

    public Idempotent<TransferResponse> transfer(String idempotencyKey, TransferRequest request) {
        if (request.debitAccountId().equals(request.creditAccountId())) {
            throw new BusinessRuleException("same_account", "Debit and credit accounts must differ");
        }
        return idempotency.execute("transfers", idempotencyKey, request, TransferResponse.class, () -> {
            JournalEntry entry = ledger.post(EntryKind.TRANSFER, request.currency(), request.reference(),
                    request.description(), List.of(
                            Leg.debit(request.debitAccountId(), request.amount()),
                            Leg.credit(request.creditAccountId(), request.amount())));
            return TransferResponse.from(entry, request);
        });
    }
}
