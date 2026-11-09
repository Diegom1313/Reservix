package com.reservix;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reservix.domain.AppUser;
import com.reservix.domain.Role;
import com.reservix.payment.WebhookSignature;
import com.reservix.repository.UserRepository;
import com.reservix.security.AuthService;
import com.reservix.service.BookingService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestPropertySource(properties = {
        "reservix.jwt.secret=una-clave-de-pruebas-con-mas-de-32-bytes",
        "reservix.payments.fake=true",
        "reservix.payments.webhook-secret=whsec_prueba"
})
class ReservasTest {
    private static final String PASSWORD = "clave-de-pruebas-1";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    int port;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    UserRepository users;
    @Autowired
    PasswordEncoder encoder;
    @Autowired
    AuthService auth;
    @Autowired
    BookingService bookings;
    @Autowired
    JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    private record Res(int status, JsonNode body) {
    }

    private Res call(String method, String path, Object body, String token, Map<String, String> headers) throws Exception {
        String json = body == null ? "" : body instanceof String s ? s : mapper.writeValueAsString(body);
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        headers.forEach(builder::header);
        var res = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new Res(res.statusCode(), res.body().isBlank() ? null : mapper.readTree(res.body()));
    }

    private Res call(String method, String path, Object body, String token) throws Exception {
        return call(method, path, body, token, Map.of());
    }

    private String customer() throws Exception {
        String email = UUID.randomUUID() + "@t.test";
        return call("POST", "/api/auth/register", Map.of("name", "Cliente", "email", email, "password", PASSWORD), null).body().get("token").asText();
    }

    private String admin() {
        String email = UUID.randomUUID() + "@admin.test";
        users.save(new AppUser("Admin", email, encoder.encode(PASSWORD), Role.ADMIN));
        return auth.login(email, PASSWORD, "127.0.0.2");
    }

    private long session(int capacity) throws Exception {
        var body = Map.of("title", "Yoga", "startsAt", Instant.now().plus(2, ChronoUnit.DAYS).toString(), "capacity", capacity, "priceCents", 1200);
        Res res = call("POST", "/api/sessions", body, admin());
        assertEquals(201, res.status());
        return res.body().get("id").asLong();
    }

    private Res webhook(String id, long bookingId, String secret, String status) throws Exception {
        String payload = mapper.writeValueAsString(Map.of("id", id, "type", "checkout.session.completed",
                "data", Map.of("object", Map.of("payment_status", status, "payment_intent", "pi_" + bookingId, "metadata", Map.of("booking_id", Long.toString(bookingId))))));
        long t = System.currentTimeMillis() / 1000;
        String signature = "t=" + t + ",v1=" + WebhookSignature.sign(t + "." + payload, secret);
        return call("POST", "/api/payments/webhook", payload, null, Map.of("Stripe-Signature", signature));
    }

    private Res webhook(String id, long bookingId, String secret) throws Exception {
        return webhook(id, bookingId, secret, "paid");
    }

    private int events(long booking, String type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE booking_id = ? AND type = ?", Integer.class, booking, type);
    }

    @Test
    void bloqueaFuerzaBruta() throws Exception {
        String email = UUID.randomUUID() + "@t.test";
        call("POST", "/api/auth/register", Map.of("name", "A", "email", email, "password", PASSWORD), null);
        for (int i = 0; i < 5; i++) {
            assertEquals(401, call("POST", "/api/auth/login", Map.of("email", email, "password", "mala-clave-larga"), null).status());
        }
        assertEquals(429, call("POST", "/api/auth/login", Map.of("email", email, "password", PASSWORD), null).status());
    }

    @Test
    void unPagoIncompletoNoConfirma() throws Exception {
        String token = customer();
        long booking = call("POST", "/api/bookings", Map.of("sessionId", session(2)), token).body().get("id").asLong();
        assertEquals(200, webhook("evt_" + UUID.randomUUID(), booking, "whsec_prueba", "unpaid").status());
        assertEquals("PENDING_PAYMENT", call("GET", "/api/bookings/mine", null, token).body().get(0).get("status").asText());
    }

    @Test
    void otroEventoSobreUnaReservaPagadaNoReembolsa() throws Exception {
        long booking = call("POST", "/api/bookings", Map.of("sessionId", session(2)), customer()).body().get("id").asLong();
        webhook("evt_" + UUID.randomUUID(), booking, "whsec_prueba");
        webhook("evt_" + UUID.randomUUID(), booking, "whsec_prueba");
        assertEquals(0, events(booking, "REFUND_REQUIRED"));
    }

    @Test
    void unPagoTardioGeneraReembolso() throws Exception {
        long booking = call("POST", "/api/bookings", Map.of("sessionId", session(2)), customer()).body().get("id").asLong();
        jdbc.update("UPDATE bookings SET hold_expires_at = now() - interval '1 minute' WHERE id = ?", booking);
        bookings.expireHolds();
        webhook("evt_" + UUID.randomUUID(), booking, "whsec_prueba");
        assertEquals(1, events(booking, "REFUND_REQUIRED"));
        assertEquals(0, events(booking, "BOOKING_CONFIRMED"));
    }

