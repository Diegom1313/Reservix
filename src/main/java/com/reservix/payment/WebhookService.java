package com.reservix.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.reservix.repository.ProcessedWebhookRepository;
import com.reservix.service.BookingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookService {
    private final ProcessedWebhookRepository processed;
    private final BookingService bookings;

    public WebhookService(ProcessedWebhookRepository processed, BookingService bookings) {
        this.processed = processed;
        this.bookings = bookings;
    }

    @Transactional
    public void handle(JsonNode event) {
        if (!processed.markProcessed(event.path("id").asText())) {
            return;
        }
        if ("checkout.session.completed".equals(event.path("type").asText())) {
            long bookingId = event.path("data").path("object").path("metadata").path("booking_id").asLong();
            bookings.confirmPaid(bookingId);
        }
    }
}
