package com.reservix.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingNotifier implements Notifier {
    private static final Logger log = LoggerFactory.getLogger(LoggingNotifier.class);

    @Override
    public void send(String eventType, Long bookingId) {
        log.info("Notificación {} para la reserva {}", eventType, bookingId);
    }
}
