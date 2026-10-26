package in.ommishra.ledgerline.transfer;

import in.ommishra.ledgerline.idempotency.Idempotent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/api/v1/transfers")
    ResponseEntity<TransferResponse> transfer(@RequestHeader("Idempotency-Key") @Size(min = 1, max = 128) String key,
                                              @Valid @RequestBody TransferRequest request) {
        Idempotent<TransferResponse> result = transferService.transfer(key, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(result.value());
    }
}
