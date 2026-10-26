package in.ommishra.ledgerline.payout;

import in.ommishra.ledgerline.idempotency.Idempotent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payouts")
public class PayoutController {

    private final PayoutService payoutService;

    public PayoutController(PayoutService payoutService) {
        this.payoutService = payoutService;
    }

    @PostMapping
    ResponseEntity<PayoutResponse> request(@RequestHeader("Idempotency-Key") @Size(min = 1, max = 128) String key,
                                           @Valid @RequestBody PayoutRequest request) {
        Idempotent<PayoutResponse> result = payoutService.request(key, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(result.value());
    }

    @GetMapping("/{id}")
    PayoutResponse get(@PathVariable UUID id) {
        return payoutService.get(id);
    }
}
