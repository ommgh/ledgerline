package in.ommishra.ledgerline.journal;

public enum EntryKind {
    TRANSFER,
    /** Wallet funds (amount + fee) move into payouts-in-transit when a payout is requested. */
    PAYOUT_HOLD,
    /** The provider paid the beneficiary: in-transit funds leave the bank balance. */
    PAYOUT_SETTLEMENT,
    /** The provider rejected the payout: the hold and fee go back to the wallet. */
    PAYOUT_RELEASE,
    /** A settled payout bounced back from the beneficiary's bank. */
    PAYOUT_REVERSAL
}
