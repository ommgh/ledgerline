package in.ommishra.ledgerline.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OpenAccountRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[a-z0-9:._-]+") String code,
        @NotBlank @Size(max = 255) String name,
        @NotNull AccountType type,
        @NotNull @Pattern(regexp = "[A-Z]{3}") String currency,
        Boolean overdraftAllowed) {
}
