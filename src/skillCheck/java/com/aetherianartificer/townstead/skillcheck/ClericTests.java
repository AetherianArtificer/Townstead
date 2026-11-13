package com.aetherianartificer.townstead.skillcheck;

import com.aetherianartificer.townstead.pheno.combat.Fears;
import com.aetherianartificer.townstead.pheno.combat.Wards;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Cleric: Confessor, Exorcist and Pilgrim. */
@GameTestHolder(SkillCheck.ID)
@PrefixGameTestTemplate(false)
public final class ClericTests {
    private static final String C = "minecraft:cleric/confessor/";
    private static final String E = "minecraft:cleric/exorcist/";
    private static final String P = "minecraft:cleric/pilgrim/";
    private static final ResourceLocation FAITH = ResourceLocation.parse("townstead:faith");

    private static int faith(ServerPlayer p) {
        return SkillCheck.resource(p, FAITH);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void cleanseClearsHarmfulEffects(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, FAITH, 0, C + "cleanse");
        Cow ally = SkillCheck.dummy(h, EntityType.COW, 8, 1, 5);
        ally.addEffect(new MobEffectInstance(MobEffects.POISON, 400));
        ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400));
        h.assertTrue(SkillCheck.press(p, C + "cleanse"), "Cleanse did not fire");
        h.assertFalse(ally.hasEffect(MobEffects.POISON) || ally.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                "The ally still carries harmful effects");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void shieldOfFaithWardsAndClearsWhenItSpares(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, FAITH, 0, C + "shield_of_faith");
        Cow ally = SkillCheck.dummy(h, EntityType.COW, 8, 1, 5);
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 7);
        h.assertTrue(SkillCheck.press(p, C + "shield_of_faith"), "Shield of Faith did not fire");
        h.assertTrue(Wards.remaining(ally, "faith") == 4f, "The ally got no 4 point ward");
        ally.addEffect(new MobEffectInstance(MobEffects.POISON, 400));
        float health = ally.getHealth();
        ally.hurt(ally.damageSources().mobAttack(husk), 2);
        h.assertTrue(ally.getHealth() == health, "The ward did not spare the hit");
        h.assertFalse(ally.hasEffect(MobEffects.POISON), "A spared hit cleared nothing");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void lastRitesWardsTheAimedAllyAndHealsWhenItBreaks(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, FAITH, 30, C + "last_rites");
        Cow ally = SkillCheck.dummy(h, EntityType.COW, 8, 1, 5);
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 7);
        ally.setHealth(4f);
        h.assertTrue(SkillCheck.press(p, C + "last_rites"), "Last Rites did not fire");
        h.assertTrue(faith(p) == 0, "Last Rites did not cost 30 Faith");
        h.assertTrue(Wards.remaining(ally, "last_rites") == 12f, "The aimed ally got no 12 point ward");
        ally.hurt(ally.damageSources().mobAttack(husk), 14);
        // 12 spared, 2 taken, 4 healed.
        h.assertTrue(Math.abs(ally.getHealth() - 6f) < 0.01f, "After the ward broke the ally has " + ally.getHealth());
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void absolutionHealsTheAimedAlly(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, FAITH, 30, C + "absolution");
        Cow ally = SkillCheck.dummy(h, EntityType.COW, 8, 1, 5);
        ally.setHealth(2f);
        h.assertTrue(SkillCheck.press(p, C + "absolution"), "Absolution did not fire");
        h.assertTrue(Math.abs(ally.getHealth() - 8f) < 0.01f, "The ally has " + ally.getHealth());
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void turnUndeadFrightensTheUndead(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, FAITH, 0, E + "turn_undead", E + "rebuke");
        Zombie husk = h.spawn(EntityType.HUSK, new BlockPos(8, 1, 5));
        float health = husk.getHealth();
        h.assertTrue(SkillCheck.press(p, E + "turn_undead"), "Turn Undead did not fire");
        h.assertTrue(Fears.afraid(husk), "The undead creature does not flee");
        h.assertTrue(husk.hasEffect(MobEffects.WEAKNESS), "The undead creature is not weakened");
        h.assertTrue(husk.getHealth() < health, "Rebuke dealt no damage");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 140)
    public static void banishHoldsThenHurtsTheUndead(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, FAITH, 30, E + "banish");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 6);
        float health = husk.getHealth();
        h.assertTrue(SkillCheck.press(p, E + "banish"), "Banish did not fire");
        var slow = husk.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        h.assertTrue(slow != null && slow.getAmplifier() >= 6, "The banished creature can still move");
        h.runAfterDelay(40, () -> h.assertTrue(husk.getHealth() == health, "The return came early"));
        h.runAfterDelay(95, () -> {
            float dealt = health - husk.getHealth();
            h.assertTrue(dealt > 7f && dealt <= 8f, "The return dealt " + dealt);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void candleDrivesTheUndeadAway(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, FAITH, 30, E + "candle");
        Zombie husk = h.spawn(EntityType.HUSK, new BlockPos(8, 1, 5));
        h.assertTrue(SkillCheck.press(p, E + "candle"), "Candle did not fire");
        boolean[] fled = {false};
        h.onEachTick(() -> fled[0] |= Fears.afraid(husk));
        h.runAfterDelay(60, () -> {
            h.assertTrue(fled[0], "The undead creature never fled the Candle");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void staffSweepTripsAndBearTheLoadMakesItHeavier(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, FAITH, 30, P + "bear_the_load", P + "staff_sweep");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 4);
        Cow ally = SkillCheck.dummy(h, EntityType.COW, 10, 1, 4);
        ally.addEffect(new MobEffectInstance(MobEffects.POISON, 400));
        float health = husk.getHealth();
        h.assertTrue(SkillCheck.press(p, P + "staff_sweep"), "Staff Sweep did not fire");
        float plain = health - husk.getHealth();
        h.assertTrue(plain > 1.5f && plain <= 2f, "Staff Sweep dealt " + plain);
        h.assertTrue(husk.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "The creature did not trip");
        SkillCheck.learn(p, P + "bear_the_load");
        h.assertTrue(SkillCheck.press(p, P + "bear_the_load"), "Bear the Load did not fire");
        h.assertFalse(ally.hasEffect(MobEffects.POISON), "The ally still carries Poison");
        h.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "The caster carries nothing");
        h.assertTrue(SkillCheck.flagged(p, "townstead:pilgrim_load"), "The load is not tracked");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void shareWaterClearsHungerAndPoison(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, FAITH, 0, P + "share_water");
        Cow ally = SkillCheck.dummy(h, EntityType.COW, 8, 1, 5);
        ally.addEffect(new MobEffectInstance(MobEffects.HUNGER, 400));
        h.assertTrue(SkillCheck.press(p, P + "share_water"), "Share Water did not fire");
        h.assertFalse(ally.hasEffect(MobEffects.HUNGER), "The ally still carries Hunger");
        h.succeed();
    }
}
