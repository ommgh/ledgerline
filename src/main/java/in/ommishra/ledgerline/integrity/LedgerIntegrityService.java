package in.ommishra.ledgerline.integrity;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Proves the two ledger invariants from raw postings: every journal entry sums
 * to zero, and every cached account balance equals the sum of its postings.
 * A violation means a bug or a manual write that bypassed the ledger.
 */
@Service
public class LedgerIntegrityService {

    private static final Logger log = LoggerFactory.getLogger(LedgerIntegrityService.class);

    private static final String SIGNED_AMOUNT = "case when p.direction = 'DEBIT' then p.amount else -p.amount end";

    private final JdbcTemplate jdbc;
    private final AtomicReference<IntegrityReport> lastReport = new AtomicReference<>();

    public LedgerIntegrityService(JdbcTemplate jdbc, MeterRegistry meters) {
        this.jdbc = jdbc;
        Gauge.builder("ledgerline.integrity.violations", lastReport,
                        ref -> ref.get() == null ? 0 : ref.get().violations())
                .description("Invariant violations found by the last ledger integrity check")
                .register(meters);
    }

    /** Repeatable read gives both queries one snapshot, so in-flight transfers cannot cause false alarms. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public IntegrityReport check() {
        List<UUID> unbalanced = jdbc.queryForList("""
                select p.journal_entry_id
                from postings p
                group by p.journal_entry_id
                having sum(%s) <> 0
                """.formatted(SIGNED_AMOUNT), UUID.class);

        List<IntegrityReport.BalanceMismatch> mismatches = jdbc.query("""
                select a.id, a.code, a.balance, derived.amount
                from accounts a
                cross join lateral (
                    select coalesce(sum(%s), 0)
                           * case when a.type in ('ASSET', 'EXPENSE') then 1 else -1 end as amount
                    from postings p
                    where p.account_id = a.id
                ) derived
                where a.balance <> derived.amount
                """.formatted(SIGNED_AMOUNT),
                (rs, row) -> new IntegrityReport.BalanceMismatch(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getLong(3), rs.getLong(4)));

        IntegrityReport report = new IntegrityReport(Instant.now(), unbalanced, mismatches);
        lastReport.set(report);
        return report;
    }

    @Scheduled(cron = "${ledgerline.integrity.check-cron:0 */10 * * * *}")
    public void scheduledCheck() {
        IntegrityReport report = check();
        if (!report.healthy()) {
            log.error("Ledger integrity check failed: {} unbalanced entries, {} balance mismatches: {}",
                    report.unbalancedEntries().size(), report.balanceMismatches().size(), report);
        }
    }

    public IntegrityReport lastReport() {
        return lastReport.get();
    }
}
