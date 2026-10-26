package in.ommishra.ledgerline.integrity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IntegrityController {

    private final LedgerIntegrityService integrity;

    public IntegrityController(LedgerIntegrityService integrity) {
        this.integrity = integrity;
    }

    /** Runs a full check now. Scans every posting, so keep it behind an admin route in production. */
    @GetMapping("/api/v1/ledger/integrity")
    IntegrityReport check() {
        return integrity.check();
    }
}
