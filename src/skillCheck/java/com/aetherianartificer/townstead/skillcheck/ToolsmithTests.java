package com.aetherianartificer.townstead.skillcheck;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Toolsmith: Delver, Sapper and Tinker. The test world lies below sea level. */
@GameTestHolder(SkillCheck.ID)
@PrefixGameTestTemplate(false)
public final class ToolsmithTests {
    private static final String D = "minecraft:toolsmith/delver/";
    private static final String S = "minecraft:toolsmith/sapper/";
    private static final String T = "minecraft:toolsmith/tinker/";
    private static final ResourceLocation KNACK = ResourceLocation.parse("townstead:knack");

    private static ServerPlayer miner(GameTestHelper h, int knack, String... skills) {
        ServerPlayer p = SkillCheck.ready(h, KNACK, knack, skills);
        p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        return p;
    }

    private static int knack(ServerPlayer p) {
        return SkillCheck.resource(p, KNACK);
    }

    private static void cube(GameTestHelper h, net.minecraft.world.level.block.Block block) {
        for (int x = 5; x <= 11; x++) for (int y = 1; y <= 5; y++) for (int z = 4; z <= 9; z++) h.setBlock(x, y, z, block);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void boreBreaksAThreeByThreeAroundTheAimedBlock(GameTestHelper h) {
        ServerPlayer p = miner(h, 0, D + "bore");
        cube(h, Blocks.STONE);
        h.assertTrue(SkillCheck.press(p, D + "bore"), "Bore did not fire");
        h.assertTrue(SkillCheck.resource(p, SkillCheck.STAMINA) == 75, "Bore did not cost 25 stamina");
        h.assertBlockPresent(Blocks.AIR, new BlockPos(8, 2, 4));
        h.assertBlockPresent(Blocks.AIR, new BlockPos(9, 3, 5));
        h.assertBlockPresent(Blocks.AIR, new BlockPos(7, 1, 4));
        h.assertBlockPresent(Blocks.STONE, new BlockPos(8, 2, 6));
        h.assertBlockPresent(Blocks.STONE, new BlockPos(10, 2, 4));
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void veinBreaksTheAimedOreAndThreeMore(GameTestHelper h) {
        ServerPlayer p = miner(h, 30, D + "vein");
        BlockPos[] ore = {new BlockPos(8, 2, 4), new BlockPos(8, 3, 4), new BlockPos(8, 2, 5), new BlockPos(9, 2, 4),
                new BlockPos(7, 2, 4), new BlockPos(8, 1, 4)};
        for (BlockPos pos : ore) h.setBlock(pos, Blocks.IRON_ORE);
        h.assertTrue(SkillCheck.press(p, D + "vein"), "Vein did not fire");
        h.assertTrue(knack(p) == 0, "Vein did not cost 30 Knack");
        int left = 0;
        for (BlockPos pos : ore) if (h.getBlockState(pos).is(Blocks.IRON_ORE)) left++;
        h.assertTrue(left == 2, "Vein left " + left + " of 6 ore blocks");
        h.assertBlockPresent(Blocks.AIR, new BlockPos(8, 2, 4));
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void caveInHurtsSlowsAndDustUpBlinds(GameTestHelper h) {
        ServerPlayer p = miner(h, 0, D + "cave_in", D + "dust_up");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 5);
        float health = husk.getHealth();
        h.assertTrue(SkillCheck.press(p, D + "cave_in"), "Cave-In did not fire");
        h.assertTrue(health - husk.getHealth() > 3.5f && health - husk.getHealth() <= 4f, "Cave-In dealt " + (health - husk.getHealth()));
        h.assertTrue(husk.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Cave-In applied no Slowness");
        h.assertTrue(husk.hasEffect(MobEffects.BLINDNESS), "Dust Up did not blind");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void deepDwellerDoublesCaveInBelowSeaLevel(GameTestHelper h) {
        ServerPlayer p = miner(h, 0, D + "cave_in", D + "deep_dweller");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 5);
        float health = husk.getHealth();
        h.assertTrue(SkillCheck.press(p, D + "cave_in"), "Cave-In did not fire");
        h.assertTrue(health - husk.getHealth() > 7f && health - husk.getHealth() <= 8f, "Deep Cave-In dealt " + (health - husk.getHealth()));
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void undermineHoldsTheAimedCreature(GameTestHelper h) {
        ServerPlayer p = miner(h, 30, D + "undermine");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 6);
        h.assertTrue(SkillCheck.press(p, D + "undermine"), "Undermine did not fire");
        var slow = husk.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        h.assertTrue(slow != null && slow.getAmplifier() >= 6, "The aimed creature is not held");
        h.assertTrue(knack(p) == 0, "Undermine did not cost 30 Knack");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void firecrackerBlastsTheAimedCreatureAndChains(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, KNACK, 0, S + "firecracker", S + "chain_reaction");
        Zombie aimed = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 8);
        Zombie beside = SkillCheck.dummy(h, EntityType.HUSK, 11, 1, 10);
        float a = aimed.getHealth();
        float b = beside.getHealth();
        h.assertTrue(SkillCheck.press(p, S + "firecracker"), "Firecracker did not fire");
        h.runAfterDelay(25, () -> {
            h.assertTrue(aimed.getHealth() < a, "Firecracker did not hurt the aimed creature");
            h.assertTrue(beside.getHealth() < b, "Chain Reaction passed no blast on");
            h.assertBlockPresent(Blocks.STONE, new BlockPos(8, 0, 8));
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void shapedChargeWaitsTwoSecondsAndSticks(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, KNACK, 30, S + "shaped_charge", S + "sticky");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 8);
        float health = husk.getHealth();
        h.assertTrue(SkillCheck.press(p, S + "shaped_charge"), "Shaped Charge did not fire");
        h.assertTrue(knack(p) == 0, "Shaped Charge did not cost 30 Knack");
        h.assertTrue(husk.hasEffect(MobEffects.GLOWING), "Sticky did not mark the creature");
        h.runAfterDelay(25, () -> h.assertTrue(husk.getHealth() == health, "The charge went off early"));
        h.runAfterDelay(55, () -> {
            h.assertTrue(husk.getHealth() < health, "The charge never went off");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void rocketJumpLaunchesTheCasterUnhurt(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, KNACK, 30, S + "rocket_jump", S + "soft_landing");
        h.assertTrue(SkillCheck.press(p, S + "rocket_jump"), "Rocket Jump did not fire");
        h.assertTrue(p.getDeltaMovement().y > 1.0, "Rocket Jump launched at " + p.getDeltaMovement().y);
        h.assertTrue(SkillCheck.flagged(p, "townstead:sapper_soft"), "Soft Landing was not readied");
        float fall = SkillCheck.struck(p, p.damageSources().fall(), 10);
        h.assertTrue(fall == 0f, "The fall after Rocket Jump dealt " + fall);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void minefieldWaitsForAHostileCreature(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, KNACK, 0, S + "firecracker", S + "minefield");
        Zombie far = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 10);
        float health = far.getHealth();
        h.assertTrue(SkillCheck.press(p, S + "firecracker"), "Firecracker did not set a mine");
        h.runAfterDelay(30, () -> {
            h.assertTrue(far.getHealth() == health, "The mine went off with nothing near it");
            far.teleportTo(p.getX(), p.getY(), p.getZ() + 1);
        });
        h.runAfterDelay(70, () -> {
            h.assertTrue(far.getHealth() < health, "The mine ignored a hostile creature beside it");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void blastingChargeClearsAFiveByFive(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, KNACK, 0, S + "blasting_charge");
        cube(h, Blocks.STONE);
        h.assertTrue(SkillCheck.press(p, S + "blasting_charge"), "Blasting Charge did not fire");
        h.assertBlockPresent(Blocks.AIR, new BlockPos(8, 2, 4));
        h.assertBlockPresent(Blocks.AIR, new BlockPos(10, 4, 6));
        h.assertBlockPresent(Blocks.STONE, new BlockPos(8, 2, 7));
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void flashPowderBlindsAndSmokeAndMirrorsSpeedsYou(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, KNACK, 0, T + "flash_powder", T + "smoke_and_mirrors");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 6);
        h.assertTrue(SkillCheck.press(p, T + "flash_powder"), "Flash Powder did not fire");
        h.assertTrue(husk.hasEffect(MobEffects.BLINDNESS), "Flash Powder did not blind");
        h.assertTrue(SkillCheck.flagged(husk, "townstead:tinker_dazzled"), "The blinded creature is not marked");
        h.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SPEED), "Smoke and Mirrors gave no Speed");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void springTrapLaunchesAndClockworkHurtsOnTheWayDown(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, KNACK, 30, T + "spring_trap", T + "clockwork");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 6);
        float health = husk.getHealth();
        h.assertTrue(SkillCheck.press(p, T + "spring_trap"), "Spring Trap did not fire");
        h.assertTrue(husk.getDeltaMovement().y > 1.0, "Spring Trap launched at " + husk.getDeltaMovement().y);
        h.assertTrue(husk.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Clockwork applied no Slowness");
        h.runAfterDelay(45, () -> {
            h.assertTrue(Math.abs(health - husk.getHealth() - 4f) < 0.01f, "Clockwork dealt " + (health - husk.getHealth()));
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void springPadLaunchesWhatStepsOnIt(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, KNACK, 30, T + "spring_pad");
        h.assertTrue(SkillCheck.press(p, T + "spring_pad"), "Spring Pad did not fire");
        Cow cow = SkillCheck.dummy(h, EntityType.COW, 8, 1, 2);
        double[] best = {0};
        h.onEachTick(() -> best[0] = Math.max(best[0], cow.getDeltaMovement().y));
        h.runAfterDelay(60, () -> {
            h.assertTrue(best[0] > 0.8, "The pad launched the cow at " + best[0]);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void grapplingLineFlingsTheCaster(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, KNACK, 0, T + "grappling_line");
        h.assertTrue(SkillCheck.press(p, T + "grappling_line"), "Grappling Line did not fire");
        h.assertTrue(p.getDeltaMovement().z > 1.0 && p.getDeltaMovement().y > 0.5, "The fling was " + p.getDeltaMovement());
        h.assertTrue(SkillCheck.resource(p, SkillCheck.STAMINA) == 70, "Grappling Line did not cost 30 stamina");
        h.succeed();
    }
}
