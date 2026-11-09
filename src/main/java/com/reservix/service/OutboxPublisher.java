package com.reservix.service;

import com.reservix.domain.OutboxEvent;
import com.reservix.payment.PaymentGateway;
import com.reservix.repository.BookingRepository;
import com.reservix.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outbox;
    private final Notifier notifier;
    private final BookingRepository bookings;
    private final PaymentGateway payments;

    public OutboxPublisher(OutboxRepository outbox, Notifier notifier, BookingRepository bookings, PaymentGateway payments) {
        this.outbox = outbox;
        this.notifier = notifier;
        this.bookings = bookings;
        this.payments = payments;
    }

    @Transactional
    public int publishPending() {
        int published = 0;
        for (OutboxEvent event : outbox.lockPending()) {
            try {
                if ("REFUND_REQUIRED".equals(event.getType())) {
                    refund(event.getBookingId());
                }
                notifier.send(event.getType(), event.getBookingId());
                event.markPublished();
                published++;
            } catch (RuntimeException e) {
                event.registerFailure();
                log.warn("Evento {} no entregado (intento {})", event.getId(), event.getAttempts(), e);
            }
        }
        return published;
    }

    private void refund(Long bookingId) {
        String paymentIntent = bookings.findById(bookingId).orElseThrow().getPaymentIntent();
        if (paymentIntent == null) {
            throw new IllegalStateException("La reserva no tiene un pago asociado");
        }
        payments.refund(paymentIntent, bookingId);
    }
}
