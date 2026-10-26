package in.ommishra.ledgerline.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /** SKIP LOCKED lets several relay instances drain the table without blocking on each other. */
    @Query(value = """
            select * from outbox_events
            where published_at is null
            order by created_at, id
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<OutboxEvent> lockUnpublished(int limit);

    List<OutboxEvent> findByAggregateId(UUID aggregateId);
}
