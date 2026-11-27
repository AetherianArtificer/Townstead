package com.aetherianartificer.townstead.skillcheck;

import com.aetherianartificer.townstead.pheno.combat.Wards;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Leatherworker, Librarian and Climatologist: one played tool for each career. */
@GameTestHolder(SkillCheck.ID)
@PrefixGameTestTemplate(false)
public final class LateCareerTests {
    private static final String OUTFITTER = "minecraft:leatherworker/outfitter/";
    private static final String HUSH = "minecraft:librarian/hush/";
    private static final String FROST = "townstead:climatologist/frostkeeper/";
    private static final String STORM = "townstead:climatologist/stormcaller/";
    private static final ResourceLocation HIDE = ResourceLocation.parse("townstead:hide");
    private static final ResourceLocation FOCUS = ResourceLocation.parse("townstead:focus");
    private static final ResourceLocation FRONT = ResourceLocation.parse("townstead:front");

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void emergencyKitClearsAndWardsTheAimedAlly(GameTestHelper h) {
        if (SkillCheck.missing(h, OUTFITTER + "emergency_kit")) return;
        ServerPlayer p = SkillCheck.ready(h, HIDE, 30, OUTFITTER + "emergency_kit");
        Cow ally = SkillCheck.dummy(h, EntityType.COW, 8, 1, 8);
        ally.addEffect(new MobEffectInstance(MobEffects.POISON, 400));
        h.assertTrue(SkillCheck.press(p, OUTFITTER + "emergency_kit"), "Emergency Kit did not fire");
        h.assertFalse(ally.hasEffect(MobEffects.POISON), "The ally is still poisoned");
        h.assertTrue(Wards.remaining(ally, "emergency_kit") == 6f, "The ally got no 6 point ward");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void holdBreathHidesTheCasterForATime(GameTestHelper h) {
        if (SkillCheck.missing(h, HUSH + "hold_breath")) return;
        ServerPlayer p = SkillCheck.ready(h, FOCUS, 30, HUSH + "hold_breath");
        h.assertTrue(SkillCheck.press(p, HUSH + "hold_breath"), "Hold Breath did not fire");
        h.assertTrue(SkillCheck.flagged(p, "townstead:hush_breath"), "No breath is held");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void iceBridgeStartsFreezingWater(GameTestHelper h) {
        if (SkillCheck.missing(h, FROST + "ice_bridge")) return;
        ServerPlayer p = SkillCheck.ready(h, FRONT, 0, FROST + "ice_bridge");
        h.assertTrue(SkillCheck.press(p, FROST + "ice_bridge"), "Ice Bridge did not fire");
        h.assertTrue(SkillCheck.flagged(p, "townstead:frostkeeper_bridge"), "No bridge is forming");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void gustWardsAgainstProjectiles(GameTestHelper h) {
        if (SkillCheck.missing(h, STORM + "gust")) return;
        ServerPlayer p = SkillCheck.ready(h, FRONT, 0, STORM + "gust");
        h.assertTrue(SkillCheck.press(p, STORM + "gust"), "Gust did not fire");
        h.assertTrue(Wards.remaining(p, "gust") == 100f, "Gust raised no ward");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void toolBeltPutsTheRightToolInHand(GameTestHelper h) {
        String skill = "minecraft:toolsmith/tool_belt";
        if (SkillCheck.missing(h, skill)) return;
        ServerPlayer p = SkillCheck.ready(h, HIDE, 0, skill);
        h.setBlock(new net.minecraft.core.BlockPos(8, 2, 5), net.minecraft.world.level.block.Blocks.STONE);
        p.getInventory().selected = 0;
        p.getInventory().items.set(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK));
        p.getInventory().items.set(5, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
        h.assertTrue(SkillCheck.press(p, skill), "Tool Belt did not fire");
        h.assertTrue(p.getMainHandItem().is(net.minecraft.world.item.Items.IRON_PICKAXE), "The pickaxe is not in hand");
        h.assertTrue(p.getInventory().items.get(5).is(net.minecraft.world.item.Items.STICK), "The stick is not in the pack");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void slingStrikesTheAimedCreature(GameTestHelper h) {
        String skill = "minecraft:leatherworker/sling";
        if (SkillCheck.missing(h, skill)) return;
        ServerPlayer p = SkillCheck.ready(h, HIDE, 0, skill);
        net.minecraft.world.entity.monster.Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 10);
        husk.invulnerableTime = 0;
        float health = husk.getHealth();
        h.assertTrue(SkillCheck.press(p, skill), "Sling did not fire");
        float dealt = health - husk.getHealth();
        h.assertTrue(dealt > 1.5f && dealt <= 2f, "Sling dealt " + dealt);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void fieldHandWetsFarmland(GameTestHelper h) {
        String skill = "minecraft:farmer/field_hand";
        if (SkillCheck.missing(h, skill)) return;
        ServerPlayer p = SkillCheck.ready(h, HIDE, 0, skill);
        net.minecraft.core.BlockPos at = new net.minecraft.core.BlockPos(9, 1, 3);
        h.setBlock(at, net.minecraft.world.level.block.Blocks.FARMLAND);
        h.assertTrue(SkillCheck.press(p, skill), "Field Hand did not fire");
        h.assertTrue(h.getBlockState(at).getValue(net.minecraft.world.level.block.FarmBlock.MOISTURE) == 7,
                "The farmland is dry");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void brewSenseReadsTheAimedCreature(GameTestHelper h) {
        String skill = "townstead:witch/brew_sense";
        if (SkillCheck.missing(h, skill)) return;
        ServerPlayer p = SkillCheck.ready(h, HIDE, 0, skill);
        SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 8).addEffect(new MobEffectInstance(MobEffects.POISON, 400));
        h.assertTrue(SkillCheck.press(p, skill), "Brew Sense did not fire");
        h.succeed();
    }
}
