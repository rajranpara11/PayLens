package com.paylens.common.validation;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class CountryCodes {

    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("india", "IN"),
            Map.entry("united states", "US"),
            Map.entry("united states of america", "US"),
            Map.entry("usa", "US"),
            Map.entry("united kingdom", "GB"),
            Map.entry("great britain", "GB"),
            Map.entry("uk", "GB"),
            Map.entry("germany", "DE"),
            Map.entry("singapore", "SG"),
            Map.entry("australia", "AU"),
            Map.entry("canada", "CA"),
            Map.entry("netherlands", "NL")
    );

    private CountryCodes() {
    }

    public static Optional<String> toIsoCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String trimmed = raw.trim();
        if (trimmed.length() == 2 && trimmed.chars().allMatch(Character::isLetter)) {
            return Optional.of(trimmed.toUpperCase(Locale.ROOT));
        }
        return Optional.ofNullable(NAMES.get(trimmed.toLowerCase(Locale.ROOT)));
    }
}
