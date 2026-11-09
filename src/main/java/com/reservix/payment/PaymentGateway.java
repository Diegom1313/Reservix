package com.reservix.payment;

public interface PaymentGateway {
    Checkout createCheckout(Long bookingId, String description, int amountCents);

    void refund(String paymentIntent, Long bookingId);

    record Checkout(String id, String url) {
    }
}
