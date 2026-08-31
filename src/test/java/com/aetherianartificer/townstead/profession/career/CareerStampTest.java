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
    void decorativeCalendarArtCannotBeSmuggledIntoCareerRecords() {
        CareerStamp stamp = CareerStamp.sanitized(142, 24, 0f, "Crowbury", "",
                "townstead:textures/stamps/party_hat.png", "", "Party Hat");

        assertEquals("", stamp.textureId());
        assertEquals("Party Hat", stamp.label());
    }
}
