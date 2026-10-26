package in.ommishra.ledgerline.journal;

import in.ommishra.ledgerline.common.Direction;

import java.util.UUID;

/** One side of a journal entry before it is posted. */
public record Leg(UUID accountId, Direction direction, long amount) {

    public static Leg debit(UUID accountId, long amount) {
        return new Leg(accountId, Direction.DEBIT, amount);
    }

    public static Leg credit(UUID accountId, long amount) {
        return new Leg(accountId, Direction.CREDIT, amount);
    }
}
