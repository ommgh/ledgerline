package in.ommishra.ledgerline.outbox;

import in.ommishra.ledgerline.IntegrationTest;
import in.ommishra.ledgerline.LedgerFixtures;
import in.ommishra.ledgerline.account.Account;
import in.ommishra.ledgerline.common.BusinessRuleException;
import in.ommishra.ledgerline.transfer.TransferRequest;
import in.ommishra.ledgerline.transfer.TransferService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.ConsumerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class OutboxTest {

    @Autowired
    LedgerFixtures fixtures;

    @Autowired
    TransferService transfers;

    @Autowired
    OutboxEventRepository events;

    @Autowired
    ConsumerFactory<String, String> consumerFactory;

    @Autowired
    OutboxProperties properties;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void publishesCommittedEntryToKafkaKeyedByEntryId() {
        Account bank = fixtures.bank();
        Account asha = fixtures.fundedWallet(bank, 5_000);
        Account ravi = fixtures.wallet();

        UUID entryId = transfers.transfer(UUID.randomUUID().toString(),
                new TransferRequest(asha.getId(), ravi.getId(), 1_200, "INR", "outbox-1", null)).value().journalEntryId();

        ConsumerRecord<String, String> record = awaitRecord(entryId.toString());
        JsonNode event = json.readTree(record.value());
        assertThat(event.get("eventType").asString()).isEqualTo("LedgerEntryPosted");
        assertThat(event.get("data").get("reference").asString()).isEqualTo("outbox-1");
        assertThat(event.get("data").get("postings")).hasSize(2);

        List<OutboxEvent> rows = events.findByAggregateId(entryId);
        assertThat(rows).hasSize(1);
        assertThat(event.get("eventId").asString()).isEqualTo(rows.getFirst().getId().toString());
        assertThat(events.findById(rows.getFirst().getId()).orElseThrow().getPublishedAt()).isNotNull();
    }

    @Test
    void rolledBackTransferWritesNoEvent() {
        Account bank = fixtures.bank();
        Account asha = fixtures.fundedWallet(bank, 100);
        Account ravi = fixtures.wallet();
        int before = countEvents();

        assertThatThrownBy(() -> transfers.transfer(UUID.randomUUID().toString(),
                new TransferRequest(asha.getId(), ravi.getId(), 1_000, "INR", "outbox-rollback", null)))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(countEvents()).isEqualTo(before);
    }

    private int countEvents() {
        return jdbc.queryForObject("select count(*) from outbox_events", Integer.class);
    }

    private ConsumerRecord<String, String> awaitRecord(String key) {
        Properties overrides = new Properties();
        overrides.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (Consumer<String, String> consumer =
                     consumerFactory.createConsumer("outbox-test-" + UUID.randomUUID(), null, null, overrides)) {
            consumer.subscribe(List.of(properties.topic()));
            Instant deadline = Instant.now().plusSeconds(20);
            while (Instant.now().isBefore(deadline)) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(250))) {
                    if (key.equals(record.key())) {
                        return record;
                    }
                }
            }
        }
        throw new AssertionError("No Kafka record with key " + key);
    }
}
