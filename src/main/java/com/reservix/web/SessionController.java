package com.reservix.web;

import com.reservix.domain.ClassSession;
import com.reservix.repository.ClassSessionRepository;
import com.reservix.service.BookingService;
import com.reservix.web.Dtos.*;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {
    private final ClassSessionRepository sessions;
    private final BookingService bookings;

    public SessionController(ClassSessionRepository sessions, BookingService bookings) {
        this.sessions = sessions;
        this.bookings = bookings;
    }

    @GetMapping
    public List<SessionResponse> upcoming() {
        return sessions.findByStartsAtAfterOrderByStartsAt(Instant.now()).stream().map(this::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse create(@Valid @RequestBody SessionRequest r) {
        return toResponse(sessions.save(new ClassSession(r.title(), r.startsAt(), r.capacity(), r.priceCents())));
    }

    private SessionResponse toResponse(ClassSession s) {
        return new SessionResponse(s.getId(), s.getTitle(), s.getStartsAt(), s.getCapacity(), s.getPriceCents(), bookings.availableSeats(s));
    }
}
