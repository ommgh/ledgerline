package in.ommishra.ledgerline.journal;

import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Runs ledger work in a fresh transaction and replays the whole transaction on a
 * lock failure (deadlock, lock timeout or a stale {@code @Version}). Retrying
 * inside the transaction would not help: Postgres has already aborted it.
 */
@Component
public class LedgerTransactions {

    private final TransactionTemplate transactionTemplate;
    private final RetryTemplate retryTemplate;

    public LedgerTransactions(PlatformTransactionManager transactionManager, LedgerProperties properties) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.retryTemplate = new RetryTemplate(RetryPolicy.builder()
                .includes(OptimisticLockingFailureException.class, PessimisticLockingFailureException.class)
                .maxRetries(properties.maxRetries())
                .delay(Duration.ofMillis(10))
                .jitter(Duration.ofMillis(5))
                .multiplier(2)
                .maxDelay(Duration.ofMillis(250))
                .build());
    }

    public <T> T execute(Supplier<T> work) {
        return retryTemplate.invoke(() -> transactionTemplate.execute(status -> work.get()));
    }
}
