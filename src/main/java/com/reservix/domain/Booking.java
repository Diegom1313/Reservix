package com.reservix.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "bookings")
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id")
    private ClassSession session;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;
    @Enumerated(EnumType.STRING)
    private BookingStatus status;
    @Column(name = "checkout_id")
    private String checkoutId;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    protected Booking() {
    }

    public Booking(ClassSession session, AppUser user, Instant holdExpiresAt) {
        this.session = session;
        this.user = user;
        this.status = BookingStatus.PENDING_PAYMENT;
        this.createdAt = Instant.now();
        this.holdExpiresAt = holdExpiresAt;
    }

    public boolean confirm() {
        if (status != BookingStatus.PENDING_PAYMENT) {
            return false;
        }
        status = BookingStatus.CONFIRMED;
        return true;
    }

    public void cancel() {
        status = BookingStatus.CANCELLED;
    }

    public void expire() {
        status = BookingStatus.EXPIRED;
    }

    public void attachCheckout(String id) {
        this.checkoutId = id;
    }

    public Long getId() { return id; }
    public ClassSession getSession() { return session; }
    public AppUser getUser() { return user; }
    public BookingStatus getStatus() { return status; }
    public String getCheckoutId() { return checkoutId; }
    public Instant getHoldExpiresAt() { return holdExpiresAt; }
}
