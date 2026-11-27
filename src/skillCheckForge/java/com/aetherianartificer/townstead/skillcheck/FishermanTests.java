package com.aetherianartificer.townstead.skillcheck;

import com.aetherianartificer.townstead.pheno.combat.Wards;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Fisherman: Harpooner, Netter and Tidecaller. The caster stands at (8,1,2) and faces +z. */
@GameTestHolder(SkillCheck.ID)
@PrefixGameTestTemplate(false)
public final class FishermanTests {
    private static final String H = "minecraft:fisherman/harpooner/";
    private static final String N = "minecraft:fisherman/netter/";
    private static final String T = "minecraft:fisherman/tidecaller/";
    private static final ResourceLocation TIDE = new ResourceLocation("townstead:tide");

    private static ServerPlayer withRod(GameTestHelper h, int tide, String... skills) {
        ServerPlayer p = SkillCheck.ready(h, TIDE, tide, skills);
        p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.FISHING_ROD));
        return p;
    }

    private static int tide(ServerPlayer p) {
        return SkillCheck.resource(p, TIDE);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void getOverHerePullsAndSnagStaggers(GameTestHelper h) {
        ServerPlayer p = withRod(h, 0, H + "get_over_here", H + "snag");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 9);
        h.assertTrue(SkillCheck.press(p, H + "get_over_here"), "Get Over Here did not fire");
        h.assertTrue(husk.getDeltaMovement().z < -1.0, "The creature was not pulled: " + husk.getDeltaMovement());
        h.assertTrue(tide(p) == 10, "A pull gave " + tide(p) + " Tide");
        h.assertTrue(SkillCheck.flagged(husk, "townstead:harpooner_snagged"), "Snag did not mark the creature");
        float dealt = SkillCheck.struck(husk, husk.damageSources().playerAttack(p), 4);
        h.assertTrue(Math.abs(dealt - 8f) < 0.01f, "A hit on a snagged creature dealt " + dealt);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void trawlPullsEverythingInFront(GameTestHelper h) {
        ServerPlayer p = withRod(h, 40, H + "trawl");
        Zombie one = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 8);
        Zombie two = SkillCheck.dummy(h, EntityType.HUSK, 9, 1, 11);
        h.assertTrue(SkillCheck.press(p, H + "trawl"), "Trawl did not fire");
        h.assertTrue(one.getDeltaMovement().z < -0.8 && two.getDeltaMovement().z < -0.8, "Trawl did not pull both");
        h.assertTrue(tide(p) == 0, "Trawl did not cost 40 Tide");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void castOffPullsTheCasterToItsTarget(GameTestHelper h) {
        ServerPlayer p = withRod(h, 30, H + "cast_off");
        SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 9);
        h.assertTrue(SkillCheck.press(p, H + "cast_off"), "Cast Off did not fire");
        h.assertTrue(p.getDeltaMovement().z > 1.0, "The caster was not pulled: " + p.getDeltaMovement()
                + " tide " + tide(p) + " at " + p.position());
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void gaffReelsInADroppedItem(GameTestHelper h) {
        ServerPlayer p = withRod(h, 0, H + "gaff");
        var item = h.spawnItem(Items.EMERALD, 8.5f, 2.6f, 8.5f);
        item.setNoGravity(true);
        item.setDeltaMovement(0, 0, 0);
        double start = item.distanceTo(p);
        h.assertTrue(SkillCheck.press(p, H + "gaff"), "Gaff did not fire");
        h.onEachTick(() -> SkillCheck.tick(p));
        h.runAfterDelay(60, () -> {
            boolean near = !item.isAlive() || item.distanceTo(p) < start - 3;
            h.assertTrue(near, "The item stayed " + item.distanceTo(p) + " blocks away");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void tossNetNetsHostileCreatures(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, TIDE, 0, N + "toss_net", N + "weighted_net");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 5);
        h.assertTrue(SkillCheck.press(p, N + "toss_net"), "Toss Net did not fire");
        String block = BuiltInRegistries.BLOCK.getKey(h.getBlockState(new BlockPos(8, 1, 5)).getBlock()).toString();
        h.assertTrue(block.equals("townstead:haze"), "No net under the creature: " + block);
        h.assertTrue(husk.hasEffect(MobEffects.JUMP), "Weighted Net did not pin the creature");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void lureDrawsCreaturesAndWardsTheCaster(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, TIDE, 30, N + "lure", N + "bait_and_switch");
        Zombie husk = h.spawn(EntityType.HUSK, new BlockPos(8, 1, 6));
        h.assertTrue(SkillCheck.press(p, N + "lure"), "Lure did not fire");
        h.assertTrue(tide(p) == 0, "Lure did not cost 30 Tide");
        h.assertTrue(Wards.remaining(p, "lure") == 8f, "Lure placed no 8 point ward");
        h.assertTrue(SkillCheck.flagged(husk, "townstead:netter_lured"), "Bait and Switch did not mark the creature");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void landingNetCarriesASmallCreature(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, TIDE, 0, N + "landing_net");
        Chicken chicken = SkillCheck.dummy(h, EntityType.CHICKEN, 8, 1, 4);
        Cow cow = SkillCheck.dummy(h, EntityType.COW, 8, 1, 5);
        p.setXRot(25f);
        h.assertTrue(SkillCheck.press(p, N + "landing_net"), "Landing Net did not fire");
        h.assertTrue(chicken.getVehicle() == p, "The chicken is not carried");
        h.assertTrue(cow.getVehicle() == null, "A cow was netted");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void splashPushesAndSoaks(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, TIDE, 0, T + "splash");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 4);
        husk.setRemainingFireTicks(100);
        h.assertTrue(SkillCheck.press(p, T + "splash"), "Splash did not fire");
        h.assertTrue(husk.getDeltaMovement().z > 0.5, "Splash did not push the creature");
        h.assertTrue(SkillCheck.flagged(husk, "townstead:tidecaller_soaked"), "The creature is not soaked");
        h.assertFalse(husk.isOnFire(), "A soaked creature still burns");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void whirlpoolDragsCreaturesToItsCenter(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, TIDE, 30, T + "whirlpool");
        SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 7);
        Zombie outer = SkillCheck.dummy(h, EntityType.HUSK, 12, 1, 7);
        h.assertTrue(SkillCheck.press(p, T + "whirlpool"), "Whirlpool did not fire");
        h.assertTrue(tide(p) == 0, "Whirlpool did not cost 30 Tide");
        h.runAfterDelay(30, () -> {
            h.assertTrue(outer.getDeltaMovement().x < -0.3, "The outer creature was not dragged in: " + outer.getDeltaMovement());
            h.assertTrue(outer.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "The vortex does not slow");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void waveCarriesCreaturesAhead(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, TIDE, 30, T + "wave");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 7);
        h.assertTrue(SkillCheck.press(p, T + "wave"), "Wave did not fire");
        h.assertTrue(husk.getDeltaMovement().z > 1.0, "The wave did not carry the creature: " + husk.getDeltaMovement());
        h.assertTrue(tide(p) == 0, "Wave did not cost 30 Tide");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void deepBreathReachesAllies(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, TIDE, 0, T + "deep_breath");
        Cow ally = SkillCheck.dummy(h, EntityType.COW, 8, 1, 6);
        h.assertTrue(SkillCheck.press(p, T + "deep_breath"), "Deep Breath did not fire");
        // The caster is not in the world's entity list here, so an area that includes the caster misses it.
        h.assertTrue(ally.hasEffect(MobEffects.WATER_BREATHING), "Deep Breath did not reach the ally");
        h.succeed();
    }
}
