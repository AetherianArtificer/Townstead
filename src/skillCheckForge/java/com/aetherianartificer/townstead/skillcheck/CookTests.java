package com.aetherianartificer.townstead.skillcheck;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Cook: Chef and Pizzaiolo. Only the tools that need no item from another mod are played here. */
@GameTestHolder(SkillCheck.ID)
@PrefixGameTestTemplate(false)
public final class CookTests {
    private static final String C = "townstead:cook/chef/";
    private static final String Z = "townstead:cook/pizzaiolo/";
    private static final ResourceLocation CHAR = new ResourceLocation("townstead:char");
    private static final ResourceLocation FLOW = new ResourceLocation("townstead:flow");

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void passHandsTheHeldItemToTheAimedAlly(GameTestHelper h) {
        if (SkillCheck.missing(h, C + "pass")) return;
        ServerPlayer p = SkillCheck.ready(h, FLOW, 0, C + "pass", C + "hands");
        Cow ally = SkillCheck.dummy(h, EntityType.COW, 8, 1, 5);
        h.assertFalse(SkillCheck.press(p, C + "pass"), "Pass fired with an empty hand");
        p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BREAD, 3));
        h.assertTrue(SkillCheck.press(p, C + "pass"), "Pass did not fire");
        h.assertTrue(p.getMainHandItem().isEmpty(), "The caster still holds the item");
        h.assertTrue(ally.getMainHandItem().is(Items.BREAD) && ally.getMainHandItem().getCount() == 3,
                "The ally did not catch the item");
        h.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SPEED), "Hands gave no Speed");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void peelVaultThrowsTheCasterAndHighBarSavesTheFall(GameTestHelper h) {
        if (SkillCheck.missing(h, Z + "peel_vault")) return;
        ServerPlayer p = SkillCheck.ready(h, CHAR, 0, Z + "peel_vault", Z + "high_bar");
        h.assertTrue(SkillCheck.press(p, Z + "peel_vault"), "Peel Vault did not fire");
        h.assertTrue(p.getDeltaMovement().y > 0.8 && p.getDeltaMovement().z > 0.5, "The vault was " + p.getDeltaMovement());
        float fall = SkillCheck.struck(p, p.damageSources().fall(), 6);
        h.assertTrue(fall == 0f, "The fall after a High Bar vault dealt " + fall);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void peelSlamStrikesKnocksBackAndBuildsChar(GameTestHelper h) {
        if (SkillCheck.missing(h, Z + "peel_slam")) return;
        ServerPlayer p = SkillCheck.ready(h, CHAR, 0, Z + "peel_slam");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 4);
        float health = husk.getHealth();
        h.assertTrue(SkillCheck.press(p, Z + "peel_slam"), "Peel Slam did not fire");
        h.assertTrue(health - husk.getHealth() > 5f && health - husk.getHealth() <= 6f, "Peel Slam dealt " + (health - husk.getHealth()));
        h.assertTrue(husk.getDeltaMovement().horizontalDistanceSqr() > 0.5, "Peel Slam did not knock the creature back");
        h.assertTrue(SkillCheck.resource(p, CHAR) == 5, "A peel hit gave " + SkillCheck.resource(p, CHAR) + " Char");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void hotStoneBurnsWhatTouchesIt(GameTestHelper h) {
        if (SkillCheck.missing(h, Z + "hot_stone")) return;
        ServerPlayer p = SkillCheck.ready(h, CHAR, 30, Z + "hot_stone");
        p.setXRot(60f);
        Zombie husk = h.spawn(EntityType.HUSK, new BlockPos(8, 1, 3));
        h.assertTrue(SkillCheck.press(p, Z + "hot_stone"), "Hot Stone did not fire");
        h.assertTrue(SkillCheck.resource(p, CHAR) == 0, "Hot Stone did not cost 30 Char");
        String block = BuiltInRegistries.BLOCK.getKey(h.getBlockState(new BlockPos(8, 1, 3)).getBlock()).toString();
        h.assertTrue(block.equals("townstead:haze"), "No stone on the ground where the caster aims: " + block);
        h.runAfterDelay(40, () -> {
            h.assertTrue(husk.isOnFire(), "The creature on the stone does not burn");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void intoTheOvenPunishesABurningCreature(GameTestHelper h) {
        if (SkillCheck.missing(h, Z + "peel_slam")) return;
        ServerPlayer p = SkillCheck.ready(h, CHAR, 0, Z + "peel_slam", Z + "into_the_oven");
        Zombie burning = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 4);
        burning.setRemainingFireTicks(200);
        float health = burning.getHealth();
        h.assertTrue(SkillCheck.press(p, Z + "peel_slam"), "Peel Slam did not fire");
        h.assertTrue(burning.getHealth() == health, "Peel Slam dealt damage with Into the Oven");
        h.runAfterDelay(30, () -> {
            h.assertTrue(health - burning.getHealth() >= 6f, "The burning creature lost only " + (health - burning.getHealth()));
            h.succeed();
        });
    }
}
