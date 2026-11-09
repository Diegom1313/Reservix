package com.reservix.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reservix.payment.WebhookService;
import com.reservix.payment.WebhookSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class WebhookController {
    private final WebhookService service;
    private final ObjectMapper mapper;
    private final String secret;

    public WebhookController(WebhookService service, ObjectMapper mapper, @Value("${reservix.payments.webhook-secret}") String secret) {
        this.service = service;
        this.mapper = mapper;
        this.secret = secret;
    }

    @PostMapping("/api/payments/webhook")
    public void receive(@RequestBody String payload, @RequestHeader(value = "Stripe-Signature", required = false) String signature)
            throws JsonProcessingException {
        if (!WebhookSignature.isValid(payload, signature, secret, System.currentTimeMillis() / 1000)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Firma no válida");
        }
        service.handle(mapper.readTree(payload));
    }
}
