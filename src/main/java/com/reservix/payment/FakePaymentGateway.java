package com.reservix.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "reservix.payments.fake", havingValue = "true")
public class FakePaymentGateway implements PaymentGateway {
    @Override
    public Checkout createCheckout(Long bookingId, String description, int amountCents) {
        return new Checkout("fake_" + bookingId, "https://pagos.invalid/" + bookingId);
    }

    @Override
    public void refund(String paymentIntent, Long bookingId) {
    }
}
