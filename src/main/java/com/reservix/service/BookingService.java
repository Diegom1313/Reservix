package com.reservix.service;

import com.reservix.domain.*;
import com.reservix.repository.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
    private static final Set<BookingStatus> ACTIVE = Set.of(BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED);

    private final ClassSessionRepository sessions;
    private final BookingRepository bookings;
    private final UserRepository users;
    private final OutboxRepository outbox;
    private final Duration hold;

    public BookingService(ClassSessionRepository sessions, BookingRepository bookings, UserRepository users,
                          OutboxRepository outbox, @Value("${reservix.booking.hold-minutes}") long holdMinutes) {
        this.sessions = sessions;
        this.bookings = bookings;
        this.users = users;
        this.outbox = outbox;
        this.hold = Duration.ofMinutes(holdMinutes);
    }

    @Transactional
    public Booking reserve(Long sessionId, Long userId) {
        ClassSession session = sessions.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new NotFoundException("Clase no encontrada"));
        if (session.getStartsAt().isBefore(Instant.now())) {
            throw new ConflictException("La clase ya ha empezado");
        }
        if (bookings.existsBySessionIdAndUserIdAndStatusIn(sessionId, userId, ACTIVE)) {
            throw new ConflictException("Ya tienes una reserva en esta clase");
        }
        if (bookings.countBySessionIdAndStatusIn(sessionId, ACTIVE) >= session.getCapacity()) {
            throw new ConflictException("No quedan plazas");
        }
        return bookings.save(new Booking(session, users.getReferenceById(userId), Instant.now().plus(hold)));
    }

    @Transactional
    public void attachCheckout(Long bookingId, String checkoutId) {
        bookings.findById(bookingId).orElseThrow().attachCheckout(checkoutId);
    }

    @Transactional
    public boolean confirmPaid(Long bookingId) {
        Booking booking = bookings.findById(bookingId).orElseThrow(() -> new NotFoundException("Reserva no encontrada"));
        if (!booking.confirm()) {
            outbox.save(new OutboxEvent("REFUND_REQUIRED", bookingId));
            return false;
        }
        outbox.save(new OutboxEvent("BOOKING_CONFIRMED", bookingId));
        return true;
    }

    @Transactional
    public void cancel(Long bookingId, Long userId) {
        Booking booking = bookings.findById(bookingId).orElseThrow(() -> new NotFoundException("Reserva no encontrada"));
        if (!booking.getUser().getId().equals(userId)) {
            throw new NotFoundException("Reserva no encontrada");
        }
        if (!ACTIVE.contains(booking.getStatus())) {
            throw new ConflictException("La reserva no se puede cancelar");
        }
        boolean wasPaid = booking.getStatus() == BookingStatus.CONFIRMED;
        booking.cancel();
        outbox.save(new OutboxEvent(wasPaid ? "REFUND_REQUIRED" : "BOOKING_CANCELLED", bookingId));
    }

    @Transactional(readOnly = true)
    public List<Booking> mine(Long userId) {
        return bookings.findByUserIdOrderByIdDesc(userId);
    }

    @Transactional(readOnly = true)
    public long availableSeats(ClassSession session) {
        return session.getCapacity() - bookings.countBySessionIdAndStatusIn(session.getId(), ACTIVE);
    }

    @Transactional
    public int expireHolds() {
        List<Booking> expired = bookings.findByStatusAndHoldExpiresAtBefore(BookingStatus.PENDING_PAYMENT, Instant.now());
        expired.forEach(Booking::expire);
        return expired.size();
    }
}