    @Test
    void webhookDeReservaDesconocidaSeIgnora() throws Exception {
        assertEquals(200, webhook("evt_" + UUID.randomUUID(), 99999999L, "whsec_prueba").status());
    }

    @Test
    void permisos() throws Exception {
        assertEquals(401, call("GET", "/api/bookings/mine", null, null).status());
        assertEquals(401, call("GET", "/api/bookings/mine", null, "token.falso.x").status());
        var body = Map.of("title", "X", "startsAt", Instant.now().plus(1, ChronoUnit.DAYS).toString(), "capacity", 1, "priceCents", 100);
        assertEquals(403, call("POST", "/api/sessions", body, customer()).status());
        assertEquals(200, call("GET", "/api/sessions", null, null).status());
    }

    @Test
    void credenciales() throws Exception {
        String email = UUID.randomUUID() + "@t.test";
        assertEquals(201, call("POST", "/api/auth/register", Map.of("name", "A", "email", email, "password", PASSWORD), null).status());
        assertEquals(409, call("POST", "/api/auth/register", Map.of("name", "A", "email", email, "password", PASSWORD), null).status());
        assertEquals(400, call("POST", "/api/auth/register", Map.of("name", "A", "email", "b@t.test", "password", "corta"), null).status());
        assertEquals(401, call("POST", "/api/auth/login", Map.of("email", email, "password", "mala-clave-larga"), null).status());
        assertEquals(200, call("POST", "/api/auth/login", Map.of("email", email, "password", PASSWORD), null).status());
    }

    @Test
    void noSeVendeMasDeLaCapacidad() throws Exception {
        long session = session(5);
        int clientes = 40;
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < clientes; i++) {
            tokens.add(customer());
        }
        ExecutorService pool = Executors.newFixedThreadPool(clientes);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Integer>> futuros = new ArrayList<>();
        for (String token : tokens) {
            futuros.add(pool.submit(() -> {
                salida.await();
                return call("POST", "/api/bookings", Map.of("sessionId", session), token).status();
            }));
        }
        salida.countDown();
        int creadas = 0;
        int rechazadas = 0;
        for (var futuro : futuros) {
            int status = futuro.get(60, TimeUnit.SECONDS);
            if (status == 201) {
                creadas++;
            } else if (status == 409) {
                rechazadas++;
            }
        }
        pool.shutdown();
        assertEquals(5, creadas);
        assertEquals(35, rechazadas);
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM bookings WHERE session_id = ?", Integer.class, session));
    }

    @Test
    void unaReservaActivaPorPersona() throws Exception {
        long session = session(3);
        String token = customer();
        assertEquals(201, call("POST", "/api/bookings", Map.of("sessionId", session), token).status());
        assertEquals(409, call("POST", "/api/bookings", Map.of("sessionId", session), token).status());
    }

    @Test
    void webhookConfirmaUnaSolaVez() throws Exception {
        long session = session(3);
        String token = customer();
        long booking = call("POST", "/api/bookings", Map.of("sessionId", session), token).body().get("id").asLong();
        String event = "evt_" + UUID.randomUUID();

        assertEquals(400, webhook(event, booking, "whsec_mala").status());
        assertEquals(200, webhook(event, booking, "whsec_prueba").status());
        assertEquals(200, webhook(event, booking, "whsec_prueba").status());

        assertEquals("CONFIRMED", call("GET", "/api/bookings/mine", null, token).body().get(0).get("status").asText());
        assertEquals(1, events(booking, "BOOKING_CONFIRMED"));
    }

    @Test
    void cancelarLiberaPlazaYSoloElDueno() throws Exception {
        long session = session(1);
        String primero = customer();
        long booking = call("POST", "/api/bookings", Map.of("sessionId", session), primero).body().get("id").asLong();
        String segundo = customer();
        assertEquals(409, call("POST", "/api/bookings", Map.of("sessionId", session), segundo).status());
        assertEquals(404, call("DELETE", "/api/bookings/" + booking, null, segundo).status());
        assertEquals(204, call("DELETE", "/api/bookings/" + booking, null, primero).status());
        assertEquals(201, call("POST", "/api/bookings", Map.of("sessionId", session), segundo).status());
    }

    @Test
    void lasRetencionesCaducadasLiberanPlaza() throws Exception {
        long session = session(1);
        long booking = call("POST", "/api/bookings", Map.of("sessionId", session), customer()).body().get("id").asLong();
        jdbc.update("UPDATE bookings SET hold_expires_at = now() - interval '1 minute' WHERE id = ?", booking);
        bookings.expireHolds();
        assertEquals(201, call("POST", "/api/bookings", Map.of("sessionId", session), customer()).status());
    }
}
