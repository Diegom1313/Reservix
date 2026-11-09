package com.reservix.web;

import jakarta.validation.constraints.*;
import java.time.Instant;

public final class Dtos {
    private Dtos() {
    }

    public record RegisterRequest(@NotBlank @Size(max = 120) String name, @NotBlank @Email @Size(max = 190) String email,
                                  @NotBlank @Size(min = 12, max = 72) String password) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank @Size(max = 200) String password) {
    }

    public record TokenResponse(String token) {
    }

    public record SessionRequest(@NotBlank @Size(max = 160) String title, @NotNull @Future Instant startsAt,
                                 @Min(1) @Max(1000) int capacity, @Min(50) @Max(1000000) int priceCents) {
    }

    public record SessionResponse(Long id, String title, Instant startsAt, int capacity, int priceCents, long availableSeats) {
    }

    public record BookingRequest(@NotNull Long sessionId) {
    }

    public record BookingResponse(Long id, Long sessionId, String sessionTitle, String status, Instant holdExpiresAt, String paymentUrl) {
    }
}
