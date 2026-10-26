package in.ommishra.ledgerline.payout;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface PayoutRepository extends JpaRepository<Payout, UUID> {

    /** Serialises webhook deliveries for the same payout, which providers may send concurrently. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payout p where p.id = :id")
    Optional<Payout> lockById(UUID id);
}
