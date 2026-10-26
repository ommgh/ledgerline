package in.ommishra.ledgerline.transfer;

import in.ommishra.ledgerline.IntegrationTest;
import in.ommishra.ledgerline.LedgerFixtures;
import in.ommishra.ledgerline.account.Account;
import in.ommishra.ledgerline.common.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hammers a small set of wallets from many threads. Without optimistic locking
 * two transfers out of the same wallet could both read the old balance and one
 * debit would be lost, creating money out of nothing.
 */
@IntegrationTest
class ConcurrentTransferTest {

    private static final int WALLETS = 8;
    private static final long OPENING_BALANCE = 10_000;
    private static final int TRANSFERS = 2_000;
    private static final int THREADS = 32;

    @Autowired
    TransferService transfers;

    @Autowired
    LedgerFixtures fixtures;

    @Test
    void conservesMoneyUnderContention() throws Exception {
        Account bank = fixtures.bank();
        List<Account> wallets = new ArrayList<>();
        for (int i = 0; i < WALLETS; i++) {
            wallets.add(fixtures.fundedWallet(bank, OPENING_BALANCE));
        }

        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            for (int i = 0; i < TRANSFERS; i++) {
                futures.add(pool.submit(() -> {
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    int from = random.nextInt(WALLETS);
                    int to = (from + 1 + random.nextInt(WALLETS - 1)) % WALLETS;
                    long amount = 1 + random.nextInt(3_000);
                    try {
                        transfers.transfer(new TransferRequest(wallets.get(from).getId(), wallets.get(to).getId(),
                                amount, "INR", null, null));
                        succeeded.incrementAndGet();
                    } catch (BusinessRuleException insufficientFunds) {
                        rejected.incrementAndGet();
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get(); // surfaces any exhausted optimistic-lock retry as a test failure
            }
        }

        long total = 0;
        for (Account wallet : wallets) {
            long balance = fixtures.balance(wallet.getId());
            assertThat(balance).isNotNegative();
            assertThat(fixtures.derivedBalance(wallet.getId())).isEqualTo(balance);
            total += balance;
        }
        assertThat(total).isEqualTo(WALLETS * OPENING_BALANCE);
        assertThat(succeeded.get() + rejected.get()).isEqualTo(TRANSFERS);
        assertThat(succeeded.get()).isGreaterThan(TRANSFERS / 2);
    }
}
