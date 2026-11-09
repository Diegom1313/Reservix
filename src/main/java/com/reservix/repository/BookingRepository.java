package com.reservix.repository;

import com.reservix.domain.Booking;
import com.reservix.domain.BookingStatus;
import java.time.Instant;
import java.util.Collection;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    long countBySessionIdAndStatusIn(Long sessionId, Collection<BookingStatus> statuses);

    boolean existsBySessionIdAndUserIdAndStatusIn(Long sessionId, Long userId, Collection<BookingStatus> statuses);

    @EntityGraph(attributePaths = "session")
    List<Booking> findByUserIdOrderByIdDesc(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.status = :status and b.holdExpiresAt < :limit")
    List<Booking> lockExpired(@Param("status") BookingStatus status, @Param("limit") Instant limit);

    @Query("select b.session.id as sessionId, count(b) as total from Booking b where b.session.id in :ids and b.status in :statuses group by b.session.id")
    List<SeatCount> countActive(@Param("ids") Collection<Long> ids, @Param("statuses") Collection<BookingStatus> statuses);

    interface SeatCount {
        Long getSessionId();
        long getTotal();
    }
}
