package in.ommishra.ledgerline.transfer;

import in.ommishra.ledgerline.common.BusinessRuleException;
import in.ommishra.ledgerline.journal.EntryKind;
import in.ommishra.ledgerline.journal.JournalEntry;
import in.ommishra.ledgerline.journal.LedgerService;
import in.ommishra.ledgerline.journal.LedgerTransactions;
import in.ommishra.ledgerline.journal.Leg;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TransferService {

    private final LedgerTransactions transactions;
    private final LedgerService ledger;

    public TransferService(LedgerTransactions transactions, LedgerService ledger) {
        this.transactions = transactions;
        this.ledger = ledger;
    }

    public TransferResponse transfer(TransferRequest request) {
        if (request.debitAccountId().equals(request.creditAccountId())) {
            throw new BusinessRuleException("same_account", "Debit and credit accounts must differ");
        }
        return transactions.execute(() -> {
            JournalEntry entry = ledger.post(EntryKind.TRANSFER, request.currency(), request.reference(),
                    request.description(), List.of(
                            Leg.debit(request.debitAccountId(), request.amount()),
                            Leg.credit(request.creditAccountId(), request.amount())));
            return TransferResponse.from(entry, request);
        });
    }
}
