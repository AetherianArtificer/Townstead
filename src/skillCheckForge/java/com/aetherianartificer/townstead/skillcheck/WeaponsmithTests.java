package com.aetherianartificer.townstead.skillcheck;

import com.aetherianartificer.townstead.pheno.combat.Fears;
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
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Weaponsmith: Duelist, Headsman and Honer. */
@GameTestHolder(SkillCheck.ID)
@PrefixGameTestTemplate(false)
public final class WeaponsmithTests {
    private static final String D = "minecraft:weaponsmith/duelist/";
    private static final String H = "minecraft:weaponsmith/headsman/";
    private static final String O = "minecraft:weaponsmith/honer/";
    private static final ResourceLocation EDGE = new ResourceLocation("townstead:edge");

    private static ServerPlayer armed(GameTestHelper h, int edge, net.minecraft.world.item.Item weapon, String... skills) {
        ServerPlayer p = SkillCheck.ready(h, EDGE, edge, skills);
        p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
        return p;
    }

    private static int edge(ServerPlayer p) {
        return SkillCheck.resource(p, EDGE);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void parrySparesOneHitAndAnswersIt(GameTestHelper h) {
        ServerPlayer p = armed(h, 0, Items.IRON_SWORD, D + "parry", D + "lunge", D + "counter");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 4);
        float health = husk.getHealth();
        h.assertTrue(SkillCheck.press(p, D + "parry"), "Parry did not fire");
        float left = SkillCheck.struck(p, p.damageSources().mobAttack(husk), 6);
        h.assertTrue(left == 0f, "The parried hit still dealt " + left);
        // Lunge strikes for 6. The husk's armor takes a little of it.
        h.assertTrue(health - husk.getHealth() > 5f && health - husk.getHealth() <= 6f,
                "The attacker lost " + (health - husk.getHealth()));
        h.assertTrue(husk.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "The attacker was not staggered");
        h.assertTrue(edge(p) == 10, "A parried hit gave " + edge(p) + " Edge");
        h.assertTrue(SkillCheck.flagged(p, "townstead:duelist_counter"), "Counter was not readied");
        left = SkillCheck.struck(p, p.damageSources().mobAttack(husk), 6);
        h.assertTrue(left == 6f, "Parry spared a second hit");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void challengeMarksOneCreatureAndPaysWhenItDies(GameTestHelper h) {
        ServerPlayer p = armed(h, 30, Items.IRON_SWORD, D + "footwork", D + "masters_duel", D + "challenge");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 5);
        h.assertTrue(SkillCheck.press(p, D + "challenge"), "Challenge did not fire");
        h.assertTrue(edge(p) == 0, "Challenge did not cost 30 Edge");
        h.assertTrue(SkillCheck.flagged(husk, "townstead:duelist_challenged"), "The aimed creature was not challenged");
        float dealt = SkillCheck.struck(husk, husk.damageSources().playerAttack(p), 4);
        h.assertTrue(Math.abs(dealt - 5f) < 0.01f, "A hit on the challenged creature dealt " + dealt);
        float taken = SkillCheck.struck(p, p.damageSources().mobAttack(husk), 4);
        h.assertTrue(Math.abs(taken - 5f) < 0.01f, "A hit from the challenged creature dealt " + taken);
        SkillCheck.learn(p, D + "footwork");   // equip the other tool of the column
        h.assertTrue(SkillCheck.press(p, D + "footwork"), "Footwork did not fire");
        h.runAfterDelay(5, () -> {
            h.assertTrue(SkillCheck.resource(p, SkillCheck.STAMINA) == 100, "Footwork cost stamina during a Challenge");
            husk.hurt(husk.damageSources().playerAttack(p), 100);
            h.assertTrue(edge(p) == 30, "Master's Duel gave " + edge(p) + " Edge for the kill");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void disarmDropsTheWeaponAndSweepsWithTheUpgrade(GameTestHelper h) {
        ServerPlayer p = armed(h, 30, Items.IRON_SWORD, D + "disarm", D + "sweeping_disarm");
        Zombie aimed = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 5);
        Zombie beside = SkillCheck.dummy(h, EntityType.HUSK, 10, 1, 5);
        aimed.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SHOVEL));
        beside.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SHOVEL));
        h.assertTrue(SkillCheck.press(p, D + "disarm"), "Disarm did not fire");
        h.assertTrue(aimed.getMainHandItem().isEmpty(), "The aimed creature kept its weapon");
        h.assertTrue(aimed.hasEffect(MobEffects.WEAKNESS), "Disarm applied no Weakness");
        h.assertTrue(beside.getMainHandItem().isEmpty(), "Sweeping Disarm did not reach the creature beside");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void footworkDashesFourBlocks(GameTestHelper h) {
        ServerPlayer p = armed(h, 0, Items.IRON_SWORD, D + "footwork");
        double start = p.getZ();
        h.assertTrue(SkillCheck.press(p, D + "footwork"), "Footwork did not fire");
        h.assertTrue(SkillCheck.resource(p, SkillCheck.STAMINA) == 80, "Footwork did not cost 20 stamina");
        h.onEachTick(() -> SkillCheck.tick(p));
        h.runAfterDelay(15, () -> {
            double moved = p.getZ() - start;
            h.assertTrue(moved > 2.5 && moved < 5.5, "Footwork covered " + moved + " blocks");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void fellCutsTheAimedLogAndThoseAbove(GameTestHelper h) {
        ServerPlayer p = armed(h, 0, Items.IRON_AXE, H + "fell");
        for (int y = 1; y <= 5; y++) h.setBlock(8, y, 4, Blocks.OAK_LOG);
        h.assertTrue(SkillCheck.press(p, H + "fell"), "Fell did not fire");
        h.assertTrue(SkillCheck.resource(p, SkillCheck.STAMINA) == 75, "Fell did not cost 25 stamina");
        h.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(8, 1, 4));
        for (int y = 2; y <= 5; y++) h.assertBlockPresent(Blocks.AIR, new BlockPos(8, y, 4));
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void executeSparesTheHealthyAndSpendsTheEdge(GameTestHelper h) {
        ServerPlayer p = armed(h, 30, Items.IRON_AXE, H + "execute");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 4);
        float health = husk.getHealth();
        h.assertTrue(SkillCheck.press(p, H + "execute"), "Execute did not fire");
        h.assertTrue(husk.getHealth() == health, "Execute hurt a healthy creature");
        h.assertTrue(edge(p) == 0, "A wasted Execute kept its Edge");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void executeKillsTheWoundedAndDreadScattersTheRest(GameTestHelper h) {
        ServerPlayer p = armed(h, 30, Items.IRON_AXE, H + "execute", H + "dread_presence");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 4);
        Zombie witness = h.spawn(EntityType.HUSK, new BlockPos(11, 1, 5));
        husk.setHealth(5f);
        h.assertTrue(SkillCheck.press(p, H + "execute"), "Execute did not fire");
        h.assertTrue(husk.isDeadOrDying(), "Execute left a wounded creature at " + husk.getHealth());
        h.assertTrue(Fears.afraid(witness), "Dread Presence did not scatter the witness");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void cleaveThroughHitsHarderBelowThirtyPercent(GameTestHelper h) {
        ServerPlayer p = armed(h, 0, Items.IRON_AXE, H + "cleave_through", H + "judge");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 4);
        float health = husk.getHealth();
        h.assertTrue(SkillCheck.press(p, H + "cleave_through"), "Cleave Through did not fire");
        h.assertTrue(health - husk.getHealth() > 2.5f && health - husk.getHealth() <= 3f, "A healthy creature lost " + (health - husk.getHealth()));
        com.aetherianartificer.townstead.root.ability.ResourceValues.set(p, EDGE, 30);
        h.assertTrue(SkillCheck.press(p, H + "judge"), "Judge did not fire");
        h.assertTrue(SkillCheck.flagged(husk, "townstead:headsman_judged"), "Judge did not mark the creature");
        h.assertTrue(husk.hasEffect(MobEffects.GLOWING), "A judged creature does not glow");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void sharpenArmsGivesStrengthAndWarbandMoreUpClose(GameTestHelper h) {
        ServerPlayer p = armed(h, 0, Items.IRON_SWORD, O + "sharpen_arms", O + "warband");
        Cow close = SkillCheck.dummy(h, EntityType.COW, 8, 1, 4);
        Cow far = SkillCheck.dummy(h, EntityType.COW, 8, 1, 8);
        h.assertTrue(SkillCheck.press(p, O + "sharpen_arms"), "Sharpen Arms did not fire");
        h.assertTrue(p.hasEffect(MobEffects.DAMAGE_BOOST), "The caster has no Strength");
        h.assertTrue(far.getEffect(MobEffects.DAMAGE_BOOST) != null && far.getEffect(MobEffects.DAMAGE_BOOST).getAmplifier() == 0,
                "An ally at 5 blocks has no Strength I");
        h.assertTrue(close.getEffect(MobEffects.DAMAGE_BOOST) != null && close.getEffect(MobEffects.DAMAGE_BOOST).getAmplifier() == 1,
                "Warband gave no Strength II to an ally within 3 blocks");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void keenStrikeWithPerfectEdgeSpendsEverything(GameTestHelper h) {
        ServerPlayer p = armed(h, 80, Items.IRON_SWORD, O + "keen_strike", O + "perfect_edge");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 4);
        float health = husk.getHealth();
        h.assertTrue(SkillCheck.press(p, O + "keen_strike"), "Keen Strike did not fire");
        // 80 Edge adds 8 damage to the 6. The husk's armor takes a little of it.
        h.assertTrue(health - husk.getHealth() > 13f && health - husk.getHealth() <= 14f, "Keen Strike dealt " + (health - husk.getHealth()));
        h.assertTrue(edge(p) == 0, "Perfect Edge left " + edge(p) + " Edge");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void whetNeedsAGrindstoneAndAddsDamage(GameTestHelper h) {
        ServerPlayer p = armed(h, 30, Items.IRON_SWORD, O + "whet");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 4);
        h.assertFalse(SkillCheck.press(p, O + "whet"), "Whet fired with no grindstone");
        h.setBlock(9, 1, 2, Blocks.GRINDSTONE);
        h.assertTrue(SkillCheck.press(p, O + "whet"), "Whet did not fire at a grindstone");
        h.assertTrue(edge(p) == 0, "Whet did not cost 30 Edge");
        h.onEachTick(() -> SkillCheck.tick(p));
        h.runAfterDelay(25, () -> {
            float dealt = SkillCheck.struck(husk, husk.damageSources().playerAttack(p), 4);
            h.assertTrue(Math.abs(dealt - 6f) < 0.01f, "A whetted hit dealt " + dealt);
            h.succeed();
        });
    }
}
