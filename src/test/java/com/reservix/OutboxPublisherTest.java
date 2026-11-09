package com.reservix;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.reservix.domain.OutboxEvent;
import com.reservix.payment.PaymentGateway;
import com.reservix.repository.BookingRepository;
import com.reservix.repository.OutboxRepository;
import com.reservix.service.Notifier;
import com.reservix.service.OutboxPublisher;
import java.util.List;
import org.junit.jupiter.api.Test;

class OutboxPublisherTest {
    @Test
    void publicaYMarca() {
        OutboxRepository repo = mock(OutboxRepository.class);
        Notifier notifier = mock(Notifier.class);
        OutboxEvent event = new OutboxEvent("BOOKING_CONFIRMED", 7L);
        when(repo.lockPending()).thenReturn(List.of(event));

        assertEquals(1, new OutboxPublisher(repo, notifier, mock(BookingRepository.class), mock(PaymentGateway.class)).publishPending());

        verify(notifier).send("BOOKING_CONFIRMED", 7L);
        assertNotNull(event.getPublishedAt());
    }

    @Test
    void siFallaElEnvioNoMarca() {
        OutboxRepository repo = mock(OutboxRepository.class);
        Notifier notifier = mock(Notifier.class);
        OutboxEvent event = new OutboxEvent("BOOKING_CONFIRMED", 7L);
        when(repo.lockPending()).thenReturn(List.of(event));
        doThrow(new RuntimeException("caído")).when(notifier).send(any(), any());

        assertEquals(0, new OutboxPublisher(repo, notifier, mock(BookingRepository.class), mock(PaymentGateway.class)).publishPending());
        assertEquals(1, event.getAttempts());
        assertNull(event.getPublishedAt());
    }
}
