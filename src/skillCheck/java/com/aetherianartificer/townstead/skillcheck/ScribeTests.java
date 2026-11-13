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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Scribe: Diplomat and Journalist. */
@GameTestHolder(SkillCheck.ID)
@PrefixGameTestTemplate(false)
public final class ScribeTests {
    private static final String D = "townstead:scribe/diplomat/";
    private static final String J = "townstead:scribe/journalist/";
    private static final ResourceLocation GOODWILL = ResourceLocation.parse("townstead:goodwill");
    private static final ResourceLocation SCOOP = ResourceLocation.parse("townstead:scoop");
    private static final String A = "townstead:scribe/archivist/";
    private static final ResourceLocation INK = ResourceLocation.parse("townstead:ink");

    private static ChestBlockEntity chestWithPaper(GameTestHelper h) {
        BlockPos at = new BlockPos(10, 2, 5);
        h.setBlock(at, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) h.getBlockEntity(at);
        chest.setItem(3, new ItemStack(Items.PAPER, 20));
        return chest;
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void indexFindsTheContainerThatHoldsTheItemInHand(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, INK, 0, A + "index");
        ChestBlockEntity chest = chestWithPaper(h);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.PAPER));
        h.assertTrue(SkillCheck.press(p, A + "index"), "Index did not fire");
        h.assertTrue(chest.getItem(3).getCount() == 20, "Index took from the chest");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void requisitionBringsOneStackToThePack(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, INK, 30, A + "requisition");
        ChestBlockEntity chest = chestWithPaper(h);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.PAPER));
        h.assertTrue(SkillCheck.press(p, A + "requisition"), "Requisition did not fire");
        h.assertTrue(chest.getItem(3).isEmpty(), "The stack is still in the chest");
        h.assertTrue(p.getInventory().countItem(Items.PAPER) == 21, "The pack holds "
                + p.getInventory().countItem(Items.PAPER) + " paper");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void inkSplashStainsAndStopsTheAimedCreature(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, INK, 0, A + "ink_splash");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 8);
        h.assertTrue(SkillCheck.press(p, A + "ink_splash"), "Ink Splash did not fire");
        h.assertTrue(SkillCheck.flagged(husk, "townstead:archivist_stained"), "The creature is not stained");
        h.assertTrue(Fears.afraid(husk), "The creature did not stand still");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void byTheBookWardsAlliesNearby(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, INK, 30, A + "by_the_book");
        Cow ally = SkillCheck.dummy(h, EntityType.COW, 8, 1, 5);
        h.assertTrue(SkillCheck.press(p, A + "by_the_book"), "By the Book did not fire");
        h.assertTrue(Wards.remaining(ally, "by_the_book") == 10f, "The ally got no 10 point ward");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void oliveBranchMakesHostileCreaturesStandDown(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, GOODWILL, 0, D + "olive_branch");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 6);
        h.assertTrue(SkillCheck.press(p, D + "olive_branch"), "Olive Branch did not fire");
        h.assertTrue(Fears.afraid(husk), "The creature did not stand down");
        h.assertTrue(SkillCheck.resource(p, SkillCheck.STAMINA) == 70, "Olive Branch did not cost 30 stamina");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void ceasefireSetsATruceOnTheCaster(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, GOODWILL, 0, D + "olive_branch", D + "ceasefire");
        SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 6);
        h.assertTrue(SkillCheck.press(p, D + "olive_branch"), "Olive Branch did not fire");
        h.assertTrue(SkillCheck.flagged(p, "townstead:diplomat_truce"), "Ceasefire set no truce");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void tipOffShowsAHostileCreature(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, SCOOP, 0, J + "tip_off");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 12);
        h.assertTrue(SkillCheck.press(p, J + "tip_off"), "Tip-off did not fire");
        // Tip-off picks any one hostile creature within 48 blocks, and other test plots are that close.
        boolean shown = !h.getLevel().getEntitiesOfClass(Zombie.class, husk.getBoundingBox().inflate(60),
                zombie -> zombie.hasEffect(MobEffects.GLOWING)).isEmpty();
        h.assertTrue(shown, "No creature glows");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void scandalTurnsHostileCreaturesOnTheTarget(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, SCOOP, 0, J + "scandal");
        Zombie target = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 8);
        Zombie other = SkillCheck.dummy(h, EntityType.HUSK, 10, 1, 8);
        h.assertTrue(SkillCheck.press(p, J + "scandal"), "Scandal did not fire");
        h.assertTrue(other.getTarget() == target, "The other creature does not attack the target");
        h.assertTrue(SkillCheck.flagged(target, "townstead:journalist_story"), "The target carries no story");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void retractionClearsAlliesAndSlowsHostileCreatures(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, SCOOP, 30, J + "retraction");
        Cow ally = SkillCheck.dummy(h, EntityType.COW, 8, 1, 5);
        ally.addEffect(new MobEffectInstance(MobEffects.POISON, 400));
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 7);
        h.assertTrue(SkillCheck.press(p, J + "retraction"), "Retraction did not fire");
        h.assertFalse(ally.hasEffect(MobEffects.POISON), "The ally is still poisoned");
        h.assertTrue(husk.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && husk.hasEffect(MobEffects.WEAKNESS),
                "The hostile creature is not slowed and weakened");
        h.succeed();
    }
}
