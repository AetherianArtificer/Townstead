package com.aetherianartificer.townstead.skillcheck;

import com.aetherianartificer.townstead.pheno.combat.Fears;
import com.aetherianartificer.townstead.pheno.combat.Wards;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Tailor: Clothier, Needle and Weaver. */
@GameTestHolder(SkillCheck.ID)
@PrefixGameTestTemplate(false)
public final class TailorTests {
    private static final String C = "townstead:tailor/clothier/";
    private static final String N = "townstead:tailor/needle/";
    private static final String W = "townstead:tailor/weaver/";
    private static final ResourceLocation STITCH = new ResourceLocation("townstead:stitch");

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void masqueradeMakesHostileCreaturesStandStill(GameTestHelper h) {
        if (SkillCheck.missing(h, C + "masquerade")) return;
        ServerPlayer p = SkillCheck.ready(h, STITCH, 0, C + "masquerade");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 6);
        h.assertTrue(SkillCheck.press(p, C + "masquerade"), "Masquerade did not fire");
        h.assertTrue(Fears.afraid(husk), "The creature did not stand still");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void wardrobeSwapsWornArmorWithThePack(GameTestHelper h) {
        if (SkillCheck.missing(h, C + "wardrobe")) return;
        ServerPlayer p = SkillCheck.ready(h, STITCH, 30, C + "wardrobe");
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        p.getInventory().items.set(5, new ItemStack(Items.IRON_CHESTPLATE));
        h.assertTrue(SkillCheck.press(p, C + "wardrobe"), "Wardrobe did not fire");
        h.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "The iron chestplate is not worn");
        h.assertTrue(p.getInventory().items.get(5).is(Items.LEATHER_CHESTPLATE),
                "The leather chestplate is not in the pack");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void wardrobeMalfunctionTripsAndLoosens(GameTestHelper h) {
        if (SkillCheck.missing(h, C + "wardrobe_malfunction")) return;
        ServerPlayer p = SkillCheck.ready(h, STITCH, 30, C + "wardrobe_malfunction");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 8);
        h.assertTrue(SkillCheck.press(p, C + "wardrobe_malfunction"), "Wardrobe Malfunction did not fire");
        h.assertTrue(husk.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "The creature did not trip");
        h.assertTrue(SkillCheck.flagged(husk, "townstead:clothier_loose"), "The armor is not loose");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void shearCleanShearsTheSheepNearby(GameTestHelper h) {
        if (SkillCheck.missing(h, N + "shear_clean")) return;
        ServerPlayer p = SkillCheck.ready(h, STITCH, 0, N + "shear_clean");
        p.getInventory().items.set(5, new ItemStack(Items.SHEARS));
        Sheep sheep = SkillCheck.dummy(h, EntityType.SHEEP, 8, 1, 4);
        h.assertTrue(SkillCheck.press(p, N + "shear_clean"), "Shear Clean did not fire");
        h.assertTrue(sheep.isSheared(), "The sheep was not shorn");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void snipCutsAndStripsBeneficialEffects(GameTestHelper h) {
        if (SkillCheck.missing(h, N + "snip")) return;
        ServerPlayer p = SkillCheck.ready(h, STITCH, 0, N + "snip");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 5);
        husk.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 400));
        husk.invulnerableTime = 0;
        float health = husk.getHealth();
        h.assertTrue(SkillCheck.press(p, N + "snip"), "Snip did not fire");
        float dealt = health - husk.getHealth();
        h.assertTrue(dealt > 2.5f && dealt <= 3f, "Snip dealt " + dealt);
        h.assertFalse(husk.hasEffect(MobEffects.DAMAGE_BOOST), "The creature kept its Strength");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void sewUpWardsTheAimedAlly(GameTestHelper h) {
        if (SkillCheck.missing(h, N + "sew_up")) return;
        ServerPlayer p = SkillCheck.ready(h, STITCH, 30, N + "sew_up");
        Cow ally = SkillCheck.dummy(h, EntityType.COW, 8, 1, 5);
        h.assertTrue(SkillCheck.press(p, N + "sew_up"), "Sew Up did not fire");
        h.assertTrue(Wards.remaining(ally, "sew_up") == 8f, "The ally got no 8 point ward");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void lifelineSparesFalls(GameTestHelper h) {
        if (SkillCheck.missing(h, W + "lifeline")) return;
        ServerPlayer p = SkillCheck.ready(h, STITCH, 0, W + "lifeline");
        h.assertTrue(SkillCheck.press(p, W + "lifeline"), "Lifeline did not fire");
        h.assertTrue(SkillCheck.flagged(p, "townstead:weaver_lifeline"), "No lifeline is tied");
        float taken = SkillCheck.struck(p, p.damageSources().fall(), 6);
        h.assertTrue(taken == 0f, "A fall still dealt " + taken);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void bindHoldsTheAimedCreature(GameTestHelper h) {
        if (SkillCheck.missing(h, W + "bind")) return;
        ServerPlayer p = SkillCheck.ready(h, STITCH, 0, W + "bind");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 8);
        h.assertTrue(SkillCheck.press(p, W + "bind"), "Bind did not fire");
        MobEffectInstance slow = husk.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        h.assertTrue(slow != null && slow.getAmplifier() == 6, "The creature can still walk");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void cocoonWrapsTheAimedCreature(GameTestHelper h) {
        if (SkillCheck.missing(h, W + "cocoon")) return;
        ServerPlayer p = SkillCheck.ready(h, STITCH, 30, W + "cocoon");
        Zombie husk = SkillCheck.dummy(h, EntityType.HUSK, 8, 1, 8);
        h.assertTrue(SkillCheck.press(p, W + "cocoon"), "Cocoon did not fire");
        h.assertTrue(husk.hasEffect(MobEffects.WEAKNESS), "The creature can still strike");
        h.assertTrue(Wards.remaining(husk, "cocoon") > 0f, "The cocoon spares nothing");
        h.succeed();
    }
}
