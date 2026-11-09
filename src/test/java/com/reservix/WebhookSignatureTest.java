package com.reservix;

import static org.junit.jupiter.api.Assertions.*;

import com.reservix.payment.WebhookSignature;
import org.junit.jupiter.api.Test;

class WebhookSignatureTest {
    private static final String SECRET = "whsec_prueba";
    private static final long NOW = 1_800_000_000L;

    private String header(long time, String payload) {
        return "t=" + time + ",v1=" + WebhookSignature.sign(time + "." + payload, SECRET);
    }

    @Test
    void aceptaFirmaCorrecta() {
        assertTrue(WebhookSignature.isValid("{}", header(NOW, "{}"), SECRET, NOW));
    }

    @Test
    void rechazaCuerpoAlterado() {
        assertFalse(WebhookSignature.isValid("{\"a\":1}", header(NOW, "{}"), SECRET, NOW));
    }

    @Test
    void rechazaFirmaAntigua() {
        assertFalse(WebhookSignature.isValid("{}", header(NOW - 301, "{}"), SECRET, NOW));
    }

    @Test
    void rechazaCabeceraMalformada() {
        assertFalse(WebhookSignature.isValid("{}", "basura", SECRET, NOW));
        assertFalse(WebhookSignature.isValid("{}", null, SECRET, NOW));
        assertFalse(WebhookSignature.isValid("{}", "t=abc,v1=x", SECRET, NOW));
    }
}
