package in.ommishra.ledgerline.journal;

import in.ommishra.ledgerline.common.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerServiceTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    @Test
    void acceptsBalancedMultiLegEntry() {
        assertThatCode(() -> LedgerService.validateBalanced(List.of(
                Leg.debit(a, 1_000), Leg.credit(b, 950), Leg.credit(c, 50))))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsUnbalancedEntry() {
        assertThatThrownBy(() -> LedgerService.validateBalanced(List.of(Leg.debit(a, 100), Leg.credit(b, 99))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Debits (100) must equal credits (99)");
    }

    @Test
    void rejectsSingleLeg() {
        assertThatThrownBy(() -> LedgerService.validateBalanced(List.of(Leg.debit(a, 100))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsNonPositiveAmounts() {
        assertThatThrownBy(() -> LedgerService.validateBalanced(List.of(Leg.debit(a, 0), Leg.credit(b, 0))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void detectsOverflowInsteadOfWrappingAround() {
        assertThatThrownBy(() -> LedgerService.validateBalanced(List.of(
                Leg.debit(a, Long.MAX_VALUE), Leg.debit(b, 1), Leg.credit(c, 1))))
                .isInstanceOf(ArithmeticException.class);
    }
}
