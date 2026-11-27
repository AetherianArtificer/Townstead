package com.aetherianartificer.townstead.skillcheck;

import com.aetherianartificer.townstead.profession.career.CareerProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** The one-time respec of a career record saved under older skill data. */
@GameTestHolder(SkillCheck.ID)
@PrefixGameTestTemplate(false)
public final class RespecTests {

    private static CompoundTag oldSave() {
        CompoundTag tag = new CompoundTag();
        ListTag learned = new ListTag();
        learned.add(StringTag.valueOf("minecraft:armorer/iron_skin"));
        learned.add(StringTag.valueOf("minecraft:armorer/patch_up"));
        tag.put("learned", learned);
        return tag;
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void anOldRecordIsRespeccedOnce(GameTestHelper h) {
        CareerProfile profile = CareerProfile.fromTag(oldSave());
        h.assertFalse(profile.learnedChoices().isEmpty(), "The old save holds no skills");
        h.assertTrue(profile.respec(), "An old record was not respecced");
        h.assertTrue(profile.learnedChoices().isEmpty(), "Skills remain after the respec");
        h.assertTrue(profile.respecPending(), "The respec left no notice");
        h.assertFalse(profile.respec(), "The respec ran twice");
        CareerProfile again = CareerProfile.fromTag(profile.toTag());
        h.assertTrue(again.respecPending(), "The notice was not saved");
        again.learnChoice(new net.minecraft.resources.ResourceLocation("minecraft:armorer/iron_skin"));
        h.assertFalse(CareerProfile.fromTag(again.toTag()).respec(), "A record saved after the respec was reset again");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void aNewRecordIsLeftAlone(GameTestHelper h) {
        CareerProfile profile = CareerProfile.fromTag(new CompoundTag());
        h.assertFalse(profile.respec(), "A new record was respecced");
        h.succeed();
    }
}
