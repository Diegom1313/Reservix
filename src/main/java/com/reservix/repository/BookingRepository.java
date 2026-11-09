package com.reservix.repository;

import com.reservix.domain.Booking;
import com.reservix.domain.BookingStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    long countBySessionIdAndStatusIn(Long sessionId, Collection<BookingStatus> statuses);

    boolean existsBySessionIdAndUserIdAndStatusIn(Long sessionId, Long userId, Collection<BookingStatus> statuses);

    @EntityGraph(attributePaths = "session")
    List<Booking> findByUserIdOrderByIdDesc(Long userId);

    List<Booking> findByStatusAndHoldExpiresAtBefore(BookingStatus status, Instant limit);
}
