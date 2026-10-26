package in.ommishra.ledgerline.journal;

import in.ommishra.ledgerline.account.Account;
import in.ommishra.ledgerline.account.AccountRepository;
import in.ommishra.ledgerline.common.BusinessRuleException;
import in.ommishra.ledgerline.common.Direction;
import in.ommishra.ledgerline.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The only way money moves. Every entry must balance (debits equal credits),
 * every account must share the entry's currency, and each account's cached
 * balance is updated in the same transaction as its posting rows.
 *
 * <p>Accounts are row-locked in id order before their balances change. An
 * earlier version relied on optimistic locking alone; under 32 threads on 8 hot
 * wallets it retried almost once per transfer and still exhausted retries, so
 * {@code @Version} is now only a backstop for writes that bypass this path.
 */
@Service
public class LedgerService {

    private final AccountRepository accounts;
    private final JournalEntryRepository entries;

    public LedgerService(AccountRepository accounts, JournalEntryRepository entries) {
        this.accounts = accounts;
        this.entries = entries;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public JournalEntry post(EntryKind kind, String currency, String reference, String description, List<Leg> legs) {
        validateBalanced(legs);

        Set<UUID> ids = legs.stream().map(Leg::accountId).collect(Collectors.toSet());
        Map<UUID, Account> byId = accounts.lockAllById(ids).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
        Set<UUID> missing = new HashSet<>(ids);
        missing.removeAll(byId.keySet());
        if (!missing.isEmpty()) {
            throw new NotFoundException("Accounts not found: " + missing);
        }

        JournalEntry entry = new JournalEntry(kind, currency, reference, description);
        for (Leg leg : legs) {
            Account account = byId.get(leg.accountId());
            if (!account.getCurrency().equals(currency)) {
                throw new BusinessRuleException("currency_mismatch",
                        "Account %s holds %s, entry is in %s".formatted(account.getCode(), account.getCurrency(), currency));
            }
            account.apply(leg.direction(), leg.amount());
            entry.addPosting(leg);
        }
        return entries.save(entry);
    }

    static void validateBalanced(List<Leg> legs) {
        if (legs.size() < 2) {
            throw new BusinessRuleException("unbalanced_entry", "An entry needs at least two legs");
        }
        long debits = 0;
        long credits = 0;
        for (Leg leg : legs) {
            if (leg.amount() <= 0) {
                throw new BusinessRuleException("invalid_amount", "Leg amounts must be positive");
            }
            if (leg.direction() == Direction.DEBIT) {
                debits = Math.addExact(debits, leg.amount());
            } else {
                credits = Math.addExact(credits, leg.amount());
            }
        }
        if (debits != credits) {
            throw new BusinessRuleException("unbalanced_entry",
                    "Debits (%d) must equal credits (%d)".formatted(debits, credits));
        }
    }
}
