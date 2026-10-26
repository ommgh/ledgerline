package in.ommishra.ledgerline.idempotency;

import in.ommishra.ledgerline.IntegrationTest;
import in.ommishra.ledgerline.LedgerFixtures;
import in.ommishra.ledgerline.account.Account;
import in.ommishra.ledgerline.transfer.TransferRequest;
import in.ommishra.ledgerline.transfer.TransferResponse;
import in.ommishra.ledgerline.transfer.TransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class IdempotencyTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    LedgerFixtures fixtures;

    @Autowired
    TransferService transfers;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void retryWithSameKeyReplaysTheOriginalResponse() throws Exception {
        Account bank = fixtures.bank();
        Account asha = fixtures.fundedWallet(bank, 5_000);
        Account ravi = fixtures.wallet();
        String key = UUID.randomUUID().toString();
        String body = transfer(asha, ravi, 1_000);

        String first = mvc.perform(post("/api/v1/transfers").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andReturn().getResponse().getContentAsString();

        mvc.perform(post("/api/v1/transfers").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(first));

        assertThat(fixtures.balance(asha.getId())).isEqualTo(4_000);
        assertThat(fixtures.balance(ravi.getId())).isEqualTo(1_000);
    }

    @Test
    void reusingKeyWithDifferentBodyIsRejected() throws Exception {
        Account bank = fixtures.bank();
        Account asha = fixtures.fundedWallet(bank, 5_000);
        Account ravi = fixtures.wallet();
        String key = UUID.randomUUID().toString();

        mvc.perform(post("/api/v1/transfers").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(transfer(asha, ravi, 1_000)))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/transfers").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(transfer(asha, ravi, 2_000)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("idempotency_key_reused"));

        assertThat(fixtures.balance(asha.getId())).isEqualTo(4_000);
    }

    @Test
    void missingKeyIsRejected() throws Exception {
        Account bank = fixtures.bank();
        Account asha = fixtures.fundedWallet(bank, 5_000);
        Account ravi = fixtures.wallet();

        mvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON).content(transfer(asha, ravi, 1_000)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectedRequestCanBeRetriedWithSameKey() throws Exception {
        Account bank = fixtures.bank();
        Account asha = fixtures.fundedWallet(bank, 500);
        Account ravi = fixtures.wallet();
        String key = UUID.randomUUID().toString();
        String body = transfer(asha, ravi, 1_000);

        mvc.perform(post("/api/v1/transfers").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableContent());

        transfers.transfer(UUID.randomUUID().toString(),
                new TransferRequest(bank.getId(), asha.getId(), 500, "INR", null, "top-up"));

        mvc.perform(post("/api/v1/transfers").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"));
    }

    @Test
    void concurrentDuplicatesPostExactlyOnce() throws Exception {
        Account bank = fixtures.bank();
        Account asha = fixtures.fundedWallet(bank, 5_000);
        Account ravi = fixtures.wallet();
        String key = UUID.randomUUID().toString();
        TransferRequest request = new TransferRequest(asha.getId(), ravi.getId(), 1_000, "INR", "dup-race", null);

        int clients = 16;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Idempotent<TransferResponse>>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(clients)) {
            for (int i = 0; i < clients; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return transfers.transfer(key, request);
                }));
            }
            start.countDown();

            Set<UUID> entryIds = results.stream()
                    .map(IdempotencyTest::join)
                    .map(result -> result.value().journalEntryId())
                    .collect(Collectors.toSet());
            assertThat(entryIds).hasSize(1);
        }

        assertThat(fixtures.balance(asha.getId())).isEqualTo(4_000);
        assertThat(jdbc.queryForObject(
                "select count(*) from journal_entries where reference = 'dup-race'", Integer.class)).isEqualTo(1);
    }

    static String transfer(Account debit, Account credit, long amount) {
        return """
                {"debitAccountId":"%s","creditAccountId":"%s","amount":%d,"currency":"INR"}
                """.formatted(debit.getId(), credit.getId(), amount);
    }

    private static <T> T join(Future<T> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
