package in.ommishra.ledgerline.payout;

import in.ommishra.ledgerline.IntegrationTest;
import in.ommishra.ledgerline.LedgerFixtures;
import in.ommishra.ledgerline.account.Account;
import in.ommishra.ledgerline.account.SystemAccounts;
import in.ommishra.ledgerline.common.BusinessRuleException;
import in.ommishra.ledgerline.outbox.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class PayoutTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    LedgerFixtures fixtures;

    @Autowired
    PayoutService payouts;

    @Autowired
    SystemAccounts system;

    @Autowired
    OutboxEventRepository outbox;

    @Autowired
    WebhookProperties webhookProperties;

    @Test
    void requestHoldsAmountPlusFee() {
        Account wallet = fixtures.fundedWallet(fixtures.bank(), 10_000);
        long inTransitBefore = balance(SystemAccounts.PAYOUTS_IN_TRANSIT);
        long feesBefore = balance(SystemAccounts.PAYOUT_FEES);

        PayoutResponse payout = request(wallet, 4_000, 20);

        assertThat(payout.status()).isEqualTo(PayoutStatus.REQUESTED);
        assertThat(fixtures.balance(wallet.getId())).isEqualTo(5_980);
        assertThat(balance(SystemAccounts.PAYOUTS_IN_TRANSIT) - inTransitBefore).isEqualTo(4_000);
        assertThat(balance(SystemAccounts.PAYOUT_FEES) - feesBefore).isEqualTo(20);
        assertThat(outbox.findByAggregateId(payout.id()))
                .extracting(e -> e.getEventType())
                .containsExactly("PayoutRequested");
    }

    @Test
    void cannotPayOutMoreThanTheWalletHolds() {
        Account wallet = fixtures.fundedWallet(fixtures.bank(), 1_000);

        assertThatThrownBy(() -> request(wallet, 990, 20))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot debit 1010");
    }

    @Test
    void processedWebhookSettlesAndDuplicateIsNoOp() throws Exception {
        Account wallet = fixtures.fundedWallet(fixtures.bank(), 10_000);
        PayoutResponse payout = request(wallet, 4_000, 20);
        long bankBefore = balance(SystemAccounts.BANK);
        long inTransitBefore = balance(SystemAccounts.PAYOUTS_IN_TRANSIT);

        webhook("payout.processed", payout.id(), "pout_A1").andExpect(jsonPath("$.status").value("applied"));
        webhook("payout.processed", payout.id(), "pout_A1").andExpect(jsonPath("$.status").value("duplicate"));

        mvc.perform(get("/api/v1/payouts/{id}", payout.id()))
                .andExpect(jsonPath("$.status").value("PROCESSED"))
                .andExpect(jsonPath("$.providerPayoutId").value("pout_A1"));
        assertThat(balance(SystemAccounts.BANK) - bankBefore).isEqualTo(-4_000);
        assertThat(balance(SystemAccounts.PAYOUTS_IN_TRANSIT) - inTransitBefore).isEqualTo(-4_000);
        assertThat(fixtures.balance(wallet.getId())).isEqualTo(5_980);
    }

    @Test
    void failedWebhookReturnsAmountAndFeeToWallet() throws Exception {
        Account wallet = fixtures.fundedWallet(fixtures.bank(), 10_000);
        long feesBefore = balance(SystemAccounts.PAYOUT_FEES);
        PayoutResponse payout = request(wallet, 4_000, 20);

        webhook("payout.failed", payout.id(), "pout_B1").andExpect(jsonPath("$.status").value("applied"));

        assertThat(fixtures.balance(wallet.getId())).isEqualTo(10_000);
        assertThat(balance(SystemAccounts.PAYOUT_FEES)).isEqualTo(feesBefore);
        assertThat(payouts.get(payout.id()).status()).isEqualTo(PayoutStatus.FAILED);
        assertThat(payouts.get(payout.id()).failureReason()).isEqualTo("Beneficiary bank was offline");
    }

    @Test
    void reversalAfterSettlementRefundsWallet() throws Exception {
        Account wallet = fixtures.fundedWallet(fixtures.bank(), 10_000);
        PayoutResponse payout = request(wallet, 4_000, 20);
        long bankBefore = balance(SystemAccounts.BANK);

        webhook("payout.processed", payout.id(), "pout_C1");
        webhook("payout.reversed", payout.id(), "pout_C1").andExpect(jsonPath("$.status").value("applied"));

        assertThat(fixtures.balance(wallet.getId())).isEqualTo(10_000);
        assertThat(balance(SystemAccounts.BANK)).isEqualTo(bankBefore);
        assertThat(payouts.get(payout.id()).status()).isEqualTo(PayoutStatus.REVERSED);
    }

    @Test
    void failureAfterSettlementIsIgnored() throws Exception {
        Account wallet = fixtures.fundedWallet(fixtures.bank(), 10_000);
        PayoutResponse payout = request(wallet, 4_000, 0);
        webhook("payout.processed", payout.id(), "pout_D1");

        webhook("payout.failed", payout.id(), "pout_D1").andExpect(jsonPath("$.status").value("ignored"));

        assertThat(payouts.get(payout.id()).status()).isEqualTo(PayoutStatus.PROCESSED);
        assertThat(fixtures.balance(wallet.getId())).isEqualTo(6_000);
    }

    @Test
    void rejectsForgedSignature() throws Exception {
        Account wallet = fixtures.fundedWallet(fixtures.bank(), 10_000);
        PayoutResponse payout = request(wallet, 4_000, 0);

        mvc.perform(post("/api/v1/webhooks/razorpay")
                        .header("X-Razorpay-Signature", "00".repeat(32))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event("payout.failed", payout.id(), "pout_E1")))
                .andExpect(status().isUnauthorized());

        assertThat(payouts.get(payout.id()).status()).isEqualTo(PayoutStatus.REQUESTED);
    }

    private PayoutResponse request(Account wallet, long amount, long fee) {
        return payouts.request(UUID.randomUUID().toString(),
                new PayoutRequest(wallet.getId(), amount, fee, "INR", "HDFC0001234 / 50100012345678")).value();
    }

    private long balance(String systemCode) {
        return fixtures.balance(system.id(systemCode));
    }

    private ResultActions webhook(String type, UUID payoutId, String providerId) throws Exception {
        String body = event(type, payoutId, providerId);
        return mvc.perform(post("/api/v1/webhooks/razorpay")
                        .header("X-Razorpay-Signature", sign(body))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    /** Shape of a Razorpay payout webhook, trimmed to the fields the service reads. */
    private static String event(String type, UUID payoutId, String providerId) {
        return """
                {"entity":"event","event":"%s","payload":{"payout":{"entity":{
                  "id":"%s","reference_id":"%s","status":"%s",
                  "status_details":{"description":"Beneficiary bank was offline"}}}}}
                """.formatted(type, providerId, payoutId, type.substring("payout.".length()));
    }

    private String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(webhookProperties.razorpaySecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
