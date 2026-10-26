package in.ommishra.ledgerline.account;

import in.ommishra.ledgerline.common.Direction;

public enum AccountType {
    ASSET(Direction.DEBIT),
    EXPENSE(Direction.DEBIT),
    LIABILITY(Direction.CREDIT),
    EQUITY(Direction.CREDIT),
    REVENUE(Direction.CREDIT);

    private final Direction normalSide;

    AccountType(Direction normalSide) {
        this.normalSide = normalSide;
    }

    /** The side that increases the balance: debits grow assets, credits grow liabilities. */
    public Direction normalSide() {
        return normalSide;
    }
}
