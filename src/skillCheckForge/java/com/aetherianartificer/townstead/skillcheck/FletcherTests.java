package com.aetherianartificer.townstead.skillcheck;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Fletcher: Marksman, Skirmisher and Trapper. */
@GameTestHolder(SkillCheck.ID)
@PrefixGameTestTemplate(false)
public final class FletcherTests {
    private static final String M = "minecraft:fletcher/marksman/";
    private static final String S = "minecraft:fletcher/skirmisher/";
    private static final String T = "minecraft:fletcher/trapper/";
    private static final ResourceLocation DRAW = new ResourceLocation("townstead:draw");

    private static ServerPlayer withBow(GameTestHelper h, int draw, String... skills) {
        ServerPlayer p = SkillCheck.ready(h, DRAW, draw, skills);
        p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        return p;
    }

    private static int arrows(GameTestHelper h, ServerPlayer p) {
        return h.getLevel().getEntitiesOfClass(AbstractArrow.class, p.getBoundingBox().inflate(24),
                arrow -> arrow.getOwner() == p).size();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void doubleShotFiresTwoArrows(GameTestHelper h) {
        ServerPlayer p = withBow(h, 0, M + "double_shot");
        h.assertTrue(SkillCheck.press(p, M + "double_shot"), "Double Shot did not fire");
        h.assertTrue(arrows(h, p) == 2, "Arrows in the air: " + arrows(h, p));
        h.assertTrue(SkillCheck.resource(p, SkillCheck.STAMINA) == 80, "Double Shot did not cost 20 stamina");
        h.succeed();
    }

    /** What stands near the line of fire, for a failed shot. */
    private static String scene(GameTestHelper h, ServerPlayer p, Zombie husk) {
        StringBuilder out = new StringBuilder(" eye " + p.getEyePosition() + " look " + p.getViewVector(1f)
                + " husk " + husk.position() + " box " + husk.getBoundingBox() + " alive " + husk.isAlive() + " near:");
        for (net.minecraft.world.entity.LivingEntity e : h.getLevel().getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class, p.getBoundingBox().inflate(34))) {
            out.append(' ').append(e.getType().toShortString()).append('@').append(e.blockPosition().toShortString());
        }
        return out.toString();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void headshotHitsHarderOnAGlowingCreature(GameTestHelper h) {
        ServerPlayer p = withBow(h, 80, M + "headshot");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 10);
        // A neighboring test's area hit can leave the husk in its invulnerable frames.
        husk.invulnerableTime = 0;
        float health = husk.getHealth();
        h.assertTrue(SkillCheck.press(p, M + "headshot"), "Headshot did not fire");
        float plain = health - husk.getHealth();
        h.assertTrue(plain > 9f && plain <= 10f, "Headshot dealt " + plain + scene(h, p, husk));
        h.assertTrue(SkillCheck.resource(p, DRAW) == 40, "Headshot did not cost 40 Draw");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void headshotOnAMarkedCreature(GameTestHelper h) {
        ServerPlayer p = withBow(h, 40, M + "headshot");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 10);
        husk.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200));
        float health = husk.getHealth();
        h.assertTrue(SkillCheck.press(p, M + "headshot"), "Headshot did not fire");
        float lit = health - husk.getHealth();
        h.assertTrue(lit > 14f && lit <= 15f, "Headshot dealt " + lit + " to a glowing creature, draw "
                + SkillCheck.resource(p, DRAW) + " glowing " + husk.hasEffect(MobEffects.GLOWING)
                + " invulnerable " + husk.invulnerableTime + scene(h, p, husk));
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void rangeFinderMarksTheCreatureYouLookAt(GameTestHelper h) {
        ServerPlayer p = withBow(h, 0, M + "range_finder");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 8);
        h.assertTrue(SkillCheck.press(p, M + "range_finder"), "Range Finder did not fire");
        h.assertTrue(husk.hasEffect(MobEffects.GLOWING), "The creature does not glow");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void vaultLeapsHighAndForward(GameTestHelper h) {
        ServerPlayer p = withBow(h, 0, S + "vault");
        h.assertTrue(SkillCheck.press(p, S + "vault"), "Vault did not fire");
        h.assertTrue(p.getDeltaMovement().y > 0.8 && p.getDeltaMovement().z > 1.0, "The leap was " + p.getDeltaMovement());
        h.assertTrue(p.hasEffect(MobEffects.SLOW_FALLING), "Vault gave no Slow Falling");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void tumbleDashesAndSavesTheFall(GameTestHelper h) {
        ServerPlayer p = withBow(h, 30, S + "tumble");
        double start = p.getZ();
        h.assertTrue(SkillCheck.press(p, S + "tumble"), "Tumble did not fire");
        h.assertTrue(SkillCheck.resource(p, DRAW) == 0, "Tumble did not cost 30 Draw");
        float fall = SkillCheck.struck(p, p.damageSources().fall(), 6);
        h.assertTrue(fall == 0f, "The fall after Tumble dealt " + fall);
        h.onEachTick(() -> SkillCheck.tick(p));
        h.runAfterDelay(15, () -> {
            h.assertTrue(p.getZ() - start > 3, "Tumble covered " + (p.getZ() - start) + " blocks");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void coveringFireRainsEightArrows(GameTestHelper h) {
        ServerPlayer p = withBow(h, 30, S + "covering_fire");
        h.assertTrue(SkillCheck.press(p, S + "covering_fire"), "Covering Fire did not fire");
        h.runAfterDelay(75, () -> {
            h.assertTrue(arrows(h, p) == 8, "Arrows fired over 3 seconds: " + arrows(h, p));
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void baitDrawsNearbyCreatures(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, DRAW, 0, T + "bait");
        Zombie husk = h.spawn(EntityType.HUSK, new BlockPos(8, 1, 8));
        h.assertTrue(SkillCheck.press(p, T + "bait"), "Bait did not fire");
        int lumps = h.getLevel().getEntitiesOfClass(ArmorStand.class, p.getBoundingBox().inflate(3)).size();
        h.assertTrue(lumps == 1, "Baits set: " + lumps);
        h.assertTrue(SkillCheck.flagged(husk, "townstead:trapper_baited"), "The creature did not take the bait");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void tripwireHoldsAndMarksWhatCrossesIt(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, DRAW, 30, T + "tripwire");
        h.assertTrue(SkillCheck.press(p, T + "tripwire"), "Tripwire did not fire");
        for (int x = 6; x <= 10; x++) {
            String block = BuiltInRegistries.BLOCK.getKey(h.getBlockState(new BlockPos(x, 1, 4)).getBlock()).toString();
            h.assertTrue(block.equals("townstead:haze"), "No wire at x " + x + ": " + block);
        }
        Zombie husk = h.spawn(EntityType.HUSK, new BlockPos(8, 1, 4));
        h.runAfterDelay(50, () -> {
            h.assertTrue(SkillCheck.flagged(husk, "townstead:trapper_tripped") || husk.hasEffect(MobEffects.GLOWING),
                    "The creature on the wire was not caught");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void ambushDoublesTheNextHit(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, DRAW, 0, T + "ambush");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 4);
        float plain = SkillCheck.struck(husk, husk.damageSources().playerAttack(p), 4);
        h.assertTrue(Math.abs(plain - 4f) < 0.01f, "A plain hit dealt " + plain);
        h.assertTrue(SkillCheck.press(p, T + "ambush"), "Ambush did not fire");
        float readied = SkillCheck.struck(husk, husk.damageSources().playerAttack(p), 4);
        h.assertTrue(Math.abs(readied - 8f) < 0.01f, "An ambush hit dealt " + readied);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void deadfallDropsOneSecondLater(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, DRAW, 30, T + "deadfall");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 8);
        float health = husk.getHealth();
        h.assertTrue(SkillCheck.press(p, T + "deadfall"), "Deadfall did not fire");
        h.runAfterDelay(10, () -> h.assertTrue(husk.getHealth() == health, "The log fell at once"));
        h.runAfterDelay(35, () -> {
            float dealt = health - husk.getHealth();
            h.assertTrue(dealt > 6.5f && dealt <= 8f, "Deadfall dealt " + dealt);
            h.succeed();
        });
    }
}
