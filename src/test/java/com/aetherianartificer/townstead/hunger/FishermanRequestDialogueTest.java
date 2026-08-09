package com.aetherianartificer.townstead.hunger;

import net.conczin.mca.entity.ai.relationship.Personality;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

// The legacy 1.20 MCA enum initializes against runtime-obfuscated RandomSource names that are not
// present in Forge's plain JUnit classpath. The old API is still covered by its production compile.
//? if neoforge {
class FishermanRequestDialogueTest {
    @Test
    void flavoredKeyUsesCompatibilityPersonalityName() {
        String key = FishermanRequestDialogue.pickKey(
                Personality.FLIRTY, "no_rod", RandomSource.create(42L));

        assertTrue(key.matches("dialogue\\.chat\\.fisherman_request\\.no_rod\\.flirty/[1-3]"));
    }
}
//?}
