package com.aetherianartificer.townstead.compat.temperature;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.compat.thirst.ToughAsNailsThirstBridge;
import com.aetherianartificer.townstead.temperature.ThermalProtection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** Entity-only TAN modifiers that its native player handlers cannot apply to MCA villagers. */
public final class ToughAsNailsEntityCompat {
    private ToughAsNailsEntityCompat() {}
    private static MobEffectInstance effect(LivingEntity entity, String path) {
        if (!ModCompat.isLoaded("toughasnails")) return null;
        for (MobEffectInstance effect : entity.getActiveEffects()) {
            //? if >=1.21 {
            var key = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
            //?} else {
            /*var key = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect());
            *///?}
            if (ToughAsNailsThirstBridge.id(path).equals(key)) return effect;
        }
        return null;
    }
    public static boolean climateClemency(LivingEntity entity) { return effect(entity, "climate_clemency") != null; }
    public static float internalAmbient(LivingEntity entity, float ambient) {
        return TanTemperaturePolicy.internal(ambient, effect(entity, "internal_warmth") != null,
                effect(entity, "internal_chill") != null);
    }
    public static float thirstExhaustion(LivingEntity entity) {
        var effect = effect(entity, "thirst");
        return effect == null ? 0 : 0.025f * (effect.getAmplifier() + 1);
    }
    public static boolean enchanted(LivingEntity entity, ItemStack stack, String path) {
        if (!ModCompat.isLoaded("toughasnails")) return false;
        var id = ToughAsNailsThirstBridge.id(path);
        //? if >=1.21 {
        var registry = entity.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        var enchantment = registry.getHolder(id);
        return enchantment.isPresent() && EnchantmentHelper.getItemEnchantmentLevel(enchantment.get(), stack) > 0;
        //?} else {
        /*var enchantment = BuiltInRegistries.ENCHANTMENT.get(id);
        return enchantment != null && EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack) > 0;
        *///?}
    }
    public static ThermalProtection protection(LivingEntity entity) {
        for (ItemStack stack : entity.getArmorSlots())
            if (enchanted(entity, stack, "thermal_tuning")) return new ThermalProtection(0, 0, 0, 10000);
        return effect(entity, "ice_resistance") != null ? new ThermalProtection(0, 10000, 0, 0) : ThermalProtection.NONE;
    }
    public static void cleanseCanteens(net.conczin.mca.entity.VillagerEntityMCA villager) {
        if (!ModCompat.isLoaded("toughasnails") || villager.tickCount % 20 != 0) return;
        var inventory = villager.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!enchanted(villager, stack, "water_cleansing")) continue;
            try {
                var method = stack.getItem().getClass().getMethod("getPurifiedWaterCanteen");
                // Empty canteens also expose this method, but only filled canteens are drinks.
                if (!ToughAsNailsThirstBridge.INSTANCE.isDrink(stack)) continue;
                var item = (net.minecraft.world.item.Item) method.invoke(stack.getItem());
                if (item == stack.getItem()) continue;
                ItemStack purified = new ItemStack(item);
                purified.setDamageValue(stack.getDamageValue());
                //? if >=1.21 {
                EnchantmentHelper.setEnchantments(purified, EnchantmentHelper.getEnchantmentsForCrafting(stack));
                //?} else {
                /*EnchantmentHelper.setEnchantments(EnchantmentHelper.getEnchantments(stack), purified);
                *///?}
                inventory.setItem(slot, purified);
            } catch (ReflectiveOperationException ignored) { }
        }
    }
}
