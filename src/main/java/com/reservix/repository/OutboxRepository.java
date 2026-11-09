package com.reservix.repository;

import com.reservix.domain.OutboxEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {
    @Query(value = "SELECT * FROM outbox_events WHERE published_at IS NULL AND next_attempt_at <= now() ORDER BY id LIMIT 50 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEvent> lockPending();
}
