package in.ommishra.ledgerline.journal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("ledgerline.ledger")
public record LedgerProperties(@DefaultValue("10") int maxRetries) {
}
