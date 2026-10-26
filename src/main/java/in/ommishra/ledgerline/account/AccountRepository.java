package in.ommishra.ledgerline.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByCode(String code);

    boolean existsByCode(String code);

    /**
     * Row-locks the accounts in id order. Every transaction acquires its locks in
     * the same order, so two transfers between the same pair of accounts in
     * opposite directions queue instead of deadlocking.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id in :ids order by a.id")
    List<Account> lockAllById(Collection<UUID> ids);
}
