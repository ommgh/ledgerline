package in.ommishra.ledgerline.common;

public enum Direction {
    DEBIT,
    CREDIT;

    public Direction opposite() {
        return this == DEBIT ? CREDIT : DEBIT;
    }
}
