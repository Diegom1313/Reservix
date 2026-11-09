package com.reservix.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class Scheduler {
    private final BookingService bookings;
    private final OutboxPublisher publisher;

    public Scheduler(BookingService bookings, OutboxPublisher publisher) {
        this.bookings = bookings;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelay = 60_000)
    public void expireHolds() {
        bookings.expireHolds();
    }

    @Scheduled(fixedDelay = 5_000)
    public void publishOutbox() {
        publisher.publishPending();
    }
}
