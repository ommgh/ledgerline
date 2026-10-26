package in.ommishra.ledgerline.integrity;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Reports the last scheduled check rather than scanning the ledger on every health probe. */
@Component("ledger")
public class LedgerHealthIndicator implements HealthIndicator {

    private final LedgerIntegrityService integrity;

    public LedgerHealthIndicator(LedgerIntegrityService integrity) {
        this.integrity = integrity;
    }

    @Override
    public Health health() {
        IntegrityReport report = integrity.lastReport();
        if (report == null) {
            return Health.unknown().withDetail("reason", "no check has run yet").build();
        }
        Health.Builder builder = report.healthy() ? Health.up() : Health.down();
        return builder.withDetail("checkedAt", report.checkedAt())
                .withDetail("violations", report.violations())
                .build();
    }
}
