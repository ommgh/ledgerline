package in.ommishra.ledgerline.idempotency;

import in.ommishra.ledgerline.common.BusinessRuleException;
import in.ommishra.ledgerline.journal.LedgerTransactions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Makes a write safe to retry. The work and the key record commit in one
 * transaction, so either both exist or neither does: a client that times out
 * and retries gets the original response instead of a second transfer.
 *
 * <p>Only successful responses are stored. A request rejected by a business
 * rule (say, insufficient funds) can be retried with the same key once fixed.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyRecordRepository records;
    private final LedgerTransactions transactions;
    private final ObjectMapper json;
    private final IdempotencyProperties properties;

    public IdempotencyService(IdempotencyRecordRepository records, LedgerTransactions transactions,
                              ObjectMapper json, IdempotencyProperties properties) {
        this.records = records;
        this.transactions = transactions;
        this.json = json;
        this.properties = properties;
    }

    /** Runs {@code work} in a ledger transaction unless {@code key} was already used in {@code scope}. */
    public <T> Idempotent<T> execute(String scope, String key, Object request, Class<T> responseType, Supplier<T> work) {
        IdempotencyRecordId id = new IdempotencyRecordId(scope, key);
        String requestHash = sha256(json.writeValueAsBytes(request));
        try {
            return transactions.execute(() -> {
                Optional<IdempotencyRecord> existing = records.findById(id);
                if (existing.isPresent()) {
                    return replay(existing.get(), requestHash, responseType);
                }
                T response = work.get();
                records.saveAndFlush(new IdempotencyRecord(id, requestHash, json.writeValueAsString(response)));
                return new Idempotent<>(response, false);
            });
        } catch (DataIntegrityViolationException race) {
            // A concurrent request with the same key committed first. Our insert waited on its
            // primary-key lock, then failed and rolled our work back; answer with its response.
            return transactions.execute(() -> records.findById(id)
                    .map(record -> replay(record, requestHash, responseType))
                    .orElseThrow(() -> race));
        }
    }

    private <T> Idempotent<T> replay(IdempotencyRecord record, String requestHash, Class<T> responseType) {
        if (!record.getRequestHash().equals(requestHash)) {
            throw new BusinessRuleException("idempotency_key_reused",
                    "Idempotency key was already used with a different request body");
        }
        return new Idempotent<>(json.readValue(record.getResponseBody(), responseType), true);
    }

    @Scheduled(cron = "${ledgerline.idempotency.purge-cron:0 15 * * * *}")
    @Transactional
    public void purgeExpired() {
        int purged = records.deleteCreatedBefore(Instant.now().minus(properties.retention()));
        if (purged > 0) {
            log.info("Purged {} expired idempotency keys", purged);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
