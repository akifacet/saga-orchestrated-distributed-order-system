package io.github.akifacet.order_service.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query("""
            select e
            from OutboxEvent e
            where (e.status = 'NEW' or e.status = 'FAILED')
              and e.nextAttemptAt <= :now
            order by e.createdAt asc
            """)
    List<OutboxEvent> findNextBatch(@Param("now") Instant now);
}


