package com.campuslostfound.service.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Opaque secret generation and one-way hashing for verification tokens. */
public final class Tokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL64 = Base64.getUrlEncoder().withoutPadding();

    private Tokens() {
    }

    /** A 256-bit URL-safe random string. */
    public static String random() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return URL64.encodeToString(buf);
    }

    /** Lower-case hex SHA-256 of the input; what we persist instead of the raw token. */
    public static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
