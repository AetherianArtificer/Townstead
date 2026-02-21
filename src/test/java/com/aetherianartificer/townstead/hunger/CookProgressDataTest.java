package com.aetherianartificer.townstead.hunger;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CookProgressDataTest {

    @Test
    void tierThresholds() {
        CompoundTag tag = new CompoundTag();
        assertEquals(1, CookProgressData.getTier(tag));

        tag.putInt("cookXp", 109);
        tag.putInt("cookTier", 0); // force recalc
        assertEquals(1, CookProgressData.getTier(tag));

        tag.putInt("cookXp", 110);
        tag.putInt("cookTier", 0);
        assertEquals(2, CookProgressData.getTier(tag));

        tag.putInt("cookXp", 300);
        tag.putInt("cookTier", 0);
        assertEquals(3, CookProgressData.getTier(tag));

        tag.putInt("cookXp", 660);
        tag.putInt("cookTier", 0);
        assertEquals(4, CookProgressData.getTier(tag));

        tag.putInt("cookXp", 1250);
        tag.putInt("cookTier", 0);
        assertEquals(5, CookProgressData.getTier(tag));
    }

    @Test
    void dailyXpCap() {
        CompoundTag tag = new CompoundTag();
        long gameTime = 24000L; // day 1

        CookProgressData.GainResult r1 = CookProgressData.addXp(tag, 230, gameTime);
        assertEquals(230, r1.appliedXp());

        CookProgressData.GainResult r2 = CookProgressData.addXp(tag, 10, gameTime);
        assertEquals(0, r2.appliedXp());
    }

    @Test
    void dayRolloverResetsDailyCounter() {
        CompoundTag tag = new CompoundTag();
        long day1 = 24000L;

        CookProgressData.addXp(tag, 230, day1);
        CookProgressData.GainResult capped = CookProgressData.addXp(tag, 10, day1);
        assertEquals(0, capped.appliedXp());

        long day2 = 48000L;
        CookProgressData.GainResult afterReset = CookProgressData.addXp(tag, 10, day2);
        assertEquals(10, afterReset.appliedXp());
    }

    @Test
    void getXpToNextTier() {
        CompoundTag tag = new CompoundTag();
        // tier 1, xp 0 -> next at 110
        assertEquals(110, CookProgressData.getXpToNextTier(tag));

        tag.putInt("cookXp", 50);
        tag.putInt("cookTier", 0);
        assertEquals(60, CookProgressData.getXpToNextTier(tag));

        tag.putInt("cookXp", 1250);
        tag.putInt("cookTier", 0);
        assertEquals(0, CookProgressData.getXpToNextTier(tag)); // tier 5
    }

    @Test
    void addXpReturnsTierUpFlag() {
        CompoundTag tag = new CompoundTag();
        long gameTime = 24000L;

        CookProgressData.GainResult r = CookProgressData.addXp(tag, 110, gameTime);
        assertTrue(r.tierUp());
        assertEquals(1, r.tierBefore());
        assertEquals(2, r.tierAfter());
    }

    @Test
    void addXpNoTierUp() {
        CompoundTag tag = new CompoundTag();
        long gameTime = 24000L;

        CookProgressData.GainResult r = CookProgressData.addXp(tag, 5, gameTime);
        assertFalse(r.tierUp());
        assertEquals(1, r.tierBefore());
        assertEquals(1, r.tierAfter());
    }

    @Test
    void maxXpCap() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("cookXp", 199990);
        tag.putInt("cookTier", 5);

        CookProgressData.GainResult r = CookProgressData.addXp(tag, 100, 24000L);
        // appliedXp reflects what passed the daily cap, not the MAX_XP clamp
        assertEquals(100, r.appliedXp());
        assertEquals(200000, CookProgressData.getXp(tag));
    }
}
