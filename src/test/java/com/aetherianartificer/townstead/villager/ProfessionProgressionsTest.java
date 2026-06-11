package com.aetherianartificer.townstead.villager;

import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.profession.def.ProgressionTrack;
import com.aetherianartificer.townstead.profession.def.RetrainingPolicy;
import com.aetherianartificer.townstead.profession.def.UnlockModel;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfessionProgressionsTest {

    private static final class MapStore implements ProfessionXpStore {
        final Map<String, ProfessionXp> m = new HashMap<>();
        @Override public ProfessionXp professionXp(String id) { return m.getOrDefault(id, ProfessionXp.EMPTY); }
        @Override public void setProfessionXp(String id, ProfessionXp v) { m.put(id, v); }
    }

    @BeforeEach
    void clearDefs() {
        ProfessionDefs.replaceAll(Map.of());
    }

    @AfterEach
    void clearDefsAfter() {
        ProfessionDefs.replaceAll(Map.of());
    }

    @Test
    void builtinFallbackMatchesEnumWhenNoDefRegistered() {
        ProgressionSpec spec = ProfessionProgressions.spec("farmer");
        assertArrayEquals(new int[]{0, 120, 320, 700, 1300}, spec.tierThresholds());
        assertEquals(240, spec.dailyXpCap());
        assertEquals(200000, spec.maxXp());
    }

    @Test
    void specMatchesBuiltinTierCurveAcrossRange() {
        ProgressionSpec spec = ProfessionProgressions.spec(ProfessionXpType.FARMER);
        for (int xp : new int[]{0, 119, 120, 319, 320, 700, 1299, 1300, 5000}) {
            assertEquals(ProfessionXpType.FARMER.tierForXp(xp), spec.tierForXp(xp), "xp=" + xp);
        }
    }

    @Test
    void datapackDefOverridesBuiltinById() {
        Map<ResourceLocation, ProfessionDef> defs = new HashMap<>();
        ResourceLocation id = ResourceLocation.tryParse("minecraft:farmer");
        defs.put(id, new ProfessionDef(id, null, null,
                new ProgressionTrack(List.of(0, 50), 10, 99),
                UnlockModel.EXPERIENTIAL, 1, RetrainingPolicy.FREE, List.of()));
        ProfessionDefs.replaceAll(defs);

        ProgressionSpec spec = ProfessionProgressions.spec("farmer");
        assertArrayEquals(new int[]{0, 50}, spec.tierThresholds());
        assertEquals(10, spec.dailyXpCap());
        assertEquals(99, spec.maxXp());
        assertEquals(2, spec.maxTier());
    }

    @Test
    void addXpRespectsDailyCapAndTiersUp() {
        MapStore store = new MapStore();
        // Farmer: daily cap 240, tier 2 at 120 xp. Request 1000 in one day -> only 240 applied.
        ProfessionProgress.GainResult r1 = ProfessionProgress.addXp(store, ProfessionXpType.FARMER, 1000, 0L);
        assertEquals(240, r1.appliedXp(), "daily cap clamps the gain");
        assertTrue(r1.tierUp(), "240 xp crosses the tier-2 threshold");
        assertEquals(2, r1.tierAfter());

        // Same day: no further gain.
        ProfessionProgress.GainResult r2 = ProfessionProgress.addXp(store, ProfessionXpType.FARMER, 100, 0L);
        assertEquals(0, r2.appliedXp());

        // Next day: cap resets.
        ProfessionProgress.GainResult r3 = ProfessionProgress.addXp(store, ProfessionXpType.FARMER, 100, 24000L);
        assertEquals(100, r3.appliedXp());
    }
}
