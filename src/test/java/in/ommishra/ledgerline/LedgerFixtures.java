package in.ommishra.ledgerline;

import in.ommishra.ledgerline.account.Account;
import in.ommishra.ledgerline.account.AccountService;
import in.ommishra.ledgerline.account.AccountType;
import in.ommishra.ledgerline.account.OpenAccountRequest;
import in.ommishra.ledgerline.transfer.TransferRequest;
import in.ommishra.ledgerline.transfer.TransferService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Helpers for integration tests. Codes get a random suffix so tests sharing a database never collide. */
@Component
public class LedgerFixtures {

    private final AccountService accounts;
    private final TransferService transfers;
    private final JdbcTemplate jdbc;

    public LedgerFixtures(AccountService accounts, TransferService transfers, JdbcTemplate jdbc) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.jdbc = jdbc;
    }

    public Account bank() {
        return accounts.open(new OpenAccountRequest(code("bank"), "Bank", AccountType.ASSET, "INR", true));
    }

    public Account wallet() {
        return accounts.open(new OpenAccountRequest(code("wallet"), "Wallet", AccountType.LIABILITY, "INR", false));
    }

    /** Tops up a wallet from the bank: the bank asset grows and so does what we owe the wallet holder. */
    public Account fundedWallet(Account bank, long amount) {
        Account wallet = wallet();
        transfers.transfer(UUID.randomUUID().toString(),
                new TransferRequest(bank.getId(), wallet.getId(), amount, "INR", null, "top-up"));
        return wallet;
    }

    public long balance(UUID accountId) {
        return accounts.get(accountId).getBalance();
    }

    /** Balance recomputed from postings, in the account's normal direction. */
    public long derivedBalance(UUID accountId) {
        return jdbc.queryForObject("""
                select coalesce(sum(case when p.direction = 'DEBIT' then p.amount else -p.amount end), 0)
                       * case when a.type in ('ASSET', 'EXPENSE') then 1 else -1 end
                from accounts a left join postings p on p.account_id = a.id
                where a.id = ?
                group by a.type
                """, Long.class, accountId);
    }

    public static String code(String prefix) {
        return prefix + ":" + UUID.randomUUID().toString().substring(0, 8);
    }
}
