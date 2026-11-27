package com.aetherianartificer.townstead.skillcheck;

import com.aetherianartificer.townstead.pheno.state.TimedFlags;
import com.aetherianartificer.townstead.root.Buoyancy;
import com.aetherianartificer.townstead.root.ability.AbilityToggles;
import com.aetherianartificer.townstead.root.ability.ResourceValues;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Armorer, Heavyweight path: each tool does what its text says, charges its cost, and respects its limits. */
@GameTestHolder(SkillCheck.ID)
@PrefixGameTestTemplate(false)
public final class ArmorerHeavyweightTests {
    private static final String P = "minecraft:armorer/heavyweight/";
    private static final ResourceLocation TEMPER = ResourceLocation.parse("townstead:temper");
    private static final ResourceLocation STANCE = ResourceLocation.parse(P + "plate_stance");
    private static final ResourceLocation TREAD = ResourceLocation.parse(P + "iron_tread");
    private static final ResourceLocation PLUNGING = ResourceLocation.parse("townstead:plunging");

    /** Facing +z from (8,1,2), with full stamina. */
    private static ServerPlayer caster(GameTestHelper h, String... skills) {
        SkillCheck.floor(h);
        ServerPlayer p = SkillCheck.caster(h, 8.5, 1, 2.5, 0);
        SkillCheck.learn(p, skills);
        ResourceValues.set(p, SkillCheck.STAMINA, 100);
        ResourceValues.set(p, TEMPER, 0);
        return p;
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void shoulderBargeDashesShovesAndCharges(GameTestHelper h) {
        ServerPlayer p = caster(h, P + "shoulder_barge");
        Zombie zombie = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 5);
        double startZ = p.getZ();
        float health = zombie.getHealth();
        h.assertTrue(SkillCheck.press(p, P + "shoulder_barge"), "Shoulder Barge did not fire");
        h.assertTrue(SkillCheck.resource(p, SkillCheck.STAMINA) == 80, "Stamina after the barge: "
                + SkillCheck.resource(p, SkillCheck.STAMINA));
        h.assertFalse(SkillCheck.press(p, P + "shoulder_barge"), "Shoulder Barge ignored its cooldown");
        h.onEachTick(() -> SkillCheck.tick(p));
        h.runAfterDelay(20, () -> {
            h.assertTrue(p.getZ() - startZ > 3, "The dash covered only " + (p.getZ() - startZ) + " blocks");
            h.assertTrue(zombie.getHealth() == health, "Shoulder Barge dealt damage without Trample");
            // A dummy with no AI does not travel, so the shove shows as velocity.
            h.assertTrue(zombie.getDeltaMovement().lengthSqr() > 0.1,
                    "The creature in the path was not knocked aside");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void trampleAddsDamageAndSlowness(GameTestHelper h) {
        ServerPlayer p = caster(h, P + "shoulder_barge", P + "trample");
        Zombie zombie = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 5);
        float health = zombie.getHealth();
        h.assertTrue(SkillCheck.press(p, P + "shoulder_barge"), "Shoulder Barge did not fire");
        h.onEachTick(() -> SkillCheck.tick(p));
        h.runAfterDelay(20, () -> {
            h.assertTrue(zombie.getHealth() < health, "Trample dealt no damage");
            h.assertTrue(zombie.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Trample applied no Slowness");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void ironAvalancheSpendsAllTemperOnBargeEnd(GameTestHelper h) {
        ServerPlayer p = caster(h, P + "shoulder_barge", P + "iron_avalanche");
        ResourceValues.set(p, TEMPER, 60);
        Zombie zombie = SkillCheck.dummy(h, EntityType.HUSK, 9, 1, 8);
        float health = zombie.getHealth();
        h.assertTrue(SkillCheck.press(p, P + "shoulder_barge"), "Shoulder Barge did not fire");
        h.onEachTick(() -> SkillCheck.tick(p));
        h.runAfterDelay(20, () -> {
            h.assertTrue(SkillCheck.resource(p, TEMPER) == 0, "Temper left after the avalanche: "
                    + SkillCheck.resource(p, TEMPER));
            // 6 damage for 60 Temper, less the zombie's 2 armor points.
            float lost = health - zombie.getHealth();
            h.assertTrue(lost > 4 && lost <= 6, "Avalanche damage for 60 Temper: " + lost);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void plateStanceDrainsProtectsAndReleases(GameTestHelper h) {
        ServerPlayer p = caster(h, P + "plate_stance");
        ResourceValues.set(p, TEMPER, 20);
        h.assertTrue(SkillCheck.press(p, P + "plate_stance"), "Plate Stance did not toggle");
        h.assertTrue(AbilityToggles.isOn(p, STANCE), "Plate Stance is not held");
        h.onEachTick(() -> SkillCheck.tick(p));
        h.runAfterDelay(45, () -> {
            var resistance = p.getEffect(MobEffects.DAMAGE_RESISTANCE);
            h.assertTrue(resistance != null && resistance.getAmplifier() == 2, "No Resistance III in Plate Stance");
            h.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Plate Stance does not slow");
            int temper = SkillCheck.resource(p, TEMPER);
            h.assertTrue(temper <= 12 && temper >= 6, "Temper after two seconds of stance: " + temper);
        });
        // 20 Temper pays for five seconds; the stance must then let go by itself.
        h.runAfterDelay(170, () -> {
            h.assertFalse(AbilityToggles.isOn(p, STANCE), "Plate Stance stayed held with no Temper");
            h.assertFalse(p.hasEffect(MobEffects.DAMAGE_RESISTANCE), "Resistance outlived the stance");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void plungeNeedsAirChargesTemperAndMarksTheFall(GameTestHelper h) {
        ServerPlayer p = caster(h, P + "plunge");
        ResourceValues.set(p, TEMPER, 50);
        h.assertFalse(SkillCheck.press(p, P + "plunge"), "Plunge fired on the ground");
        h.assertTrue(SkillCheck.resource(p, TEMPER) == 50, "A refused Plunge charged Temper");
        var up = h.absoluteVec(new net.minecraft.world.phys.Vec3(8.5, 12, 2.5));
        p.moveTo(up.x, up.y, up.z, 0, 0);
        p.setOnGround(false);
        h.assertTrue(SkillCheck.press(p, P + "plunge"), "Plunge did not fire in the air");
        h.assertTrue(SkillCheck.resource(p, TEMPER) == 20, "Temper after Plunge: " + SkillCheck.resource(p, TEMPER));
        h.assertTrue(p.getDeltaMovement().y < -2, "Plunge did not drive the caster down");
        h.assertTrue(TimedFlags.has(p, PLUNGING), "The fall is not marked as a Plunge");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void ironTreadSinksDrainsStaminaAndReleases(GameTestHelper h) {
        ServerPlayer p = caster(h, P + "iron_tread");
        for (int x = 7; x <= 9; x++) for (int z = 1; z <= 3; z++) for (int y = 1; y <= 2; y++) {
            h.setBlock(x, y, z, Blocks.WATER);
        }
        ResourceValues.set(p, SkillCheck.STAMINA, 12);
        h.assertFalse(AbilityToggles.isOn(p, TREAD), "Iron Tread is held as soon as it is learned");
        // Registered here, not inside a timed step: adding per-tick work while the timed steps run
        // makes the framework run the current step again.
        boolean[] held = {false};
        h.onEachTick(() -> {
            if (held[0]) SkillCheck.tick(p);
        });
        h.runAfterDelay(5, () -> {
            h.assertFalse(AbilityToggles.isOn(p, TREAD), "Iron Tread became held with no key press");
            p.baseTick();
            h.assertFalse(AbilityToggles.isOn(p, TREAD), "A base tick held Iron Tread");
            h.assertTrue(p.isInWater(), "The caster is not in the water");
            h.assertFalse(Buoyancy.ignoresCurrentFluid(p), "The caster sinks before Iron Tread is held: toggle "
                    + AbilityToggles.isOn(p, TREAD) + ", sink ability "
                    + com.aetherianartificer.townstead.root.ability.Abilities.isActive(p,
                            com.aetherianartificer.townstead.root.ability.Ability.SINK) + ", buoyancy powers "
                    + com.aetherianartificer.townstead.root.ExpressedGenes.instancesOf(p,
                            com.aetherianartificer.townstead.root.gene.types.BuoyancyGeneType.Instance.class).size());
            h.assertTrue(SkillCheck.press(p, P + "iron_tread"), "Iron Tread did not toggle");
            h.assertTrue(Buoyancy.ignoresCurrentFluid(p), "Iron Tread does not sink the caster");
            held[0] = true;
        });
        h.runAfterDelay(160, () -> {
            h.assertFalse(AbilityToggles.isOn(p, TREAD), "Iron Tread stayed held with no stamina");
            h.assertFalse(Buoyancy.ignoresCurrentFluid(p), "The caster still sinks after the release");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void batteringRamSmashesGlassForTemper(GameTestHelper h) {
        ServerPlayer p = caster(h, P + "iron_tread", P + "battering_ram");
        ResourceValues.set(p, TEMPER, 20);
        BlockPos glass = new BlockPos(8, 1, 3);
        BlockPos stone = new BlockPos(7, 1, 2);
        h.setBlock(glass, Blocks.GLASS);
        h.setBlock(stone, Blocks.STONE);
        h.onEachTick(() -> SkillCheck.tick(p));
        h.runAfterDelay(30, () -> {
            h.assertBlockPresent(Blocks.GLASS, glass);
            h.assertTrue(SkillCheck.press(p, P + "iron_tread"), "Iron Tread did not toggle");
        });
        h.runAfterDelay(70, () -> {
            h.assertBlockPresent(Blocks.AIR, glass);
            h.assertBlockPresent(Blocks.STONE, stone);
            // 5 for the pane, and Temper cools by 1 every 30 ticks.
            int temper = SkillCheck.resource(p, TEMPER);
            h.assertTrue(temper >= 12 && temper <= 15, "Temper after one pane: " + temper);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void villagerHoldsStanceWhenThreatenedAndShovesAttackers(GameTestHelper h) {
        SkillCheck.floor(h);
        var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(
                ResourceLocation.parse("mca:male_villager"));
        var villager = (net.conczin.mca.entity.VillagerEntityMCA) h.spawn(type, 8, 1, 3);
        villager.setAge(0);
        SkillCheck.learn(villager, P + "plate_stance", P + "anchor");
        ResourceValues.set(villager, TEMPER, 100);
        Zombie zombie = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 5);
        villager.setLastHurtByMob(zombie);
        h.runAfterDelay(60, () -> {
            h.assertTrue(AbilityToggles.isOn(villager, STANCE), "A threatened villager did not hold Plate Stance");
            h.assertTrue(villager.hasEffect(MobEffects.DAMAGE_RESISTANCE), "The villager's stance gives no Resistance");
            zombie.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            villager.hurt(villager.damageSources().mobAttack(zombie), 2);
            h.assertTrue(zombie.hasEffect(MobEffects.WEAKNESS), "Anchor applied no Weakness to the attacker");
            h.assertTrue(zombie.getDeltaMovement().lengthSqr() > 0.1, "Plate Stance did not shove the attacker");
            h.succeed();
        });
    }
}
