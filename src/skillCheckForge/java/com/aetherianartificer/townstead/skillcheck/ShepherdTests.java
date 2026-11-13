package com.aetherianartificer.townstead.skillcheck;

import com.aetherianartificer.townstead.pheno.combat.Wards;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Shepherd: Herder, Night Watch and Packmaster. */
@GameTestHolder(SkillCheck.ID)
@PrefixGameTestTemplate(false)
public final class ShepherdTests {
    private static final String H = "minecraft:shepherd/herder/";
    private static final String N = "minecraft:shepherd/night_watch/";
    private static final String P = "minecraft:shepherd/packmaster/";
    private static final ResourceLocation FLOCK = new ResourceLocation("townstead:flock");

    private static int flock(ServerPlayer p) {
        return SkillCheck.resource(p, FLOCK);
    }

    private static Wolf pet(GameTestHelper h, ServerPlayer owner, int x, int y, int z) {
        Wolf wolf = SkillCheck.dummy(h, EntityType.WOLF, x, y, z);
        wolf.tame(owner);
        return wolf;
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void roundUpGathersAndPenHoldsTheHerd(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, FLOCK, 0, H + "round_up", H + "pen");
        Cow cow = SkillCheck.dummy(h, EntityType.COW, 8, 1, 9);
        h.assertTrue(SkillCheck.press(p, H + "round_up"), "Round Up did not fire");
        h.assertTrue(cow.getDeltaMovement().z < -0.5, "The cow was not pulled in: " + cow.getDeltaMovement());
        h.assertTrue(cow.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Pen did not hold the cow");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void leadDrivesTheHerdAway(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, FLOCK, 30, H + "lead");
        Cow cow = SkillCheck.dummy(h, EntityType.COW, 8, 1, 6);
        h.assertTrue(SkillCheck.press(p, H + "lead"), "Lead did not fire");
        h.assertTrue(cow.getDeltaMovement().z > 0.8, "The cow was not driven off: " + cow.getDeltaMovement());
        h.assertTrue(flock(p) == 0, "Lead did not cost 30 Flock");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void crookSwingTripsTheAimedCreature(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, FLOCK, 0, H + "crook_swing");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 5);
        h.assertTrue(SkillCheck.press(p, H + "crook_swing"), "Crook Swing did not fire");
        h.assertTrue(husk.getDeltaMovement().horizontalDistanceSqr() > 0.5, "The creature was not dragged aside");
        h.assertTrue(husk.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "The creature did not fall");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void stampedeChargesThroughHostileCreatures(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, FLOCK, 30, H + "stampede");
        Cow cow = SkillCheck.dummy(h, EntityType.COW, 10, 1, 2);
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 6);
        float health = husk.getHealth();
        double start = p.getZ();
        h.assertTrue(SkillCheck.press(p, H + "stampede"), "Stampede did not fire");
        h.assertTrue(cow.hasEffect(MobEffects.MOVEMENT_SPEED), "The herd does not run with the charge");
        h.onEachTick(() -> SkillCheck.tick(p));
        h.runAfterDelay(25, () -> {
            h.assertTrue(p.getZ() - start > 5, "The charge covered " + (p.getZ() - start) + " blocks");
            h.assertTrue(husk.getHealth() < health, "The creature in the path took no damage");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void alarmShowsHostileCreatures(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, FLOCK, 0, N + "alarm");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 14, 1, 14);
        h.assertTrue(SkillCheck.press(p, N + "alarm"), "Alarm did not fire");
        h.assertTrue(husk.hasEffect(MobEffects.GLOWING), "The hostile creature does not glow");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void torchlightBlindsAndDazzleWeakens(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, FLOCK, 0, N + "torchlight", N + "dazzle");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 6);
        h.assertTrue(SkillCheck.press(p, N + "torchlight"), "Torchlight did not fire");
        h.assertTrue(husk.hasEffect(MobEffects.BLINDNESS), "Torchlight did not blind");
        h.assertTrue(husk.hasEffect(MobEffects.WEAKNESS), "Dazzle did not weaken");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void signalFlareLightsUpTheField(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, FLOCK, 30, N + "signal_flare");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 14, 1, 14);
        h.assertTrue(SkillCheck.press(p, N + "signal_flare"), "Signal Flare did not fire");
        h.assertTrue(husk.hasEffect(MobEffects.GLOWING), "The flare shows nothing");
        h.assertTrue(flock(p) == 0, "Signal Flare did not cost 30 Flock");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void lanternShowsWhatComesNearIt(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, FLOCK, 30, N + "lantern");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 6);
        h.assertTrue(SkillCheck.press(p, N + "lantern"), "Lantern did not fire");
        h.runAfterDelay(70, () -> {
            h.assertTrue(husk.hasEffect(MobEffects.GLOWING), "The lantern shows nothing near it");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void sicEmSendsPetsAtTheAimedCreature(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, FLOCK, 0, P + "sic_em");
        Wolf wolf = pet(h, p, 10, 1, 3);
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 6);
        h.assertTrue(SkillCheck.press(p, P + "sic_em"), "Sic 'Em did not fire");
        h.assertTrue(wolf.getTarget() == husk, "The pet does not attack the aimed creature");
        h.assertTrue(husk.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "The creature was not knocked down");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void guardDogWardsPets(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, FLOCK, 30, P + "guard_dog");
        h.assertFalse(SkillCheck.press(p, P + "guard_dog"), "Guard Dog fired with no pet");
        Wolf wolf = pet(h, p, 10, 1, 3);
        h.assertTrue(SkillCheck.press(p, P + "guard_dog"), "Guard Dog did not fire");
        h.assertTrue(Wards.remaining(wolf, "guard_dog") == 8f, "The pet got no 8 point ward");
        h.assertTrue(flock(p) == 0, "Guard Dog did not cost 30 Flock");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void heelCallsPets(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, FLOCK, 0, P + "heel");
        Wolf wolf = pet(h, p, 8, 1, 10);
        h.assertTrue(SkillCheck.press(p, P + "heel"), "Heel did not fire");
        h.assertTrue(wolf.getDeltaMovement().z < -0.8, "The pet was not called in: " + wolf.getDeltaMovement());
        h.succeed();
    }
}
