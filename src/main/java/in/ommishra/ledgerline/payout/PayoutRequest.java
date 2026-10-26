package in.ommishra.ledgerline.payout;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record PayoutRequest(
        @NotNull UUID walletAccountId,
        @Positive long amount,
        @PositiveOrZero long fee,
        @NotNull @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotBlank @Size(max = 255) String beneficiary) {
}
