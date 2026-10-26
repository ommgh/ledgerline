package in.ommishra.ledgerline.account;

import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String code,
        String name,
        AccountType type,
        String currency,
        long balance,
        boolean overdraftAllowed,
        Instant createdAt) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getCode(), account.getName(), account.getType(),
                account.getCurrency(), account.getBalance(), account.isOverdraftAllowed(), account.getCreatedAt());
    }
}
