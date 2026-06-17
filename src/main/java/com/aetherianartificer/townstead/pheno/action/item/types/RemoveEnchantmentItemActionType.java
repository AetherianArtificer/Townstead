package com.aetherianartificer.townstead.pheno.action.item.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.item.ItemAction;
import com.aetherianartificer.townstead.pheno.action.item.ItemActionType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;

/**
 * Removes an {@code enchantment} from the stack (Apoli's item {@code remove_enchantment}).
 * Enchantments live in a static registry on 1.20 and a data registry on 1.21, so the two
 * branches diverge: 1.21 mutates the enchantments component by {@code ResourceKey}, 1.20
 * edits the {@code EnchantmentHelper} map.
 *
 * <p>JSON: {@code { "type":"pheno:remove_enchantment", "enchantment":"minecraft:sharpness" }}</p>
 */
public final class RemoveEnchantmentItemActionType implements ItemActionType {

    public static final String KEY = "pheno:remove_enchantment";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public ItemAction parse(JsonObject json) {
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "enchantment", ""));
        if (id == null) return null;
        return ctx -> {
            ItemStack stack = ctx.stack();
            //? if >=1.21 {
            net.minecraft.world.item.enchantment.EnchantmentHelper.updateEnchantments(stack, mutable ->
                    mutable.removeIf(holder -> holder.is(net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.ENCHANTMENT, id))));
            //?} else {
            /*java.util.Map<net.minecraft.world.item.enchantment.Enchantment, Integer> map =
                    new java.util.HashMap<>(net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(stack));
            net.minecraft.world.item.enchantment.Enchantment enchantment =
                    net.minecraft.core.registries.BuiltInRegistries.ENCHANTMENT.get(id);
            if (enchantment != null && map.remove(enchantment) != null) {
                net.minecraft.world.item.enchantment.EnchantmentHelper.setEnchantments(map, stack);
            }
            *///?}
        };
    }
}
