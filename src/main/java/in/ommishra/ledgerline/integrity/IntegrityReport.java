package in.ommishra.ledgerline.integrity;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IntegrityReport(
        Instant checkedAt,
        List<UUID> unbalancedEntries,
        List<BalanceMismatch> balanceMismatches) {

    public record BalanceMismatch(UUID accountId, String code, long cachedBalance, long derivedBalance) {
    }

    @JsonProperty
    public boolean healthy() {
        return unbalancedEntries.isEmpty() && balanceMismatches.isEmpty();
    }

    @JsonProperty
    public int violations() {
        return unbalancedEntries.size() + balanceMismatches.size();
    }
}
