package in.ommishra.ledgerline.account;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    ResponseEntity<AccountResponse> open(@Valid @RequestBody OpenAccountRequest request) {
        AccountResponse body = AccountResponse.from(accountService.open(request));
        return ResponseEntity.created(URI.create("/api/v1/accounts/" + body.id())).body(body);
    }

    @GetMapping("/{id}")
    AccountResponse get(@PathVariable UUID id) {
        return AccountResponse.from(accountService.get(id));
    }
}
