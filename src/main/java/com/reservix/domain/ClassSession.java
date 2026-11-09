package com.reservix.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "class_sessions")
public class ClassSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    @Column(name = "starts_at")
    private Instant startsAt;
    private int capacity;
    @Column(name = "price_cents")
    private int priceCents;

    protected ClassSession() {
    }

    public ClassSession(String title, Instant startsAt, int capacity, int priceCents) {
        this.title = title;
        this.startsAt = startsAt;
        this.capacity = capacity;
        this.priceCents = priceCents;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public Instant getStartsAt() { return startsAt; }
    public int getCapacity() { return capacity; }
    public int getPriceCents() { return priceCents; }
}
