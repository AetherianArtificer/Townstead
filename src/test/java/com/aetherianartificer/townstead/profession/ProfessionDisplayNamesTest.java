package com.aetherianartificer.townstead.profession;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfessionDisplayNamesTest {
    @Test
    void fallbackSeparatesPathWords() {
        assertEquals("Beverage Artisan", ProfessionDisplayNames.fallback("beverage_artisan"));
        assertEquals("Jam Maker", ProfessionDisplayNames.fallback("jam-maker"));
    }
}
