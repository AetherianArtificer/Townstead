package com.aetherianartificer.townstead.skillcheck;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Cartographer: Pathfinder, Prospector and Surveyor. */
@GameTestHolder(SkillCheck.ID)
@PrefixGameTestTemplate(false)
public final class CartographerTests {
    private static final String F = "minecraft:cartographer/pathfinder/";
    private static final String O = "minecraft:cartographer/prospector/";
    private static final String S = "minecraft:cartographer/surveyor/";
    private static final ResourceLocation BEARING = ResourceLocation.parse("townstead:bearing");

    private static int bearing(ServerPlayer p) {
        return SkillCheck.resource(p, BEARING);
    }

    @GameTest(template = "empty", timeoutTicks = 500)
    public static void waypointIsSetWhileSneakingAndReturnsYou(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, BEARING, 50, F + "waypoint");
        double homeX = p.getX();
        double homeZ = p.getZ();
        p.setShiftKeyDown(true);
        h.assertTrue(SkillCheck.press(p, F + "waypoint"), "Waypoint did not set");
        h.assertTrue(bearing(p) == 50, "Setting a waypoint cost Bearing");
        p.setShiftKeyDown(false);
        p.setPos(homeX + 5, p.getY(), homeZ + 9);
        h.runAfterDelay(410, () -> {
            h.assertTrue(SkillCheck.press(p, F + "waypoint"), "Waypoint did not return");
            h.assertTrue(Math.abs(p.getX() - homeX) < 1.5 && Math.abs(p.getZ() - homeZ) < 1.5,
                    "The caster is not back at the waypoint: " + p.position());
            h.assertTrue(bearing(p) == 25, "A return did not cost 25 Bearing: " + bearing(p));
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void leadTheWaySpeedsAllies(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, BEARING, 30, F + "lead_the_way");
        Cow ally = SkillCheck.dummy(h, EntityType.COW, 8, 1, 6);
        h.assertTrue(SkillCheck.press(p, F + "lead_the_way"), "Lead the Way did not fire");
        var speed = ally.getEffect(MobEffects.MOVEMENT_SPEED);
        h.assertTrue(speed != null && speed.getAmplifier() == 1, "The ally has no Speed II");
        h.assertTrue(bearing(p) == 0, "Lead the Way did not cost 30 Bearing");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void trailheadBlinksBackward(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, BEARING, 30, F + "trailhead");
        p.setPos(p.getX(), p.getY(), p.getZ() + 10);
        double start = p.getZ();
        h.assertTrue(SkillCheck.press(p, F + "trailhead"), "Trailhead did not fire");
        h.assertTrue(start - p.getZ() > 6, "The caster moved back " + (start - p.getZ()) + " blocks");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void coreSampleNeedsOreBelow(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, BEARING, 30, O + "core_sample");
        h.assertFalse(SkillCheck.press(p, O + "core_sample"), "Core Sample fired with no ore below");
        h.assertTrue(bearing(p) == 30, "A refused Core Sample cost Bearing");
        h.setBlock(8, 0, 2, Blocks.IRON_ORE);
        h.assertTrue(SkillCheck.press(p, O + "core_sample"), "Core Sample did not fire over ore");
        h.assertTrue(bearing(p) == 0, "Core Sample did not cost 30 Bearing");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void flareBlindsAndPickHammerHitsHarderOnStone(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, BEARING, 30, O + "flare", O + "pick_hammer");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 5);
        float health = husk.getHealth();
        h.assertTrue(SkillCheck.press(p, O + "pick_hammer"), "Pick Hammer did not fire");
        float dealt = health - husk.getHealth();
        h.assertTrue(dealt > 9f && dealt <= 10f, "Pick Hammer on stone dealt " + dealt);
        SkillCheck.learn(p, O + "flare");
        h.assertTrue(SkillCheck.press(p, O + "flare"), "Flare did not fire");
        h.assertTrue(husk.hasEffect(MobEffects.BLINDNESS), "Flare did not blind");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void updraftLiftsAndRockfallHitsHarderFromTheAir(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, BEARING, 0, S + "updraft", S + "rockfall");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 5);
        float health = husk.getHealth();
        h.assertTrue(SkillCheck.press(p, S + "updraft"), "Updraft did not fire");
        h.assertTrue(p.getDeltaMovement().y > 0.9, "Updraft lifted at " + p.getDeltaMovement().y);
        h.assertTrue(p.hasEffect(MobEffects.SLOW_FALLING), "Updraft gave no Slow Falling");
        p.setOnGround(false);
        h.assertTrue(SkillCheck.press(p, S + "rockfall"), "Rockfall did not fire");
        float dealt = health - husk.getHealth();
        h.assertTrue(dealt > 5f && dealt <= 6f, "Rockfall from the air dealt " + dealt);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void diveNeedsTheAirAndStrikes(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, BEARING, 30, S + "dive");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 6);
        float health = husk.getHealth();
        h.assertFalse(SkillCheck.press(p, S + "dive"), "Dive fired from the ground");
        p.setOnGround(false);
        h.assertTrue(SkillCheck.press(p, S + "dive"), "Dive did not fire in the air");
        float fall = SkillCheck.struck(p, p.damageSources().fall(), 8);
        h.assertTrue(fall == 0f, "The fall after Dive dealt " + fall);
        h.runAfterDelay(15, () -> {
            float dealt = health - husk.getHealth();
            h.assertTrue(dealt > 5f && dealt <= 6f, "Dive dealt " + dealt);
            h.succeed();
        });
    }
}
