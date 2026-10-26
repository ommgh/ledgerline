package in.ommishra.ledgerline.account;

import in.ommishra.ledgerline.common.BusinessRuleException;
import org.junit.jupiter.api.Test;

import static in.ommishra.ledgerline.common.Direction.CREDIT;
import static in.ommishra.ledgerline.common.Direction.DEBIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    @Test
    void creditIncreasesLiabilityAndDebitDecreasesIt() {
        Account wallet = new Account("wallet:1", "Wallet", AccountType.LIABILITY, "INR", false);

        wallet.apply(CREDIT, 500);
        wallet.apply(DEBIT, 200);

        assertThat(wallet.getBalance()).isEqualTo(300);
    }

    @Test
    void debitIncreasesAsset() {
        Account bank = new Account("bank", "Bank", AccountType.ASSET, "INR", true);

        bank.apply(DEBIT, 1_000);

        assertThat(bank.getBalance()).isEqualTo(1_000);
    }

    @Test
    void rejectsOverdraftUnlessAllowed() {
        Account wallet = new Account("wallet:1", "Wallet", AccountType.LIABILITY, "INR", false);
        wallet.apply(CREDIT, 100);

        assertThatThrownBy(() -> wallet.apply(DEBIT, 101))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("wallet:1");
        assertThat(wallet.getBalance()).isEqualTo(100);
    }

    @Test
    void overdraftAccountMayGoNegative() {
        Account bank = new Account("bank", "Bank", AccountType.ASSET, "INR", true);

        bank.apply(CREDIT, 250);

        assertThat(bank.getBalance()).isEqualTo(-250);
    }
}
