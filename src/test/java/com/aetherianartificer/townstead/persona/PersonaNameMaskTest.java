package com.aetherianartificer.townstead.persona;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PersonaNameMaskTest {

    @Test void fullNameInEitherOrderBecomesTheGivenName() {
        var mask = PersonaNameMask.of(Map.of("Judit", "Moldovan"));
        assertEquals("Judit put down a vampire.", mask.apply("Judit Moldovan put down a vampire."));
        assertEquals("Judit put down a vampire.", mask.apply("Moldovan Judit put down a vampire."));
        assertEquals("Judit and Judit", mask.apply("Judit Moldovan and Judit"));
    }

    @Test void otherNamesAndTheGivenNameAloneAreLeftAlone() {
        var mask = PersonaNameMask.of(Map.of("Judit", "Moldovan"));
        assertEquals("Judit", mask.apply("Judit"));
        assertEquals("Anna Moldova", mask.apply("Anna Moldova"));
        assertEquals("", mask.apply(""));
        assertNull(mask.apply(null));
    }

    @Test void nothingToHideChangesNothing() {
        assertEquals("Judit Moldovan", PersonaNameMask.of(Map.of()).apply("Judit Moldovan"));
        assertEquals("Judit Moldovan", PersonaNameMask.of(Map.of("Judit", " ")).apply("Judit Moldovan"));
    }

    @Test void paramsAreMaskedValueByValue() {
        var mask = PersonaNameMask.of(Map.of("Judit", "Moldovan"));
        assertEquals(Map.of("hunter", "Judit", "victim", "a vampire"),
                PersonaNameMask.params(Map.of("hunter", "Judit Moldovan", "victim", "a vampire"), mask));
    }
}
