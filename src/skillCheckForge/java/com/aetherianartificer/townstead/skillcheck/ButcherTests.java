package com.aetherianartificer.townstead.skillcheck;

import com.aetherianartificer.townstead.root.ability.ResourceValues;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Butcher: Cleaver, Smokehouse and Tenderizer. Drive needs a meat hammer from another mod and is not played. */
@GameTestHolder(SkillCheck.ID)
@PrefixGameTestTemplate(false)
public final class ButcherTests {
    private static final String C = "minecraft:butcher/cleaver/";
    private static final String S = "minecraft:butcher/smokehouse/";
    private static final String T = "minecraft:butcher/tenderizer/";
    private static final ResourceLocation CUT = new ResourceLocation("townstead:cut");

    private static ServerPlayer withAxe(GameTestHelper h, int cut, String... skills) {
        ServerPlayer p = SkillCheck.ready(h, CUT, cut, skills);
        p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        return p;
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void hackStrikesAndBleeds(GameTestHelper h) {
        ServerPlayer p = withAxe(h, 0, C + "hack");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 4);
        float health = husk.getHealth();
        h.assertTrue(SkillCheck.press(p, C + "hack"), "Hack did not fire");
        float first = health - husk.getHealth();
        h.assertTrue(first > 2.5f && first <= 3f, "Hack dealt " + first);
        h.assertTrue(SkillCheck.flagged(husk, "townstead:bleeding"), "The creature is not marked as bleeding");
        h.runAfterDelay(75, () -> {
            float total = health - husk.getHealth();
            h.assertTrue(total > 5f && total <= 6f, "Hack and its bleed dealt " + total);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void butchersBlockHitsHarderOnABleedingCreature(GameTestHelper h) {
        ServerPlayer p = withAxe(h, 30, C + "butchers_block");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 4);
        com.aetherianartificer.townstead.pheno.state.TimedFlags.set(husk, new ResourceLocation("townstead:bleeding"), 200);
        float health = husk.getHealth();
        h.assertTrue(SkillCheck.press(p, C + "butchers_block"), "Butcher's Block did not fire");
        float dealt = health - husk.getHealth();
        h.assertTrue(dealt > 7f && dealt <= 8f, "Butcher's Block dealt " + dealt + " to a bleeding creature");
        h.assertTrue(SkillCheck.resource(p, CUT) == 0, "Butcher's Block did not cost 30 Cut");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void throwCleaverCutsBrushAndStrikes(GameTestHelper h) {
        ServerPlayer p = withAxe(h, 0, C + "throw_cleaver", C + "lodge");
        h.setBlock(8, 2, 5, Blocks.OAK_LEAVES);
        h.setBlock(8, 3, 6, Blocks.COBWEB);
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 9);
        float health = husk.getHealth();
        h.assertTrue(SkillCheck.press(p, C + "throw_cleaver"), "Throw Cleaver did not fire");
        h.assertBlockPresent(Blocks.AIR, new BlockPos(8, 2, 5));
        h.assertBlockPresent(Blocks.AIR, new BlockPos(8, 3, 6));
        h.assertTrue(husk.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Lodge did not slow the creature");
        h.assertTrue(husk.getHealth() < health, "The thrown cleaver did not hurt the aimed creature: " + husk.getHealth()
                + " invulnerable for " + husk.invulnerableTime);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 260)
    public static void emberCarryTakesFireAndGivesItBack(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, CUT, 0, S + "ember_carry");
        BlockPos furnace = new BlockPos(8, 2, 4);
        h.setBlock(8, 1, 4, Blocks.STONE);
        h.setBlock(furnace, Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.LIT, true));
        h.assertTrue(SkillCheck.press(p, S + "ember_carry"), "Ember Carry did not fire");
        h.assertFalse(h.getBlockState(furnace).getValue(AbstractFurnaceBlock.LIT), "The furnace is still lit");
        h.assertTrue(SkillCheck.flagged(p, "townstead:smokehouse_ember"), "The caster carries no ember");
        h.runAfterDelay(170, () -> {
            h.assertTrue(SkillCheck.press(p, S + "ember_carry"), "Ember Carry did not fire the second time");
            h.assertTrue(h.getBlockState(furnace).getValue(AbstractFurnaceBlock.LIT), "The ember did not light the furnace");
            h.assertFalse(SkillCheck.flagged(p, "townstead:smokehouse_ember"), "The ember was not used up");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void hotCoalsAndFlareUpBurnHostileCreatures(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, CUT, 30, S + "hot_coals", S + "flare_up");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 5);
        h.assertTrue(SkillCheck.press(p, S + "hot_coals"), "Hot Coals did not fire");
        h.assertTrue(SkillCheck.resource(p, CUT) == 0, "Hot Coals did not cost 30 Cut");
        h.assertTrue(husk.isOnFire(), "Flare Up did not set the creature on fire");
        String block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(
                h.getBlockState(new BlockPos(8, 1, 5)).getBlock()).toString();
        h.assertTrue(block.equals("townstead:haze"), "No coals under the creature: " + block);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void smokeOutRevealsTheInvisible(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, CUT, 30, S + "smoke_out");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 6);
        husk.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 600));
        h.assertTrue(SkillCheck.press(p, S + "smoke_out"), "Smoke Out did not fire");
        h.assertFalse(husk.hasEffect(MobEffects.INVISIBILITY), "The creature is still invisible");
        h.assertTrue(husk.hasEffect(MobEffects.GLOWING), "The creature does not glow");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void heaveThrowsTheAimedAnimal(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, CUT, 0, T + "heave");
        Cow cow = SkillCheck.dummy(h, EntityType.COW, 8, 1, 4);
        float health = cow.getHealth();
        h.assertTrue(SkillCheck.press(p, T + "heave"), "Heave did not fire");
        h.assertTrue(cow.getDeltaMovement().z > 1.0 && cow.getDeltaMovement().y > 0.3, "The throw was " + cow.getDeltaMovement());
        h.runAfterDelay(30, () -> {
            h.assertTrue(cow.getHealth() == health, "The thrown animal was hurt");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void tenderizeKeepsTheBeatAndBoneBreakerBreaks(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, CUT, 30, T + "bone_breaker", T + "rhythm", T + "tenderize");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 4);
        float health = husk.getHealth();
        h.assertTrue(SkillCheck.press(p, T + "tenderize"), "Tenderize did not fire");
        float first = health - husk.getHealth();
        h.assertTrue(first > 2.5f && first <= 3f, "Tenderize dealt " + first);
        h.assertTrue(husk.hasEffect(MobEffects.WEAKNESS), "Tenderize applied no Weakness");
        h.assertTrue(SkillCheck.flagged(p, "townstead:tenderizer_beat1"), "Rhythm counted no beat");
        SkillCheck.learn(p, T + "bone_breaker");
        husk.invulnerableTime = 0;
        float before = husk.getHealth();
        h.assertTrue(SkillCheck.press(p, T + "bone_breaker"), "Bone Breaker did not fire");
        float second = before - husk.getHealth();
        h.assertTrue(second > 7f && second <= 8f, "Bone Breaker dealt " + second);
        h.assertTrue(ResourceValues.get(p, CUT) == 0, "Bone Breaker did not cost 30 Cut");
        h.succeed();
    }
}
