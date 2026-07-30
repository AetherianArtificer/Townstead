package com.aetherianartificer.townstead.compat.pizzadelight;

import com.aetherianartificer.townstead.work.OutputAppraisal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The pizza appraisal mirrors Pizza Delight's TasteHandler exactly: pointer = unique
 * ingredients minus the nine topping slots. Quality scales career XP, so these thresholds are
 * a compatibility contract, not tuning.
 */
class PizzaTasteTest {

    @Test
    void tasteTiersMatchPizzaDelight() {
        assertTier(9, 4, "delicious");
        assertTier(8, 3, "tasty");
        assertTier(6, 3, "tasty");
        assertTier(5, 2, "good");
        assertTier(3, 2, "good");
        assertTier(2, 1, "disgusting");
        assertTier(0, 1, "disgusting");
    }

    private static void assertTier(int unique, int quality, String label) {
        OutputAppraisal.Appraisal appraisal = PizzaDelightCompat.tasteFromUniqueness(unique);
        assertEquals(quality, appraisal.quality(), "uniqueness " + unique);
        assertEquals(label, appraisal.label(), "uniqueness " + unique);
    }
}
