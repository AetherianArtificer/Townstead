package com.aetherianartificer.townstead.gift;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Which items a gift rule names. Entries are ids or {@code #tags}; {@code $name} reads a variable
 * the world rolled, such as a Persona's {@code $gift}. An id can require a potion, as in
 * {@code minecraft:potion[potion=minecraft:water]} for a water bottle.
 */
public final class GiftItems {
    private GiftItems() {}

    public static boolean matches(List<String> items, ItemStack stack, Map<String, Object> vars) {
        for (String raw : items) {
            String entry = raw.startsWith("$") ? String.valueOf(vars.getOrDefault(raw.substring(1), "")) : raw;
            if (entry.isEmpty()) continue;
            if (entry.startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(entry.substring(1));
                if (tag != null && stack.is(TagKey.create(Registries.ITEM, tag))) return true;
            } else {
                String potion = null;
                int open = entry.indexOf('[');
                if (open > 0 && entry.endsWith("]")) {
                    String property = entry.substring(open + 1, entry.length() - 1).trim();
                    if (property.startsWith("potion=")) potion = property.substring("potion=".length()).trim();
                    entry = entry.substring(0, open);
                }
                ResourceLocation id = ResourceLocation.tryParse(entry);
                if (id == null || !id.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) continue;
                if (potion == null || potion.equals(potionOf(stack))) return true;
            }
        }
        return false;
    }

    /** The potion id in a potion, splash potion, lingering potion or tipped arrow, or null. */
    public static @Nullable String potionOf(ItemStack stack) {
        //? if >=1.21 {
        var contents = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        if (contents == null || contents.potion().isEmpty()) return null;
        return contents.potion().get().unwrapKey().map(key -> key.location().toString()).orElse(null);
        //?} else {
        /*net.minecraft.world.item.alchemy.Potion potion = net.minecraft.world.item.alchemy.PotionUtils.getPotion(stack);
        ResourceLocation id = BuiltInRegistries.POTION.getKey(potion);
        return id == null ? null : id.toString();
        *///?}
    }
}
