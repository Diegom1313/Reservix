package com.reservix.service;

import com.reservix.domain.OutboxEvent;
import com.reservix.repository.OutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxPublisher {
    private final OutboxRepository outbox;
    private final Notifier notifier;

    public OutboxPublisher(OutboxRepository outbox, Notifier notifier) {
        this.outbox = outbox;
        this.notifier = notifier;
    }

    @Transactional
    public int publishPending() {
        var pending = outbox.lockPending();
        for (OutboxEvent event : pending) {
            notifier.send(event.getType(), event.getBookingId());
            event.markPublished();
        }
        return pending.size();
    }
}
