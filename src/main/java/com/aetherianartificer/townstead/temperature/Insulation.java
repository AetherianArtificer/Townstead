package com.aetherianartificer.townstead.temperature;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.curios.CuriosCompat;
import com.aetherianartificer.townstead.compat.temperature.AmbientTemperatureBridge;
import com.aetherianartificer.townstead.compat.temperature.TemperatureBridgeResolver;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What the villager wears, in degrees. Per item: the temperature mod's own value when it has one,
 * else {@code #townstead:warm_clothing} / {@code #townstead:cool_clothing}, else nothing. The sum
 * is clamped so a full set turns a Freezing day into a Cold one and no further.
 */
public final class Insulation {
    //? if >=1.21 {
    public static final TagKey<Item> WARM_CLOTHING = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "warm_clothing"));
    public static final TagKey<Item> COOL_CLOTHING = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "cool_clothing"));
    //?} else {
    /*public static final TagKey<Item> WARM_CLOTHING = TagKey.create(Registries.ITEM,
            new ResourceLocation(Townstead.MOD_ID, "warm_clothing"));
    public static final TagKey<Item> COOL_CLOTHING = TagKey.create(Registries.ITEM,
            new ResourceLocation(Townstead.MOD_ID, "cool_clothing"));
    *///?}

    private static final float TAGGED_PIECE = 0.5f;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private Insulation() {}

    /** Collect flat offsets and directional resistance separately. */
    public static ThermalProtection clothingProtection(LivingEntity entity) {
        ThermalProtection[] total = {ThermalProtection.NONE};
        for (EquipmentSlot slot : ARMOR) total[0] = total[0].plus(itemProtection(entity.getItemBySlot(slot)));
        if (CuriosCompat.present()) {
            CuriosCompat.forEachWorn(entity, stack -> total[0] = total[0].plus(itemProtection(stack)));
        }
        return total[0];
    }

    public static ThermalProtection itemProtection(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ThermalProtection.NONE;
        for (AmbientTemperatureBridge bridge : TemperatureBridgeResolver.installed()) {
            ThermalProtection protection = bridge.itemProtection(stack);
            if (protection != null) return protection;
        }
        if (stack.is(WARM_CLOTHING)) return new ThermalProtection(TAGGED_PIECE, 0, 0, 0);
        if (stack.is(COOL_CLOTHING)) return new ThermalProtection(-TAGGED_PIECE, 0, 0, 0);
        return ThermalProtection.NONE;
    }
}
