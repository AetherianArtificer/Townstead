package com.aetherianartificer.townstead.skillcheck;

import com.aetherianartificer.townstead.pheno.combat.Fears;
import com.aetherianartificer.townstead.pheno.combat.Wards;
import com.aetherianartificer.townstead.root.ability.AbilityToggles;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Farmer: Levy, Scarecrow and Tiller. */
@GameTestHolder(SkillCheck.ID)
@PrefixGameTestTemplate(false)
public final class FarmerTests {
    private static final String L = "minecraft:farmer/levy/";
    private static final String S = "minecraft:farmer/scarecrow/";
    private static final String T = "minecraft:farmer/tiller/";
    private static final ResourceLocation GRIT = new ResourceLocation("townstead:grit");

    private static ServerPlayer withHoe(GameTestHelper h, int grit, String... skills) {
        ServerPlayer p = SkillCheck.ready(h, GRIT, grit, skills);
        p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_HOE));
        return p;
    }

    private static int grit(ServerPlayer p) {
        return SkillCheck.resource(p, GRIT);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void reapRowHarvestsTheRowIntoThePack(GameTestHelper h) {
        ServerPlayer p = withHoe(h, 0, L + "reap_row");
        for (int z = 3; z <= 8; z++) {
            h.setBlock(8, 0, z, Blocks.FARMLAND);
            h.setBlock(8, 1, z, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        }
        h.assertTrue(SkillCheck.press(p, L + "reap_row"), "Reap Row did not fire");
        h.assertBlockPresent(Blocks.AIR, new BlockPos(8, 1, 3));
        h.assertBlockPresent(Blocks.AIR, new BlockPos(8, 1, 7));
        h.assertBlockPresent(Blocks.WHEAT, new BlockPos(8, 1, 8));
        h.assertTrue(p.getInventory().countItem(Items.WHEAT) >= 5, "The pack holds " + p.getInventory().countItem(Items.WHEAT) + " wheat");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void reapHitsHarderAtTheTipWithSweepTheRows(GameTestHelper h) {
        ServerPlayer p = withHoe(h, 0, L + "reap", L + "sweep_the_rows");
        Zombie close = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 3);
        Zombie tip = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 6);
        float a = close.getHealth();
        float b = tip.getHealth();
        h.assertTrue(SkillCheck.press(p, L + "reap"), "Reap did not fire");
        h.assertTrue(a - close.getHealth() > 3.5f && a - close.getHealth() <= 4f, "Close: " + (a - close.getHealth()));
        h.assertTrue(b - tip.getHealth() > 7f && b - tip.getHealth() <= 8f, "At the tip: " + (b - tip.getHealth()));
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void pitchforksArmsAlliesAgainstAttackers(GameTestHelper h) {
        ServerPlayer p = withHoe(h, 30, L + "pitchforks", L + "hold_the_line");
        Cow ally = SkillCheck.dummy(h, EntityType.COW, 8, 1, 5);
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 7);
        h.assertTrue(SkillCheck.press(p, L + "pitchforks"), "Pitchforks did not fire");
        h.assertTrue(grit(p) == 0, "Pitchforks did not cost 30 Grit");
        h.assertTrue(Wards.remaining(ally, "hold_the_line") == 6f, "Hold the Line placed no 6 point ward");
        float health = husk.getHealth();
        ally.hurt(ally.damageSources().mobAttack(husk), 4);
        h.assertTrue(health - husk.getHealth() >= 3f, "The attacker took " + (health - husk.getHealth()));
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void booFrightensHostileCreatures(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, GRIT, 0, S + "boo");
        Zombie husk = h.spawn(EntityType.HUSK, new BlockPos(8, 1, 4));
        h.assertTrue(SkillCheck.press(p, S + "boo"), "Boo did not fire");
        h.assertTrue(Fears.afraid(husk), "The creature does not flee");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void scarecrowsPostRootsYouAndKeepsThemBack(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, GRIT, 30, S + "scarecrows_post");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 4);
        ResourceLocation post = new ResourceLocation(S + "scarecrows_post");
        h.assertTrue(SkillCheck.press(p, S + "scarecrows_post"), "Scarecrow's Post did not switch on");
        h.onEachTick(() -> SkillCheck.tick(p));
        h.runAfterDelay(45, () -> {
            h.assertTrue(AbilityToggles.isOn(p, post), "The Post let go early");
            h.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "The Post does not root its holder");
            h.assertTrue(grit(p) < 30, "The Post drained nothing");
            h.assertTrue(husk.getDeltaMovement().horizontalDistanceSqr() > 0.01, "The hostile creature was not pushed back");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void strawDoubleLeavesAFigure(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, GRIT, 0, S + "straw_double");
        h.assertTrue(SkillCheck.press(p, S + "straw_double"), "Straw Double did not fire");
        int figures = h.getLevel().getEntitiesOfClass(ArmorStand.class, p.getBoundingBox().inflate(3)).size();
        h.assertTrue(figures == 1, "Figures left where the caster stood: " + figures);
        h.assertTrue(SkillCheck.flagged(p, "townstead:scarecrow_figure"), "The figure is not tracked");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void plowAheadTillsTheGroundItCrosses(GameTestHelper h) {
        ServerPlayer p = withHoe(h, 0, T + "plow_ahead");
        for (int z = 3; z <= 8; z++) h.setBlock(8, 0, z, Blocks.DIRT);
        double start = p.getZ();
        h.assertTrue(SkillCheck.press(p, T + "plow_ahead"), "Plow Ahead did not fire");
        h.assertBlockPresent(Blocks.FARMLAND, new BlockPos(8, 0, 3));
        h.assertBlockPresent(Blocks.FARMLAND, new BlockPos(8, 0, 7));
        h.assertBlockPresent(Blocks.DIRT, new BlockPos(8, 0, 8));
        h.onEachTick(() -> SkillCheck.tick(p));
        h.runAfterDelay(15, () -> {
            h.assertTrue(p.getZ() - start > 3, "The dash covered " + (p.getZ() - start) + " blocks");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void irrigateWetsFarmlandAndNeedsSome(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, GRIT, 30, T + "irrigate");
        h.assertFalse(SkillCheck.press(p, T + "irrigate"), "Irrigate fired with no farmland");
        BlockPos field = new BlockPos(9, 0, 3);
        h.setBlock(field, Blocks.FARMLAND);
        h.assertTrue(SkillCheck.press(p, T + "irrigate"), "Irrigate did not fire");
        h.assertTrue(h.getBlockState(field).getValue(FarmBlock.MOISTURE) == 7, "The farmland is not wet");
        h.assertTrue(grit(p) == 0, "Irrigate did not cost 30 Grit");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void faultLineThrowsCreaturesAside(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, GRIT, 0, T + "fault_line");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 6);
        h.assertTrue(SkillCheck.press(p, T + "fault_line"), "Fault Line did not fire");
        h.assertTrue(husk.getDeltaMovement().horizontalDistanceSqr() > 0.5, "The creature was not thrown: " + husk.getDeltaMovement());
        h.assertTrue(husk.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Fault Line applied no Slowness");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void earthquakeShakesForThreeSeconds(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, GRIT, 30, T + "earthquake");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 6);
        h.assertTrue(SkillCheck.press(p, T + "earthquake"), "Earthquake did not fire");
        h.assertTrue(grit(p) == 0, "Earthquake did not cost 30 Grit");
        h.runAfterDelay(30, () -> {
            h.assertTrue(husk.hasEffect(MobEffects.WEAKNESS), "The shaken creature can still strike");
            h.assertTrue(husk.getDeltaMovement().horizontalDistanceSqr() > 0.01, "The creature was not thrown about");
            h.succeed();
        });
    }
}
