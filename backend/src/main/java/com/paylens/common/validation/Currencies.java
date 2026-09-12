package com.paylens.common.validation;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class Currencies {

    private static final Set<String> SUPPORTED = Set.of(
            "USD", "EUR", "GBP", "INR", "SGD", "AUD", "CAD", "CHF", "JPY", "NZD"
    );

    private Currencies() {
    }

    public static Optional<String> normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String code = raw.trim().toUpperCase(Locale.ROOT);
        if (code.length() != 3 || !code.chars().allMatch(Character::isLetter)) {
            return Optional.empty();
        }
        if (!SUPPORTED.contains(code)) {
            return Optional.empty();
        }
        return Optional.of(code);
    }

    public static boolean isSupported(String code) {
        return SUPPORTED.contains(code);
    }
}
