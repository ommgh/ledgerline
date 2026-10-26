package in.ommishra.ledgerline.idempotency;

/** A response plus whether it was replayed from an earlier request with the same key. */
public record Idempotent<T>(T value, boolean replayed) {
}
