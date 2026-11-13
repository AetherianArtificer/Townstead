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
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Mason: Rampart, Roadwright and Stonewarden. The caster stands at (8,1,2) and faces +z. */
@GameTestHolder(SkillCheck.ID)
@PrefixGameTestTemplate(false)
public final class MasonTests {
    private static final String R = "minecraft:mason/rampart/";
    private static final String W = "minecraft:mason/roadwright/";
    private static final String S = "minecraft:mason/stonewarden/";
    private static final ResourceLocation MORTAR = ResourceLocation.parse("townstead:mortar");

    private static boolean haze(GameTestHelper h, int x, int y, int z) {
        return BuiltInRegistries.BLOCK.getKey(h.getBlockState(new BlockPos(x, y, z)).getBlock()).toString()
                .equals("townstead:haze");
    }

    private static int mortar(ServerPlayer p) {
        return SkillCheck.resource(p, MORTAR);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void barricadeRaisesStoneInFront(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, MORTAR, 0, R + "barricade");
        h.assertTrue(SkillCheck.press(p, R + "barricade"), "Barricade did not fire");
        h.assertTrue(haze(h, 8, 1, 3) && haze(h, 8, 2, 3), "No stone 2 high in front of the caster");
        h.assertFalse(haze(h, 9, 1, 3), "A plain Barricade is wider than 1 block");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void scaffoldLiftsTheCasterThreeBlocks(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, MORTAR, 0, R + "scaffold");
        double y = p.getY();
        h.assertTrue(SkillCheck.press(p, R + "scaffold"), "Scaffold did not fire");
        h.assertTrue(Math.abs(p.getY() - y - 3) < 0.01, "Scaffold lifted the caster " + (p.getY() - y));
        h.assertTrue(haze(h, 8, 1, 2) && haze(h, 8, 2, 2) && haze(h, 8, 3, 2), "No pillar under the caster");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void footbridgeSpansFiveBlocksAhead(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, MORTAR, 30, R + "footbridge");
        for (int z = 3; z <= 9; z++) h.setBlock(8, 0, z, Blocks.AIR);
        h.assertTrue(SkillCheck.press(p, R + "footbridge"), "Footbridge did not fire");
        h.assertTrue(mortar(p) == 0, "Footbridge did not cost 30 Mortar");
        h.assertTrue(haze(h, 8, 0, 3) && haze(h, 8, 0, 7), "The bridge does not cover blocks 1 to 5 ahead");
        h.assertFalse(haze(h, 8, 0, 8), "A plain Footbridge is longer than 5 blocks");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void redoubtRingsTheCasterAndSallyForthClearsIt(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, MORTAR, 30, R + "redoubt", R + "sally_forth");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 3);
        h.assertTrue(SkillCheck.press(p, R + "redoubt"), "Redoubt did not fire");
        h.assertTrue(haze(h, 8, 2, 4) && haze(h, 10, 2, 2) && haze(h, 6, 2, 2), "No ring of walls 2 blocks out");
        h.assertTrue(husk.getDeltaMovement().horizontalDistanceSqr() > 0.1, "Sally Forth did not throw the creature out");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void paveTurnsDirtAheadIntoPath(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, MORTAR, 0, W + "pave");
        p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SHOVEL));
        for (int z = 3; z <= 9; z++) h.setBlock(8, 0, z, Blocks.DIRT);
        h.assertTrue(SkillCheck.press(p, W + "pave"), "Pave did not fire");
        h.assertBlockPresent(Blocks.DIRT_PATH, new BlockPos(8, 0, 3));
        h.assertBlockPresent(Blocks.DIRT_PATH, new BlockPos(8, 0, 7));
        h.assertBlockPresent(Blocks.DIRT, new BlockPos(8, 0, 8));
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void tollgateSlowsHostileCreaturesOnIt(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, MORTAR, 0, W + "tollgate", W + "toll");
        // A haze cell acts on what moves through it, so this creature keeps its AI.
        Zombie husk = h.spawn(EntityType.HUSK, new BlockPos(8, 1, 5));
        float health = husk.getHealth();
        h.assertTrue(SkillCheck.press(p, W + "tollgate"), "Tollgate did not fire");
        h.assertTrue(haze(h, 8, 1, 5) && haze(h, 6, 1, 5) && haze(h, 10, 1, 7), "No chalked patch ahead of the caster");
        h.runAfterDelay(50, () -> {
            h.assertTrue(husk.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "The Tollgate does not slow");
            h.assertTrue(husk.getHealth() < health, "Toll dealt no damage");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void roadRunDashesSixBlocksOffTheRoad(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, MORTAR, 30, W + "road_run");
        double start = p.getZ();
        h.assertTrue(SkillCheck.press(p, W + "road_run"), "Road Run did not fire");
        h.assertTrue(mortar(p) == 0, "Road Run did not cost 30 Mortar");
        h.onEachTick(() -> SkillCheck.tick(p));
        h.runAfterDelay(20, () -> {
            double moved = p.getZ() - start;
            h.assertTrue(moved > 4 && moved < 8, "Road Run covered " + moved + " blocks off the road");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void boltTheDoorShutsGatesWithBarTheWindows(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, MORTAR, 0, S + "bolt_the_door", S + "bar_the_windows");
        BlockPos gate = new BlockPos(10, 1, 4);
        h.setBlock(gate, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.OPEN, true));
        h.assertTrue(SkillCheck.press(p, S + "bolt_the_door"), "Bolt the Door did not fire");
        h.assertFalse(h.getBlockState(gate).getValue(FenceGateBlock.OPEN), "The fence gate stayed open");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void murderHoleHitsHarderInAGap(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, MORTAR, 0, S + "murder_hole");
        Zombie open = SkillCheck.dummy(h, EntityType.HUSK, 10, 1, 3);
        Zombie boxed = SkillCheck.dummy(h, EntityType.HUSK, 7, 1, 5);
        h.setBlock(6, 2, 5, Blocks.STONE);
        h.setBlock(8, 2, 5, Blocks.STONE);
        float a = open.getHealth();
        float b = boxed.getHealth();
        h.assertTrue(SkillCheck.press(p, S + "murder_hole"), "Murder Hole did not fire");
        h.assertTrue(a - open.getHealth() > 3.5f && a - open.getHealth() <= 4f, "In the open: " + (a - open.getHealth()));
        h.assertTrue(b - boxed.getHealth() > 7f && b - boxed.getHealth() <= 8f, "In a gap: " + (b - boxed.getHealth()));
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void holdFastCoversOnlyThoseBesideAWall(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, MORTAR, 30, S + "hold_fast", S + "garrison");
        Cow walled = SkillCheck.dummy(h, EntityType.COW, 8, 1, 4);
        Cow loose = SkillCheck.dummy(h, EntityType.COW, 5, 1, 4);
        h.setBlock(9, 2, 4, Blocks.STONE);
        h.setBlock(9, 2, 2, Blocks.STONE);
        h.assertTrue(SkillCheck.press(p, S + "hold_fast"), "Hold Fast did not fire");
        h.assertTrue(mortar(p) == 0, "Hold Fast did not cost 30 Mortar");
        var resistance = walled.getEffect(MobEffects.DAMAGE_RESISTANCE);
        h.assertTrue(resistance != null && resistance.getAmplifier() == 1, "The ally beside a wall has no Resistance II");
        h.assertTrue(p.hasEffect(MobEffects.DAMAGE_RESISTANCE), "The caster beside a wall has no Resistance");
        h.assertFalse(loose.hasEffect(MobEffects.DAMAGE_RESISTANCE), "An ally in the open gained Resistance");
        h.assertTrue(Wards.remaining(walled, "garrison") > 0f, "Garrison placed nothing on the ally");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void braceTheBeamsWardsAgainstBlasts(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, MORTAR, 30, S + "brace_the_beams");
        Cow ally = SkillCheck.dummy(h, EntityType.COW, 8, 1, 4);
        h.assertTrue(SkillCheck.press(p, S + "brace_the_beams"), "Brace the Beams did not fire");
        h.assertTrue(Wards.remaining(ally, "braced") == 12f, "The ally got no 12 point ward");
        float health = ally.getHealth();
        ally.hurt(ally.damageSources().explosion(null, null), 6);
        h.assertTrue(ally.getHealth() == health, "The ward did not spare a blast");
        h.succeed();
    }
}
