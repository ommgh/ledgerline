package in.ommishra.ledgerline.journal;

import in.ommishra.ledgerline.account.AccountService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class StatementController {

    private final AccountService accountService;
    private final PostingRepository postings;

    public StatementController(AccountService accountService, PostingRepository postings) {
        this.accountService = accountService;
        this.postings = postings;
    }

    @GetMapping("/api/v1/accounts/{id}/postings")
    @Transactional(readOnly = true)
    public StatementPage statement(@PathVariable UUID id,
                                   @RequestParam(defaultValue = "0") @Min(0) int page,
                                   @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        accountService.get(id);
        Slice<Posting> slice = postings.findByAccountIdOrderByCreatedAtDescIdDesc(id, PageRequest.of(page, size));
        return new StatementPage(slice.map(PostingResponse::from).getContent(), page, size, slice.hasNext());
    }

    public record StatementPage(List<PostingResponse> items, int page, int size, boolean hasNext) {
    }
}
