package com.reservix.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class LoginThrottle {
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private record Rule(String identifier, int limit) {
    }

    private final JdbcTemplate jdbc;

    public LoginThrottle(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private List<Rule> rules(String email, String ip) {
        return List.of(new Rule(hash("pair|" + email + "|" + ip), 5), new Rule(hash("ip|" + ip), 30), new Rule(hash("email|" + email), 20));
    }

    public void check(String email, String ip) {
        Timestamp since = Timestamp.from(Instant.now().minus(WINDOW));
        jdbc.update("DELETE FROM login_failures WHERE failed_at < ?", since);
        for (Rule rule : rules(email, ip)) {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM login_failures WHERE identifier = ? AND failed_at >= ?", Integer.class, rule.identifier(), since);
            if (count != null && count >= rule.limit()) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos");
            }
        }
    }

    public void recordFailure(String email, String ip) {
        Timestamp now = Timestamp.from(Instant.now());
        rules(email, ip).forEach(rule -> jdbc.update("INSERT INTO login_failures (identifier, failed_at) VALUES (?, ?)", rule.identifier(), now));
    }

    public void clear(String email, String ip) {
        jdbc.update("DELETE FROM login_failures WHERE identifier = ?", rules(email, ip).get(0).identifier());
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
