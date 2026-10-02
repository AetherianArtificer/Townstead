package com.aetherianartificer.townstead.profession.career;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CareerStampTest {

    @Test
    void selectedCareerSealSurvivesRecordPersistence() {
        CareerStamp stamp = CareerStamp.sanitized(142, 24, 0.2f, "Crowbury", "5/2/1000",
                "townstead:textures/stamps/career/cook_guild.png", "Guild Seals", "Cook Guild");

        assertEquals(stamp, CareerStamp.fromTag(stamp.toTag()));
    }

    @Test
    void personalSealSurvivesRecordPersistence() {
        CareerStamp stamp = CareerStamp.sanitized(142, 24, 0f, "Crowbury", "5/2/1000",
                "", "", "A", "townstead:key", 3);

        assertEquals(stamp, CareerStamp.fromTag(stamp.toTag()));
        assertEquals(true, stamp.sealed());
    }

    @Test
    void anUnknownSealDeviceLeavesAnUnsealedMark() {
        CareerStamp stamp = CareerStamp.sanitized(142, 24, 0f, "Crowbury", "", "", "", "",
                "minecraft:textures/block/stone.png", 3);

        assertEquals(false, stamp.sealed());
    }

    @Test
    void decorativeCalendarArtCannotBeSmuggledIntoCareerRecords() {
        CareerStamp stamp = CareerStamp.sanitized(142, 24, 0f, "Crowbury", "",
                "townstead:textures/stamps/party_hat.png", "", "Party Hat");

        assertEquals("", stamp.textureId());
        assertEquals("Party Hat", stamp.label());
    }
}
