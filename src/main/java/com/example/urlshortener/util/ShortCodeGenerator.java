package com.example.urlshortener.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates random, URL-safe Base62 short codes.
 *
 * Base62 (0-9, a-z, A-Z) is used instead of Base64 because it avoids
 * characters like '+', '/', '=' that need escaping in URLs.
 *
 * 7 characters of Base62 gives 62^7 (~3.5 trillion) possible codes,
 * which keeps collision probability extremely low even at large scale,
 * while collisions that do occur are still handled explicitly by the
 * service layer via a retry loop.
 */
@Component
public class ShortCodeGenerator {

    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int DEFAULT_LENGTH = 7;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        return generate(DEFAULT_LENGTH);
    }

    public String generate(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
