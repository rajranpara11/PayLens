package com.paylens.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CountryCodesTest {

    @Test
    void acceptsKnownCodesAndNames() {
        assertThat(CountryCodes.toIsoCode("in")).contains("IN");
        assertThat(CountryCodes.toIsoCode("India")).contains("IN");
        assertThat(CountryCodes.toIsoCode("US")).contains("US");
    }

    @Test
    void rejectsUnknownCodes() {
        assertThat(CountryCodes.toIsoCode("ZZ")).isEmpty();
        assertThat(CountryCodes.toIsoCode("XX")).isEmpty();
        assertThat(CountryCodes.toIsoCode("Atlantis")).isEmpty();
    }
}
