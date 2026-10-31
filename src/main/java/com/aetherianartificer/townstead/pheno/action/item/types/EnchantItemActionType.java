package com.aetherianartificer.townstead.pheno.action.item.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.item.ItemAction;
import com.aetherianartificer.townstead.pheno.action.item.ItemActionType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;

/**
 * Adds an {@code enchantment} at {@code level} to the stack, keeping a higher level it already
 * has. Only items that can take the enchantment are changed unless {@code force} is set. Pair it
 * with {@code pheno:chance} for a crafting proc.
 *
 * <p>JSON: {@code { "type":"pheno:enchant", "enchantment":"minecraft:unbreaking", "level":1 }}</p>
 */
public final class EnchantItemActionType implements ItemActionType {

    public static final String KEY = "pheno:enchant";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public ItemAction parse(JsonObject json) {
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "enchantment", ""));
        if (id == null) return null;
        int level = Math.max(1, GsonHelper.getAsInt(json, "level", 1));
        boolean force = GsonHelper.getAsBoolean(json, "force", false);
        return ctx -> {
            ItemStack stack = ctx.stack();
            if (stack.isEmpty()) return;
            //? if >=1.21 {
            if (ctx.holder() == null) return;
            var holder = ctx.holder().level().registryAccess()
                    .registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                    .getHolder(net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.ENCHANTMENT, id));
            if (holder.isEmpty()) return;
            if (!force && !holder.get().value().canEnchant(stack)) return;
            net.minecraft.world.item.enchantment.EnchantmentHelper.updateEnchantments(stack,
                    mutable -> mutable.upgrade(holder.get(), level));
            //?} else {
            /*net.minecraft.world.item.enchantment.Enchantment enchantment =
                    net.minecraft.core.registries.BuiltInRegistries.ENCHANTMENT.get(id);
            if (enchantment == null || (!force && !enchantment.canEnchant(stack))) return;
            java.util.Map<net.minecraft.world.item.enchantment.Enchantment, Integer> map =
                    new java.util.HashMap<>(net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(stack));
            map.merge(enchantment, level, Math::max);
            net.minecraft.world.item.enchantment.EnchantmentHelper.setEnchantments(map, stack);
            *///?}
        };
    }
}
