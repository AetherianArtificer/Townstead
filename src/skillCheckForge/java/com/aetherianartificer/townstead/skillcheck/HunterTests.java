package com.aetherianartificer.townstead.skillcheck;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Hunter: Nightward, Slayer and Trailreader. A husk is undead, so it takes the stake's full weight. */
@GameTestHolder(SkillCheck.ID)
@PrefixGameTestTemplate(false)
public final class HunterTests {
    private static final String N = "townstead:hunter/nightward/";
    private static final String S = "townstead:hunter/slayer/";
    private static final String T = "townstead:hunter/trailreader/";
    private static final ResourceLocation VIGIL = new ResourceLocation("townstead:vigil");

    private static int vigil(ServerPlayer p) {
        return SkillCheck.resource(p, VIGIL);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void sunburstBurnsAndBlindsTheUndead(GameTestHelper h) {
        if (SkillCheck.missing(h, N + "sunburst")) return;
        ServerPlayer p = SkillCheck.ready(h, VIGIL, 40, N + "sunburst");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 6);
        h.assertTrue(SkillCheck.press(p, N + "sunburst"), "Sunburst did not fire");
        h.assertTrue(husk.isOnFire(), "The undead creature does not burn");
        h.assertTrue(husk.hasEffect(MobEffects.BLINDNESS), "The undead creature is not blinded");
        h.assertTrue(vigil(p) == 0, "Sunburst did not cost 40 Vigil");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void stakeThrustHitsTheUndeadHarder(GameTestHelper h) {
        if (SkillCheck.missing(h, S + "stake_thrust")) return;
        ServerPlayer p = SkillCheck.ready(h, VIGIL, 0, S + "stake_thrust");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 4);
        float health = husk.getHealth();
        h.assertTrue(SkillCheck.press(p, S + "stake_thrust"), "Stake Thrust did not fire");
        float dealt = health - husk.getHealth();
        h.assertTrue(dealt > 8f && dealt <= 9f, "Stake Thrust dealt " + dealt + " to an undead creature");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void pounceDashesAndStrikes(GameTestHelper h) {
        if (SkillCheck.missing(h, S + "pounce")) return;
        ServerPlayer p = SkillCheck.ready(h, VIGIL, 30, S + "pounce");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 5);
        float health = husk.getHealth();
        h.assertTrue(SkillCheck.press(p, S + "pounce"), "Pounce did not fire");
        h.onEachTick(() -> SkillCheck.tick(p));
        h.runAfterDelay(15, () -> {
            float dealt = health - husk.getHealth();
            h.assertTrue(dealt > 5f && dealt <= 6f, "Pounce dealt " + dealt);
            h.assertTrue(vigil(p) == 0, "Pounce did not cost 30 Vigil");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void vowNamesAQuarry(GameTestHelper h) {
        if (SkillCheck.missing(h, S + "vow")) return;
        ServerPlayer p = SkillCheck.ready(h, VIGIL, 30, S + "vow");
        Zombie quarry = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 6);
        Zombie other = SkillCheck.dummy(h, EntityType.HUSK, 12, 1, 6);
        h.assertTrue(SkillCheck.press(p, S + "vow"), "Vow did not fire");
        h.assertTrue(SkillCheck.flagged(quarry, "townstead:slayer_quarry"), "The aimed creature is not the quarry");
        h.onEachTick(() -> SkillCheck.tick(p));
        h.runAfterDelay(25, () -> {
            float on = SkillCheck.struck(quarry, quarry.damageSources().playerAttack(p), 4);
            float off = SkillCheck.struck(other, other.damageSources().playerAttack(p), 4);
            h.assertTrue(Math.abs(on - 5f) < 0.01f, "A hit on the quarry dealt " + on);
            h.assertTrue(Math.abs(off - 3f) < 0.01f, "A hit on another creature dealt " + off);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void markQuarryThenCalledShot(GameTestHelper h) {
        if (SkillCheck.missing(h, T + "mark_quarry")) return;
        ServerPlayer p = SkillCheck.ready(h, VIGIL, 30, T + "called_shot", T + "mark_quarry");
        Zombie marked = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 8);
        Zombie plain = SkillCheck.dummy(h, EntityType.HUSK, 12, 1, 8);
        float a = marked.getHealth();
        float b = plain.getHealth();
        h.assertTrue(SkillCheck.press(p, T + "mark_quarry"), "Mark Quarry did not fire");
        h.assertTrue(marked.hasEffect(MobEffects.GLOWING), "The marked creature does not glow");
        SkillCheck.learn(p, T + "called_shot");
        h.assertTrue(SkillCheck.press(p, T + "called_shot"), "Called Shot did not fire");
        float dealt = a - marked.getHealth();
        h.assertTrue(dealt > 4f && dealt <= 5f, "Called Shot dealt " + dealt + " to the marked creature");
        h.assertTrue(plain.getHealth() == b, "Called Shot hurt a creature that is not marked");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void bolaSlowsTheAimedCreature(GameTestHelper h) {
        if (SkillCheck.missing(h, T + "bola")) return;
        ServerPlayer p = SkillCheck.ready(h, VIGIL, 0, T + "bola");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 10);
        h.assertTrue(SkillCheck.press(p, T + "bola"), "Bola did not fire");
        var slow = husk.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        h.assertTrue(slow != null && slow.getAmplifier() == 2, "The creature has no Slowness III");
        h.succeed();
    }
}
