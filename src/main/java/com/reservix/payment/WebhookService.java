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
        String type = event.path("type").asText();
        JsonNode session = event.path("data").path("object");
        long bookingId = session.path("metadata").path("booking_id").asLong();
        boolean paid = "checkout.session.async_payment_succeeded".equals(type)
                || ("checkout.session.completed".equals(type) && "paid".equals(session.path("payment_status").asText()));
        if (paid && bookingId > 0) {
            bookings.confirmPaid(bookingId, session.path("payment_intent").asText(null));
        }
    }
}
