package com.reservix.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String type;
    @Column(name = "booking_id")
    private Long bookingId;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "published_at")
    private Instant publishedAt;
    private int attempts;
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(String type, Long bookingId) {
        this.type = type;
        this.bookingId = bookingId;
        this.createdAt = Instant.now();
        this.nextAttemptAt = this.createdAt;
    }

    public void markPublished() {
        this.publishedAt = Instant.now();
    }

    public void registerFailure() {
        attempts++;
        nextAttemptAt = Instant.now().plusSeconds(Math.min(3600, 30L * (1L << Math.min(attempts, 7))));
    }

    public int getAttempts() { return attempts; }
    public Long getId() { return id; }
    public String getType() { return type; }
    public Long getBookingId() { return bookingId; }
    public Instant getPublishedAt() { return publishedAt; }
}
