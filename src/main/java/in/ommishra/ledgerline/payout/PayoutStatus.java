package in.ommishra.ledgerline.payout;

public enum PayoutStatus {
    REQUESTED,
    PROCESSED,
    FAILED,
    REVERSED;

    boolean canMoveTo(PayoutStatus next) {
        return switch (this) {
            case REQUESTED -> next == PROCESSED || next == FAILED;
            case PROCESSED -> next == REVERSED;
            case FAILED, REVERSED -> false;
        };
    }
}
