package in.ommishra.ledgerline.journal;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PostingRepository extends JpaRepository<Posting, UUID> {

    @EntityGraph(attributePaths = "journalEntry")
    Slice<Posting> findByAccountIdOrderByCreatedAtDescIdDesc(UUID accountId, Pageable pageable);
}
