package in.ommishra.ledgerline.payout;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PayoutStatusTest {

    @ParameterizedTest
    @CsvSource({
            "REQUESTED, PROCESSED, true",
            "REQUESTED, FAILED,    true",
            "REQUESTED, REVERSED,  false",
            "PROCESSED, REVERSED,  true",
            "PROCESSED, FAILED,    false",
            "FAILED,    PROCESSED, false",
            "REVERSED,  PROCESSED, false",
    })
    void allowsOnlyForwardTransitions(PayoutStatus from, PayoutStatus to, boolean allowed) {
        assertThat(from.canMoveTo(to)).isEqualTo(allowed);
    }
}
