package in.ommishra.ledgerline.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByCode(String code);

    boolean existsByCode(String code);
}
