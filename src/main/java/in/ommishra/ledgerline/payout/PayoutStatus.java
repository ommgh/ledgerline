package in.ommishra.ledgerline.payout;

public enum PayoutStatus {
    REQUESTED,
    PROCESSED,
    FAILED,
    REVERSED;

    /**
     * REQUESTED can move straight to REVERSED because provider webhooks may arrive
     * out of order: the reversal can land before the processed event it follows.
     */
    boolean canMoveTo(PayoutStatus next) {
        return switch (this) {
            case REQUESTED -> next == PROCESSED || next == FAILED || next == REVERSED;
            case PROCESSED -> next == REVERSED;
            case FAILED, REVERSED -> false;
        };
    }
}
