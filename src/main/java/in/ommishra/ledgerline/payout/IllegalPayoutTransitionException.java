package in.ommishra.ledgerline.payout;

import java.util.UUID;

public class IllegalPayoutTransitionException extends RuntimeException {

    public IllegalPayoutTransitionException(UUID payoutId, PayoutStatus from, PayoutStatus to) {
        super("Payout %s cannot move from %s to %s".formatted(payoutId, from, to));
    }
}
