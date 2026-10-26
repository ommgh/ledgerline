package in.ommishra.ledgerline.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Polls unpublished outbox rows and sends them to Kafka keyed by aggregate id,
 * so events for one aggregate land on one partition in order.
 *
 * <p>Delivery is at-least-once: if the process dies after Kafka acknowledges a
 * send but before the row is marked published, the event goes out again.
 * Consumers deduplicate on {@code eventId}.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository events;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate transactionTemplate;
    private final OutboxProperties properties;

    public OutboxRelay(OutboxEventRepository events, KafkaTemplate<String, String> kafka,
                       PlatformTransactionManager transactionManager, OutboxProperties properties) {
        this.events = events;
        this.kafka = kafka;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${ledgerline.outbox.poll-interval-ms:500}")
    public void relay() {
        int published;
        do {
            published = publishBatch();
        } while (published == properties.batchSize());
    }

    /** Returns how many events were published; stops at the first failure to keep per-aggregate order. */
    int publishBatch() {
        Integer published = transactionTemplate.execute(status -> {
            List<OutboxEvent> batch = events.lockUnpublished(properties.batchSize());
            int sent = 0;
            for (OutboxEvent event : batch) {
                try {
                    kafka.send(properties.topic(), event.getAggregateId().toString(), event.getPayload())
                            .get(10, TimeUnit.SECONDS);
                    event.markPublished();
                    sent++;
                } catch (Exception e) {
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    event.markFailed(e);
                    log.warn("Outbox publish failed for event {} (attempt {})", event.getId(), event.getAttempts(), e);
                    break;
                }
            }
            return sent;
        });
        return published == null ? 0 : published;
    }
}
