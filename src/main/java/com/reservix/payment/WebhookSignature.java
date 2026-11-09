package com.reservix.payment;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class WebhookSignature {
    private static final long TOLERANCE_SECONDS = 300;

    private WebhookSignature() {
    }

    public static boolean isValid(String payload, String header, String secret, long nowSeconds) {
        if (header == null || secret == null || secret.isBlank()) {
            return false;
        }
        String timestamp = null;
        String signature = null;
        for (String part : header.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2 && pair[0].equals("t")) {
                timestamp = pair[1];
            } else if (pair.length == 2 && pair[0].equals("v1") && signature == null) {
                signature = pair[1];
            }
        }
        if (timestamp == null || signature == null) {
            return false;
        }
        try {
            if (Math.abs(nowSeconds - Long.parseLong(timestamp)) > TOLERANCE_SECONDS) {
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
        byte[] expected = sign(timestamp + "." + payload, secret).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8));
    }

    public static String sign(String content, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
