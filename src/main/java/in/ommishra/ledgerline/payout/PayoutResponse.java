package in.ommishra.ledgerline.payout;

import java.time.Instant;
import java.util.UUID;

public record PayoutResponse(
        UUID id,
        UUID walletAccountId,
        long amount,
        long fee,
        String currency,
        String beneficiary,
        PayoutStatus status,
        String providerPayoutId,
        String failureReason,
        Instant createdAt,
        Instant updatedAt) {

    static PayoutResponse from(Payout payout) {
        return new PayoutResponse(payout.getId(), payout.getWalletAccountId(), payout.getAmount(), payout.getFee(),
                payout.getCurrency(), payout.getBeneficiary(), payout.getStatus(), payout.getProviderPayoutId(),
                payout.getFailureReason(), payout.getCreatedAt(), payout.getUpdatedAt());
    }
}
