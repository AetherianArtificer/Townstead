package com.aetherianartificer.townstead.pheno.cosmetic;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows an item in an equipment slot for a while, display only: real gear is untouched and nothing
 * is created. {@code item} is one id, a {@code #tag}, or a list of either; one is picked at random.
 *
 * <p>JSON: {@code { "type":"pheno:wear_cosmetic", "slot":"head",
 * "item":["minecraft:zombie_head","minecraft:creeper_head"], "duration":200 }}</p>
 */
public final class WearCosmeticActionType implements ActionType {

    public static final String KEY = "pheno:wear_cosmetic";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        EquipmentSlot slot = CosmeticWear.slotByName(GsonHelper.getAsString(json, "slot", "head"));
        if (slot == null || !json.has("item")) return null;
        List<String> choices = new ArrayList<>();
        JsonElement raw = json.get("item");
        if (raw.isJsonArray()) raw.getAsJsonArray().forEach(e -> choices.add(e.getAsString()));
        else choices.add(raw.getAsString());
        if (choices.isEmpty()) return null;
        int duration = Math.max(1, GsonHelper.getAsInt(json, "duration", 200));
        return ctx -> {
            if (ctx.level().isClientSide()) return;
            ResourceLocation item = pick(choices, ctx.entity().getRandom().nextInt(choices.size()), ctx.entity());
            if (item == null) {
                ctx.fail();
                return;
            }
            CosmeticWear.wear(ctx.entity(), slot, item, duration);
        };
    }

    /** One entry of the list, resolved to an item id; a tag picks one of its items. */
    private static ResourceLocation pick(List<String> choices, int index, net.minecraft.world.entity.LivingEntity entity) {
        String choice = choices.get(index);
        if (!choice.startsWith("#")) {
            ResourceLocation id = ResourceLocation.tryParse(choice);
            return id != null && BuiltInRegistries.ITEM.containsKey(id) ? id : null;
        }
        ResourceLocation tagId = ResourceLocation.tryParse(choice.substring(1));
        if (tagId == null) return null;
        List<Holder<Item>> items = new ArrayList<>();
        BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, tagId)).ifPresent(set -> set.forEach(items::add));
        if (items.isEmpty()) return null;
        return BuiltInRegistries.ITEM.getKey(items.get(entity.getRandom().nextInt(items.size())).value());
    }
}
