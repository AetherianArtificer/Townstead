package com.aetherianartificer.townstead.compat.thirst;

import com.aetherianartificer.townstead.needs.Consumables;
import com.aetherianartificer.townstead.needs.NeedEffectProjection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TanDrinkProjectionTest {
    private static final NeedEffectProjection TEA = new NeedEffectProjection(5, 7, 0);

    @Test void configuredTeaWithoutTanTagsGetsItsDrinkValues() {
        assertEquals(TEA, DataDrivenThirstCompat.tanProjection(new Consumables.ResolvedEffect(TEA, true), false));
    }

    @Test void fallbackPreservesNativeTanDrinkValues() {
        assertEquals(NeedEffectProjection.NONE,
                DataDrivenThirstCompat.tanProjection(new Consumables.ResolvedEffect(TEA, true), true));
    }

    @Test void explicitDefinitionOverridesNativeDrinkValues() {
        assertEquals(TEA, DataDrivenThirstCompat.tanProjection(new Consumables.ResolvedEffect(TEA, false), true));
    }

    @Test void removedDefinitionStopsContributingValues() {
        assertEquals(NeedEffectProjection.NONE, DataDrivenThirstCompat.tanProjection(null, false));
    }
}
