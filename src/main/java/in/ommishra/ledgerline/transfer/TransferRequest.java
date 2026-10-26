package in.ommishra.ledgerline.transfer;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Moves {@code amount} by debiting one account and crediting another. For two
 * wallets (liabilities) that means "from" is the debit side and "to" the credit side.
 */
public record TransferRequest(
        @NotNull UUID debitAccountId,
        @NotNull UUID creditAccountId,
        @Positive long amount,
        @NotNull @Pattern(regexp = "[A-Z]{3}") String currency,
        @Size(max = 128) String reference,
        @Size(max = 512) String description) {
}
