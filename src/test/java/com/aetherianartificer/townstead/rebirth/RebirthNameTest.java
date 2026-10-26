package com.aetherianartificer.townstead.rebirth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RebirthNameTest {
    @Test void trimsAndCollapsesSpaces() {
        assertEquals("Elara Vane", Rebirth.cleanName("  Elara   Vane  "));
    }

    @Test void dropsFormattingCodes() {
        assertEquals("cElara", Rebirth.cleanName("§cElara"));
    }

    @Test void rejectsEmptyNames() {
        assertNull(Rebirth.cleanName("   "));
        assertNull(Rebirth.cleanName(null));
    }

    @Test void capsLength() {
        assertEquals(Rebirth.MAX_NAME_LENGTH, Rebirth.cleanName("a".repeat(80)).length());
    }
}
