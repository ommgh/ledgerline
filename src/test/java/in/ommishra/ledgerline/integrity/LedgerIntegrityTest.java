package in.ommishra.ledgerline.integrity;

import in.ommishra.ledgerline.IntegrationTest;
import in.ommishra.ledgerline.LedgerFixtures;
import in.ommishra.ledgerline.account.Account;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class LedgerIntegrityTest {

    @Autowired
    LedgerIntegrityService integrity;

    @Autowired
    LedgerFixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockMvc mvc;

    @Test
    void ledgerWrittenThroughTheServiceIsConsistent() throws Exception {
        fixtures.fundedWallet(fixtures.bank(), 7_000);

        mvc.perform(get("/api/v1/ledger/integrity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.healthy").value(true))
                .andExpect(jsonPath("$.unbalancedEntries.length()").value(0));
    }

    @Test
    void detectsBalanceTamperedOutsideTheLedger() {
        Account wallet = fixtures.fundedWallet(fixtures.bank(), 7_000);
        jdbc.update("update accounts set balance = balance + 1 where id = ?", wallet.getId());
        try {
            IntegrityReport report = integrity.check();

            assertThat(report.healthy()).isFalse();
            assertThat(report.balanceMismatches())
                    .singleElement()
                    .satisfies(m -> {
                        assertThat(m.accountId()).isEqualTo(wallet.getId());
                        assertThat(m.cachedBalance()).isEqualTo(7_001);
                        assertThat(m.derivedBalance()).isEqualTo(7_000);
                    });
        } finally {
            jdbc.update("update accounts set balance = balance - 1 where id = ?", wallet.getId());
        }
        assertThat(integrity.check().healthy()).isTrue();
    }

    @Test
    void detectsUnbalancedEntry() {
        Account wallet = fixtures.fundedWallet(fixtures.bank(), 7_000);
        UUID entryId = jdbc.queryForObject(
                "select journal_entry_id from postings where account_id = ? limit 1", UUID.class, wallet.getId());
        UUID rogue = UUID.randomUUID();
        Account other = fixtures.wallet();
        jdbc.update("""
                insert into postings (id, journal_entry_id, account_id, direction, amount, created_at)
                values (?, ?, ?, 'CREDIT', 5, now())
                """, rogue, entryId, other.getId());
        try {
            IntegrityReport report = integrity.check();

            assertThat(report.unbalancedEntries()).containsExactly(entryId);
            assertThat(report.balanceMismatches()).extracting(IntegrityReport.BalanceMismatch::accountId)
                    .containsExactly(other.getId());
        } finally {
            jdbc.update("delete from postings where id = ?", rogue);
        }
    }
}
