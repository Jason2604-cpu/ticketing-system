package com.konohahelpdesk.util.generator;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Cryptographically random temporary passwords that always satisfy the default
 * password policy (upper, lower, digit, special). Ambiguous characters (0/O, 1/l/I)
 * are excluded so the value can be read out or typed without confusion.
 */
public final class PasswordGenerator {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGIT = "23456789";
    private static final String SPECIAL = "@#$%&*!?";
    private static final String ALL = UPPER + LOWER + DIGIT + SPECIAL;
    private static final SecureRandom RNG = new SecureRandom();

    private PasswordGenerator() {}

    public static String generate(int length) {
        if (length < 8) length = 8;
        List<Character> chars = new ArrayList<>(length);
        chars.add(pick(UPPER));
        chars.add(pick(LOWER));
        chars.add(pick(DIGIT));
        chars.add(pick(SPECIAL));
        while (chars.size() < length) {
            chars.add(pick(ALL));
        }
        Collections.shuffle(chars, RNG);
        StringBuilder sb = new StringBuilder(length);
        chars.forEach(sb::append);
        return sb.toString();
    }

    private static char pick(String alphabet) {
        return alphabet.charAt(RNG.nextInt(alphabet.length()));
    }
}
