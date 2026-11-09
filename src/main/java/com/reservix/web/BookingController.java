package com.reservix.web;

import com.reservix.domain.Booking;
import com.reservix.payment.PaymentGateway;
import com.reservix.service.BookingService;
import com.reservix.web.Dtos.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {
    private final BookingService bookings;
    private final PaymentGateway payments;

    public BookingController(BookingService bookings, PaymentGateway payments) {
        this.bookings = bookings;
        this.payments = payments;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse reserve(@Valid @RequestBody BookingRequest r, @AuthenticationPrincipal Jwt jwt) {
        Booking booking = bookings.reserve(r.sessionId(), userId(jwt));
        var session = booking.getSession();
        PaymentGateway.Checkout checkout;
        try {
            checkout = payments.createCheckout(booking.getId(), session.getTitle(), session.getPriceCents());
        } catch (RuntimeException e) {
            bookings.releaseUnpaid(booking.getId());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "El servicio de pago no está disponible", e);
        }
        bookings.attachCheckout(booking.getId(), checkout.id());
        return new BookingResponse(booking.getId(), session.getId(), session.getTitle(), booking.getStatus().name(),
                booking.getHoldExpiresAt(), checkout.url());
    }

    @GetMapping("/mine")
    public List<BookingResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return bookings.mine(userId(jwt)).stream()
                .map(b -> new BookingResponse(b.getId(), b.getSession().getId(), b.getSession().getTitle(), b.getStatus().name(), b.getHoldExpiresAt(), null))
                .toList();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        bookings.cancel(id, userId(jwt));
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
