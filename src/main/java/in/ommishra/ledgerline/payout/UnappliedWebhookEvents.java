package in.ommishra.ledgerline.payout;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Keeps authentic webhook events that could not be applied. Answering them with a
 * 2xx stops pointless redelivery; storing them means a stale or contradictory
 * event is visible to reconciliation instead of disappearing.
 */
@Component
class UnappliedWebhookEvents {

    private static final int MAX_REASON = 512;

    private final JdbcClient jdbc;

    UnappliedWebhookEvents(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void record(String eventType, UUID payoutId, String providerPayoutId, String reason, String payload) {
        jdbc.sql("""
                        insert into unapplied_webhook_events
                            (id, provider, event_type, payout_id, provider_payout_id, reason, payload, received_at)
                        values (?, 'razorpay', ?, ?, ?, ?, ?, ?)
                        """)
                .params(UUID.randomUUID(), eventType, payoutId, providerPayoutId,
                        reason.length() > MAX_REASON ? reason.substring(0, MAX_REASON) : reason,
                        payload, OffsetDateTime.now(ZoneOffset.UTC))
                .update();
    }
}
