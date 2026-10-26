package in.ommishra.ledgerline.idempotency;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("ledgerline.idempotency")
public record IdempotencyProperties(@DefaultValue("24h") Duration retention) {
}
