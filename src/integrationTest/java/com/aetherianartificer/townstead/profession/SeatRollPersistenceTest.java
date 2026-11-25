package com.aetherianartificer.townstead.profession;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeatRollPersistenceTest {

    @Test
    void rollSurvivesASave() {
        SeatRoll roll = new SeatRoll();
        roll.enroll(new UUID(0, 1), ResourceLocation.tryParse("townstead:cook"), "pizzaiolo", 100);
        roll.enroll(new UUID(0, 2), ResourceLocation.tryParse("chefsdelight:chef"), null, 200);
        roll.markSeeded();

        SeatRoll loaded = SeatRoll.load(roll.save());

        assertEquals(roll.staff(), loaded.staff());
        assertTrue(loaded.seeded());
    }
}
