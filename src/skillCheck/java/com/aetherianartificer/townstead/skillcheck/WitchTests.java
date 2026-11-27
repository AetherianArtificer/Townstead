package com.aetherianartificer.townstead.skillcheck;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Witch: Broomrider, Herbalist and Hexer. */
@GameTestHolder(SkillCheck.ID)
@PrefixGameTestTemplate(false)
public final class WitchTests {
    private static final String B = "townstead:witch/broomrider/";
    private static final String R = "townstead:witch/herbalist/";
    private static final String X = "townstead:witch/hexer/";
    private static final ResourceLocation HEX = ResourceLocation.parse("townstead:hex");

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void takeOffLeapsAndFallsSlowly(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, HEX, 0, B + "take_off");
        h.assertTrue(SkillCheck.press(p, B + "take_off"), "Take Off did not fire");
        h.assertTrue(p.getDeltaMovement().y > 1.0, "The leap was " + p.getDeltaMovement());
        h.assertTrue(p.hasEffect(MobEffects.SLOW_FALLING), "Take Off gave no Slow Falling");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void broomstickOpensFlightForATime(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, HEX, 30, B + "broomstick");
        h.assertTrue(SkillCheck.press(p, B + "broomstick"), "Broomstick did not fire");
        h.assertTrue(SkillCheck.flagged(p, "townstead:broomrider_broom"), "The broom is not out");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void hexBoltStrikesAndWeakens(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, HEX, 0, B + "hex_bolt");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 12);
        husk.invulnerableTime = 0;
        float health = husk.getHealth();
        h.assertTrue(SkillCheck.press(p, B + "hex_bolt"), "Hex Bolt did not fire");
        float dealt = health - husk.getHealth();
        h.assertTrue(dealt > 4f && dealt <= 5f, "Hex Bolt dealt " + dealt);
        h.assertTrue(husk.hasEffect(MobEffects.WEAKNESS), "The creature is not weakened");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void poulticeWithTonicTurnsSlownessIntoSpeed(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, HEX, 0, R + "poultice", R + "tonic");
        Cow ally = SkillCheck.dummy(h, EntityType.COW, 8, 1, 5);
        ally.addEffect(new MobEffectInstance(MobEffects.POISON, 400));
        ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400));
        h.assertTrue(SkillCheck.press(p, R + "poultice"), "Poultice did not fire");
        h.assertFalse(ally.hasEffect(MobEffects.POISON), "The ally is still poisoned");
        h.assertFalse(ally.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "The ally is still slowed");
        h.assertTrue(ally.hasEffect(MobEffects.MOVEMENT_SPEED), "Slowness did not turn into Speed");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void nettleStingPoisonsOnlyHostileCreatures(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, HEX, 0, R + "nettle_sting");
        Pillager husk = SkillCheck.dummy(h, EntityType.PILLAGER, 8, 1, 4);
        Cow ally = SkillCheck.dummy(h, EntityType.COW, 9, 1, 4);
        h.assertTrue(SkillCheck.press(p, R + "nettle_sting"), "Nettle Sting did not fire");
        h.assertTrue(husk.hasEffect(MobEffects.POISON), "The hostile creature is not poisoned");
        h.assertFalse(ally.hasEffect(MobEffects.POISON), "Nettle Sting poisoned an ally");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void evilEyeHexesWhatHurtsYou(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, HEX, 0, X + "evil_eye");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 4);
        com.aetherianartificer.townstead.root.trigger.GeneTriggers.onDamage(p, p.damageSources().mobAttack(husk), 2);
        h.assertTrue(SkillCheck.flagged(husk, "townstead:hexer_hexed"), "The attacker is not hexed");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void hexOnAHexedCreatureStrikesIt(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, HEX, 30, X + "mass_hex");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 8);
        h.assertTrue(SkillCheck.press(p, X + "mass_hex"), "Mass Hex did not fire");
        h.assertTrue(SkillCheck.flagged(husk, "townstead:hexer_hexed"), "Mass Hex hexed nothing");
        SkillCheck.learn(p, X + "hex");
        husk.invulnerableTime = 0;
        float health = husk.getHealth();
        h.assertTrue(SkillCheck.press(p, X + "hex"), "Hex did not fire");
        float dealt = health - husk.getHealth();
        h.assertTrue(dealt > 2.5f && dealt <= 3f, "The strike dealt " + dealt);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void poppetSendsYourHurtToHexedCreatures(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, HEX, 60, X + "mass_hex", X + "poppet");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 6);
        h.assertTrue(SkillCheck.press(p, X + "mass_hex"), "Mass Hex did not fire");
        h.assertTrue(SkillCheck.press(p, X + "poppet"), "Poppet did not fire");
        husk.invulnerableTime = 0;
        float health = husk.getHealth();
        com.aetherianartificer.townstead.root.trigger.GeneTriggers.onDamage(p, p.damageSources().magic(), 2);
        float dealt = health - husk.getHealth();
        h.assertTrue(dealt > 1.5f && dealt <= 2f, "The Poppet dealt " + dealt);
        h.succeed();
    }
}
