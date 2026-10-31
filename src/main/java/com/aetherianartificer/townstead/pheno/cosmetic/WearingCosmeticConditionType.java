package com.aetherianartificer.townstead.pheno.cosmetic;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;

/**
 * True while a cosmetic shows in {@code slot}; with {@code item} (an id or {@code #tag}), only while
 * that cosmetic does. Reads the same state on both sides, so it can gate server behaviour and
 * client visuals alike.
 *
 * <p>JSON: {@code { "type":"pheno:wearing_cosmetic", "slot":"head", "item":"minecraft:zombie_head" }}</p>
 */
public final class WearingCosmeticConditionType implements ConditionType {

    public static final String KEY = "pheno:wearing_cosmetic";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        EquipmentSlot slot = CosmeticWear.slotByName(GsonHelper.getAsString(json, "slot", "head"));
        if (slot == null) return null;
        String filter = GsonHelper.getAsString(json, "item", "");
        if (filter.isEmpty()) {
            return ctx -> ctx.entity() != null && CosmeticWear.worn(ctx.entity(), slot) != null;
        }
        if (filter.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.tryParse(filter.substring(1));
            if (tagId == null) return null;
            TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);
            return ctx -> {
                if (ctx.entity() == null) return false;
                Item item = CosmeticWear.wornItem(ctx.entity(), slot);
                return item != null && item.builtInRegistryHolder().is(tag);
            };
        }
        ResourceLocation id = ResourceLocation.tryParse(filter);
        if (id == null) return null;
        return ctx -> ctx.entity() != null && id.equals(CosmeticWear.worn(ctx.entity(), slot));
    }
}
