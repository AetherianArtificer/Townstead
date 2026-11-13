package com.aetherianartificer.townstead.skillcheck;

import com.aetherianartificer.townstead.pheno.combat.Wards;
import com.aetherianartificer.townstead.root.ability.AbilityToggles;
import com.aetherianartificer.townstead.root.ability.ResourceValues;
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

/** Armorer, Field Mender and Bulwark paths. A cow stands in for an ally: it is not hostile and can be hurt. */
@GameTestHolder(SkillCheck.ID)
@PrefixGameTestTemplate(false)
public final class ArmorerTests {
    private static final String M = "minecraft:armorer/field_mender/";
    private static final String B = "minecraft:armorer/bulwark/";
    private static final ResourceLocation TEMPER = new ResourceLocation("townstead:temper");

    private static int temper(ServerPlayer p) {
        return SkillCheck.resource(p, TEMPER);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void paddingWardsAlliesAndReinforceBurstsOnBreak(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, TEMPER, 0, M + "padding", M + "reinforce", M + "masterwork");
        Cow ally = SkillCheck.dummy(h, EntityType.COW, 8, 1, 5);
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 6);
        h.assertTrue(SkillCheck.press(p, M + "padding"), "Padding did not fire");
        h.assertTrue(SkillCheck.resource(p, SkillCheck.STAMINA) == 70, "Padding did not cost 30 stamina");
        h.assertTrue(Wards.remaining(ally, "padding") == 4f, "The ally got no 4 point ward");
        h.assertTrue(Wards.remaining(p, "padding") == 0f, "Masterwork still lets Padding cover the caster");
        float health = ally.getHealth();
        ally.hurt(ally.damageSources().mobAttack(husk), 6);
        h.assertTrue(Math.abs(health - ally.getHealth() - 2f) < 0.01f, "The ward spared " + (6 - (health - ally.getHealth())));
        h.assertTrue(husk.getDeltaMovement().lengthSqr() > 0.1, "Reinforce did not knock the attacker back");
        h.assertTrue(temper(p) == 10, "Masterwork gave " + temper(p) + " Temper for a broken ward");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void temperedSteelWardsTheAimedAllyAndBurstsWhenItEnds(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, TEMPER, 30, M + "tempered_steel", M + "fireguard");
        Cow ally = SkillCheck.dummy(h, EntityType.COW, 8, 1, 5);
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 7);
        ally.setRemainingFireTicks(200);
        h.assertTrue(SkillCheck.press(p, M + "tempered_steel"), "Tempered Steel did not fire");
        h.assertTrue(temper(p) == 0, "Tempered Steel did not cost 30 Temper");
        h.assertTrue(Wards.remaining(ally, "tempered_steel") == 12f, "The aimed ally got no 12 point ward");
        h.assertFalse(ally.isOnFire(), "Fireguard did not put out the fire");
        h.assertTrue(Wards.remaining(ally, "fireguard") > 0f, "Fireguard placed no fire ward");
        float cow = ally.getHealth();
        ally.hurt(ally.damageSources().mobAttack(husk), 4);
        h.assertTrue(ally.getHealth() == cow, "The ward did not spare a 4 point hit");
        float before = husk.getHealth();
        // The ward lasts 15 seconds. It spared 4, so it bursts for 2 when it ends.
        h.runAfterDelay(320, () -> {
            h.assertTrue(Math.abs(before - husk.getHealth() - 2f) < 0.01f,
                    "The ending ward burst for " + (before - husk.getHealth()));
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void reforgeNeedsAnAnvilAndRepairsTheHeldItem(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, TEMPER, 60, M + "reforge", M + "hammer_out");
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        pick.setDamageValue(500);
        pick.setRepairCost(7);
        p.setItemSlot(EquipmentSlot.MAINHAND, pick);
        h.assertFalse(SkillCheck.press(p, M + "reforge"), "Reforge fired with no anvil");
        h.assertTrue(temper(p) == 60, "A refused Reforge cost Temper");
        h.setBlock(9, 1, 2, Blocks.ANVIL);
        h.assertTrue(SkillCheck.press(p, M + "reforge"), "Reforge did not fire at an anvil");
        ItemStack held = p.getMainHandItem();
        h.assertTrue(held.getDamageValue() == 200, "Reforge left " + held.getDamageValue() + " damage");
        h.assertTrue(held.getBaseRepairCost() == 0,
                "Hammer Out left the anvil cost");
        h.assertTrue(temper(p) == 30, "Reforge did not cost 30 Temper");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void fieldRepairMendsTheAimedAllyAndMendTheLineSpreads(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, TEMPER, 0, M + "field_repair", M + "mend_the_line");
        Cow aimed = SkillCheck.dummy(h, EntityType.COW, 8, 1, 5);
        Cow near = SkillCheck.dummy(h, EntityType.COW, 11, 1, 6);
        for (Cow cow : new Cow[]{aimed, near}) {
            ItemStack plate = new ItemStack(Items.IRON_CHESTPLATE);
            plate.setDamageValue(100);
            cow.setItemSlot(EquipmentSlot.CHEST, plate);
        }
        h.assertTrue(SkillCheck.press(p, M + "field_repair"), "Field Repair did not fire");
        h.assertTrue(aimed.getItemBySlot(EquipmentSlot.CHEST).getDamageValue() == 80, "The aimed ally was not repaired");
        h.assertTrue(near.getItemBySlot(EquipmentSlot.CHEST).getDamageValue() == 80, "Mend the Line did not spread");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void guardMovesDamageToTheGuardian(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, TEMPER, 0, B + "guard", B + "aegis");
        Cow ally = SkillCheck.dummy(h, EntityType.COW, 8, 1, 5);
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 7);
        h.assertTrue(SkillCheck.press(p, B + "guard"), "Guard did not fire");
        h.assertTrue(SkillCheck.resource(p, SkillCheck.STAMINA) == 80, "Guard did not cost 20 stamina");
        float health = ally.getHealth();
        ally.hurt(ally.damageSources().mobAttack(husk), 8);
        // With Aegis, three quarters go to the guardian.
        h.assertTrue(Math.abs(health - ally.getHealth() - 2f) < 0.01f, "The guarded ally lost " + (health - ally.getHealth()));
        h.assertTrue(temper(p) == 2, "Aegis gave " + temper(p) + " Temper for a shared hit");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void villagerGuardTakesHalfOfAnAllysDamage(GameTestHelper h) {
        SkillCheck.floor(h);
        var guardian = SkillCheck.villager(h, 8, 1, 3);
        SkillCheck.learn(guardian, B + "guard");
        ResourceValues.set(guardian, SkillCheck.STAMINA, 100);
        Cow ally = SkillCheck.dummy(h, EntityType.COW, 8, 1, 5);
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 12, 1, 12);
        ally.hurt(ally.damageSources().mobAttack(husk), 1);
        h.runAfterDelay(80, () -> {
            float cow = ally.getHealth();
            float own = guardian.getHealth();
            ally.invulnerableTime = 0;
            ally.hurt(ally.damageSources().mobAttack(husk), 8);
            h.assertTrue(Math.abs(cow - ally.getHealth() - 4f) < 0.01f, "The guarded ally lost " + (cow - ally.getHealth()));
            h.assertTrue(own - guardian.getHealth() > 0f, "The guardian took nothing");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void ironWallCoversAlliesDrainsAndReleases(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, TEMPER, 20, B + "iron_wall", B + "last_bastion");
        Cow ally = SkillCheck.dummy(h, EntityType.COW, 8, 1, 5);
        ally.setHealth(2f);
        ResourceLocation wall = new ResourceLocation(B + "iron_wall");
        h.assertTrue(SkillCheck.press(p, B + "iron_wall"), "Iron Wall did not switch on");
        h.onEachTick(() -> SkillCheck.tick(p));
        h.runAfterDelay(45, () -> {
            h.assertTrue(AbilityToggles.isOn(p, wall), "Iron Wall let go early");
            var resistance = ally.getEffect(MobEffects.DAMAGE_RESISTANCE);
            h.assertTrue(resistance != null && resistance.getAmplifier() == 1, "The ally has no Resistance II");
            h.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "The wall does not slow its holder");
            h.assertTrue(Wards.remaining(ally, "last_bastion") == 8f, "Last Bastion placed no ward on a dying ally");
            h.assertTrue(temper(p) < 20, "Iron Wall drained nothing");
        });
        h.runAfterDelay(160, () -> {
            h.assertFalse(AbilityToggles.isOn(p, wall), "Iron Wall stayed up with no Temper");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void douseExtinguishesAndBlastPlateSparesExplosions(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, TEMPER, 0, B + "douse", B + "blast_plate");
        Cow ally = SkillCheck.dummy(h, EntityType.COW, 8, 1, 5);
        ally.setRemainingFireTicks(200);
        h.assertTrue(SkillCheck.press(p, B + "douse"), "Douse did not fire");
        h.assertFalse(ally.isOnFire(), "Douse left the ally burning");
        h.assertTrue(Wards.remaining(ally, "douse") == 8f, "Douse placed no ward");
        float health = ally.getHealth();
        ally.hurt(ally.damageSources().explosion(null, null), 5);
        h.assertTrue(ally.getHealth() == health, "Blast Plate did not spare an explosion");
        ally.invulnerableTime = 0;
        ally.hurt(ally.damageSources().generic(), 3);
        h.assertTrue(ally.getHealth() < health, "The Douse ward spared plain damage");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void standFastPushesHostilesAndLivingShieldTakesBlasts(GameTestHelper h) {
        ServerPlayer p = SkillCheck.ready(h, TEMPER, 40, B + "stand_fast", B + "living_shield");
        Cow ally = SkillCheck.dummy(h, EntityType.COW, 9, 1, 2);
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 3);
        h.assertTrue(SkillCheck.press(p, B + "stand_fast"), "Stand Fast did not switch on");
        h.onEachTick(() -> SkillCheck.tick(p));
        h.runAfterDelay(30, () -> {
            h.assertTrue(AbilityToggles.isOn(p, new ResourceLocation(B + "stand_fast")), "Stand Fast let go early");
            h.assertTrue(temper(p) < 40, "Stand Fast drained nothing");
            h.assertTrue(husk.getDeltaMovement().horizontalDistanceSqr() > 0.01,
                    "The hostile creature was not pushed out: " + husk.getDeltaMovement() + " at " + husk.distanceTo(p));
            float health = ally.getHealth();
            ally.hurt(ally.damageSources().explosion(null, null), 6);
            h.assertTrue(ally.getHealth() == health, "Living Shield let an explosion through");
            h.succeed();
        });
    }
}
