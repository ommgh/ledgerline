package in.ommishra.ledgerline.account;

import in.ommishra.ledgerline.common.BusinessRuleException;
import in.ommishra.ledgerline.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accounts;

    public AccountService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional
    public Account open(OpenAccountRequest request) {
        if (accounts.existsByCode(request.code())) {
            throw new BusinessRuleException("duplicate_account_code", "Account code %s is taken".formatted(request.code()));
        }
        return accounts.save(new Account(request.code(), request.name(), request.type(), request.currency(),
                Boolean.TRUE.equals(request.overdraftAllowed())));
    }

    @Transactional(readOnly = true)
    public Account get(UUID id) {
        return accounts.findById(id).orElseThrow(() -> new NotFoundException("Account %s not found".formatted(id)));
    }
}
