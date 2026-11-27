package com.aetherianartificer.townstead.root.attachment;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartWoundsTest {

    private static final AttachmentBody.Part SEVERABLE = part(true);
    private static final AttachmentBody.Part TOUGH = part(false);

    private static AttachmentBody.Part part(boolean severable) {
        return new AttachmentBody.Part(20f, 0.5f, severable, List.of(), 1f, 100, 1f, "", "", "", false, "", "", "", "", "", "", "", true, true, 300, null);
    }

    private static AttachmentBody.Part parsed(String json) {
        return AttachmentBody.parse(com.google.gson.JsonParser.parseString(json).getAsJsonObject()).part();
    }

    @org.junit.jupiter.api.Test
    void anAttachmentCanComeOffUnlessItSaysOtherwise() {
        AttachmentBody.Part unsaid = parsed("{}");
        org.junit.jupiter.api.Assertions.assertTrue(unsaid.severable());
        org.junit.jupiter.api.Assertions.assertFalse(unsaid.hasEntities());
        org.junit.jupiter.api.Assertions.assertEquals(AttachmentBody.Part.STUMP, unsaid.stumpTrace());
        org.junit.jupiter.api.Assertions.assertNull(parsed("{\"part\":false}"));
        AttachmentBody.Part declared = parsed("{\"part\":{\"health\":5}}");
        org.junit.jupiter.api.Assertions.assertTrue(declared.severable());
        org.junit.jupiter.api.Assertions.assertTrue(declared.hasEntities());
        org.junit.jupiter.api.Assertions.assertFalse(parsed("{\"part\":{\"severable\":false}}").severable());
    }

    private static PartWounds.State fresh() {
        return new PartWounds.State(20f, AttachmentBody.Part.INTACT);
    }

    @Test
    void woundedOnlyOnceThePoolFallsToTheLine() {
        assertEquals(AttachmentBody.Part.INTACT, PartWounds.hit(fresh(), SEVERABLE, 9f, true).stage());
        assertEquals(AttachmentBody.Part.WOUNDED, PartWounds.hit(fresh(), SEVERABLE, 11f, true).stage());
    }

    @Test
    void severingNeedsTheRightDamage() {
        assertEquals(AttachmentBody.Part.LOST, PartWounds.hit(fresh(), SEVERABLE, 25f, true).stage());
        PartWounds.State blunt = PartWounds.hit(fresh(), SEVERABLE, 25f, false);
        assertEquals(AttachmentBody.Part.WOUNDED, blunt.stage(), "blunt damage wounds but can't cut");
        assertTrue(blunt.health() > 0f);
    }

    @Test
    void aPartThatCannotBeSeveredStaysOn() {
        assertEquals(AttachmentBody.Part.WOUNDED, PartWounds.hit(fresh(), TOUGH, 100f, true).stage());
    }

    @Test
    void healingMendsPastTheLine() {
        PartWounds.State wounded = PartWounds.hit(fresh(), SEVERABLE, 12f, true);
        assertEquals(AttachmentBody.Part.WOUNDED, PartWounds.heal(wounded, SEVERABLE, 1f).stage());
        assertEquals(AttachmentBody.Part.INTACT, PartWounds.heal(wounded, SEVERABLE, 3f).stage());
    }

    @Test
    void aLostPartIsNotTouchedByHitsOrHealing() {
        PartWounds.State lost = new PartWounds.State(0f, AttachmentBody.Part.LOST);
        assertEquals(lost, PartWounds.hit(lost, SEVERABLE, 5f, true));
        assertEquals(lost, PartWounds.heal(lost, SEVERABLE, 5f));
    }

    @Test
    void aRegrownPartComesBackHurt() {
        PartWounds.State back = PartWounds.regrown(SEVERABLE);
        assertEquals(AttachmentBody.Part.WOUNDED, back.stage());
        assertEquals(10f, back.health(), 1e-4);
    }
}
