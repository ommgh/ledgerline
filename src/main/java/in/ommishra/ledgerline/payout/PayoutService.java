package in.ommishra.ledgerline.payout;

import in.ommishra.ledgerline.account.SystemAccounts;
import in.ommishra.ledgerline.common.NotFoundException;
import in.ommishra.ledgerline.idempotency.Idempotent;
import in.ommishra.ledgerline.idempotency.IdempotencyService;
import in.ommishra.ledgerline.journal.EntryKind;
import in.ommishra.ledgerline.journal.LedgerService;
import in.ommishra.ledgerline.journal.LedgerTransactions;
import in.ommishra.ledgerline.journal.Leg;
import in.ommishra.ledgerline.outbox.Outbox;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static in.ommishra.ledgerline.account.SystemAccounts.BANK;
import static in.ommishra.ledgerline.account.SystemAccounts.PAYOUTS_IN_TRANSIT;
import static in.ommishra.ledgerline.account.SystemAccounts.PAYOUT_FEES;

/**
 * Payout lifecycle. Money is held in payouts-in-transit as soon as a payout is
 * requested, so the wallet cannot spend it twice while the provider works.
 *
 * <pre>
 * REQUESTED --processed--> PROCESSED --reversed--> REVERSED
 *     \--failed--> FAILED
 * </pre>
 */
@Service
public class PayoutService {

    private final PayoutRepository payouts;
    private final LedgerService ledger;
    private final LedgerTransactions transactions;
    private final IdempotencyService idempotency;
    private final SystemAccounts system;
    private final Outbox outbox;

    public PayoutService(PayoutRepository payouts, LedgerService ledger, LedgerTransactions transactions,
                         IdempotencyService idempotency, SystemAccounts system, Outbox outbox) {
        this.payouts = payouts;
        this.ledger = ledger;
        this.transactions = transactions;
        this.idempotency = idempotency;
        this.system = system;
        this.outbox = outbox;
    }

    public Idempotent<PayoutResponse> request(String idempotencyKey, PayoutRequest request) {
        return idempotency.execute("payouts", idempotencyKey, request, PayoutResponse.class, () -> {
            Payout payout = payouts.save(new Payout(request.walletAccountId(), request.amount(), request.fee(),
                    request.currency(), request.beneficiary()));

            List<Leg> legs = new ArrayList<>();
            legs.add(Leg.debit(payout.getWalletAccountId(), payout.totalDebited()));
            legs.add(Leg.credit(system.id(PAYOUTS_IN_TRANSIT), payout.getAmount()));
            if (payout.getFee() > 0) {
                legs.add(Leg.credit(system.id(PAYOUT_FEES), payout.getFee()));
            }
            ledger.post(EntryKind.PAYOUT_HOLD, payout.getCurrency(), reference(payout), "Payout hold", legs);

            PayoutResponse response = PayoutResponse.from(payout);
            // a dispatcher consumes this and calls the provider's payout API with reference_id = payout id
            outbox.append("Payout", payout.getId(), "PayoutRequested", response);
            return response;
        });
    }

    /** Applies a provider status update. Returns false when it was a duplicate delivery. */
    public boolean applyProviderUpdate(UUID payoutId, PayoutStatus next, String providerPayoutId, String failureReason) {
        return transactions.execute(() -> {
            Payout payout = payouts.lockById(payoutId)
                    .orElseThrow(() -> new NotFoundException("Payout %s not found".formatted(payoutId)));
            String reason = next == PayoutStatus.PROCESSED ? null : failureReason;
            if (!payout.moveTo(next, providerPayoutId, reason)) {
                return false;
            }
            ledger.post(entryKind(next), payout.getCurrency(), reference(payout), "Payout " + next.name().toLowerCase(),
                    legs(payout, next));
            outbox.append("Payout", payout.getId(), "Payout" + capitalize(next), PayoutResponse.from(payout));
            return true;
        });
    }

    @Transactional(readOnly = true)
    public PayoutResponse get(UUID id) {
        return payouts.findById(id).map(PayoutResponse::from)
                .orElseThrow(() -> new NotFoundException("Payout %s not found".formatted(id)));
    }

    private List<Leg> legs(Payout payout, PayoutStatus next) {
        UUID wallet = payout.getWalletAccountId();
        UUID inTransit = system.id(PAYOUTS_IN_TRANSIT);
        UUID bank = system.id(BANK);
        List<Leg> legs = new ArrayList<>();
        switch (next) {
            case PROCESSED -> {
                legs.add(Leg.debit(inTransit, payout.getAmount()));
                legs.add(Leg.credit(bank, payout.getAmount()));
            }
            case FAILED -> {
                legs.add(Leg.debit(inTransit, payout.getAmount()));
                refundFee(payout, legs);
                legs.add(Leg.credit(wallet, payout.totalDebited()));
            }
            case REVERSED -> {
                legs.add(Leg.debit(bank, payout.getAmount()));
                refundFee(payout, legs);
                legs.add(Leg.credit(wallet, payout.totalDebited()));
            }
            case REQUESTED -> throw new IllegalArgumentException("REQUESTED is not a provider update");
        }
        return legs;
    }

    private void refundFee(Payout payout, List<Leg> legs) {
        if (payout.getFee() > 0) {
            legs.add(Leg.debit(system.id(PAYOUT_FEES), payout.getFee()));
        }
    }

    private static EntryKind entryKind(PayoutStatus next) {
        return switch (next) {
            case PROCESSED -> EntryKind.PAYOUT_SETTLEMENT;
            case FAILED -> EntryKind.PAYOUT_RELEASE;
            case REVERSED -> EntryKind.PAYOUT_REVERSAL;
            case REQUESTED -> throw new IllegalArgumentException("REQUESTED is not a provider update");
        };
    }

    private static String reference(Payout payout) {
        return "payout:" + payout.getId();
    }

    private static String capitalize(PayoutStatus status) {
        String name = status.name().toLowerCase();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
