package in.ommishra.ledgerline.account;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Ids of the platform accounts seeded by migrations, resolved once by code. */
@Component
public class SystemAccounts {

    public static final String PAYOUTS_IN_TRANSIT = "platform:payouts-in-transit";
    public static final String BANK = "platform:bank";
    public static final String PAYOUT_FEES = "platform:payout-fees";

    private final AccountRepository accounts;
    private final Map<String, UUID> ids = new ConcurrentHashMap<>();

    public SystemAccounts(AccountRepository accounts) {
        this.accounts = accounts;
    }

    public UUID id(String code) {
        return ids.computeIfAbsent(code, c -> accounts.findByCode(c)
                .orElseThrow(() -> new IllegalStateException("System account %s is missing; did migrations run?".formatted(c)))
                .getId());
    }
}
