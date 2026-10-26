package in.ommishra.ledgerline.transfer;

import in.ommishra.ledgerline.IntegrationTest;
import in.ommishra.ledgerline.LedgerFixtures;
import in.ommishra.ledgerline.account.Account;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class TransferApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    LedgerFixtures fixtures;

    @Test
    void movesMoneyBetweenWalletsAndRecordsBothLegs() throws Exception {
        Account bank = fixtures.bank();
        Account asha = fixtures.fundedWallet(bank, 10_000);
        Account ravi = fixtures.wallet();

        mvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transfer(asha, ravi, 2_500)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.journalEntryId").exists())
                .andExpect(jsonPath("$.amount").value(2_500));

        assertThat(fixtures.balance(asha.getId())).isEqualTo(7_500);
        assertThat(fixtures.balance(ravi.getId())).isEqualTo(2_500);
        assertThat(fixtures.balance(bank.getId())).isEqualTo(10_000);
        assertThat(fixtures.derivedBalance(asha.getId())).isEqualTo(7_500);
        assertThat(fixtures.derivedBalance(ravi.getId())).isEqualTo(2_500);

        mvc.perform(get("/api/v1/accounts/{id}/postings", ravi.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].direction").value("CREDIT"))
                .andExpect(jsonPath("$.items[0].kind").value("TRANSFER"));
    }

    @Test
    void rejectsOverdraftAndLeavesBalancesUntouched() throws Exception {
        Account bank = fixtures.bank();
        Account asha = fixtures.fundedWallet(bank, 1_000);
        Account ravi = fixtures.wallet();

        mvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transfer(asha, ravi, 1_001)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("insufficient_funds"));

        assertThat(fixtures.balance(asha.getId())).isEqualTo(1_000);
        assertThat(fixtures.balance(ravi.getId())).isZero();
    }

    @Test
    void rejectsCurrencyMismatch() throws Exception {
        Account bank = fixtures.bank();
        Account asha = fixtures.fundedWallet(bank, 1_000);
        Account ravi = fixtures.wallet();

        mvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transfer(asha, ravi, 100).replace("INR", "USD")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("currency_mismatch"));
    }

    @Test
    void rejectsTransferToSameAccount() throws Exception {
        Account bank = fixtures.bank();
        Account asha = fixtures.fundedWallet(bank, 1_000);

        mvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transfer(asha, asha, 100)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("same_account"));
    }

    static String transfer(Account debit, Account credit, long amount) {
        return """
                {"debitAccountId":"%s","creditAccountId":"%s","amount":%d,"currency":"INR"}
                """.formatted(debit.getId(), credit.getId(), amount);
    }
}
