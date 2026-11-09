package com.reservix.service;

public interface Notifier {
    void send(String eventType, Long bookingId);
}
