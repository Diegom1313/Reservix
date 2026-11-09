package com.reservix.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProcessedWebhookRepository {
    private final JdbcTemplate jdbc;

    public ProcessedWebhookRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean markProcessed(String eventId) {
        return jdbc.update("INSERT INTO processed_webhooks (event_id, processed_at) VALUES (?, now()) ON CONFLICT DO NOTHING", eventId) == 1;
    }
}
