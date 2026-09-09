package com.aetherianartificer.townstead.compat.mcacapitals;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CapitalsNameBridgeTest {
    private CompoundTag identity(String surname, String source) {
        CompoundTag tag = new CompoundTag();
        tag.putString("CurrentSurname", surname);
        tag.putString("SurnameSource", source);
        return tag;
    }

    @Test void generatedSurnameCanBeReplacedAndRepeatedPublishingIsStable() {
        var tag = identity("Fischer", "GENERATED");
        CapitalsNameBridge.apply(tag, "Eriksson");
        assertEquals("Eriksson", tag.getString("CurrentSurname"));
        assertFalse(CapitalsNameBridge.externalOwns(tag));
        CapitalsNameBridge.apply(tag, "Eriksson");
        assertEquals("Eriksson", tag.getString("CurrentSurname"));
    }

    @Test void decreeAfterCulturalAssignmentSurvivesRepublishing() {
        var tag = identity("Fischer", "BIRTH");
        CapitalsNameBridge.apply(tag, "Eriksson");
        tag.putString("CurrentSurname", "Oakheart");
        tag.putString("SurnameSource", "LEGAL_RENAME");
        assertTrue(CapitalsNameBridge.externalOwns(tag));
        CapitalsNameBridge.apply(tag, "Eriksson");
        CapitalsNameBridge.apply(tag, "OtherCulture");
        assertEquals("Oakheart", tag.getString("CurrentSurname"));
    }

    @Test void marriageWithSameSpellingStillTransfersOwnership() {
        var tag = identity("Fischer", "GENERATED");
        CapitalsNameBridge.apply(tag, "Smith");
        tag.putString("SurnameSource", "MARRIAGE");
        CapitalsNameBridge.apply(tag, "Jones");
        assertTrue(CapitalsNameBridge.externalOwns(tag));
        assertEquals("Smith", tag.getString("CurrentSurname"));
    }

    @Test void preexistingPlayerNamesAreNeverClaimed() {
        for (String source : new String[]{"LEGAL_RENAME", "MARRIAGE", "PLAYER_HOUSE", "ROYAL_HOUSE"}) {
            var tag = identity("Oakheart", source);
            CapitalsNameBridge.apply(tag, "Eriksson");
            assertTrue(CapitalsNameBridge.externalOwns(tag));
            assertEquals("Oakheart", tag.getString("CurrentSurname"));
        }
    }
}
