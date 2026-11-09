package com.reservix.payment;

public interface PaymentGateway {
    Checkout createCheckout(Long bookingId, String description, int amountCents);

    record Checkout(String id, String url) {
    }
}
