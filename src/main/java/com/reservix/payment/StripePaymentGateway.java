package com.reservix.payment;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "reservix.payments.fake", havingValue = "false", matchIfMissing = true)
public class StripePaymentGateway implements PaymentGateway {
    private final RestClient client;
    private final String successUrl;
    private final String cancelUrl;

    public StripePaymentGateway(@Value("${reservix.payments.stripe-secret}") String secret,
                                @Value("${reservix.payments.success-url}") String successUrl,
                                @Value("${reservix.payments.cancel-url}") String cancelUrl) {
        if (secret.isBlank()) {
            throw new IllegalStateException("Falta STRIPE_SECRET");
        }
        this.client = RestClient.builder().baseUrl("https://api.stripe.com/v1")
                .defaultHeader("Authorization", "Bearer " + secret).build();
        this.successUrl = successUrl;
        this.cancelUrl = cancelUrl;
    }

    @Override
    public Checkout createCheckout(Long bookingId, String description, int amountCents) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("mode", "payment");
        form.add("success_url", successUrl);
        form.add("cancel_url", cancelUrl);
        form.add("metadata[booking_id]", bookingId.toString());
        form.add("line_items[0][quantity]", "1");
        form.add("line_items[0][price_data][currency]", "eur");
        form.add("line_items[0][price_data][unit_amount]", Integer.toString(amountCents));
        form.add("line_items[0][price_data][product_data][name]", description);
        JsonNode response = client.post().uri("/checkout/sessions")
                .header("Idempotency-Key", "booking-" + bookingId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(JsonNode.class);
        return new Checkout(response.get("id").asText(), response.get("url").asText());
    }

    @Override
    public void refund(String paymentIntent, Long bookingId) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("payment_intent", paymentIntent);
        client.post().uri("/refunds")
                .header("Idempotency-Key", "refund-" + bookingId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().toBodilessEntity();
    }
}
