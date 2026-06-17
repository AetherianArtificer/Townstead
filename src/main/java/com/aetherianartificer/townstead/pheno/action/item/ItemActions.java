package com.aetherianartificer.townstead.pheno.action.item;

import com.aetherianartificer.townstead.pheno.selector.ItemSelector;
import com.aetherianartificer.townstead.pheno.selector.ItemSelectors;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses an item-action JSON element into an {@link ItemAction}. An array runs every action in
 * order; an object dispatches by {@code "type"}, then, if it carries an {@code on}, runs once per
 * selected stack (the item analogue of an entity action's {@code on}). Clone of {@code Actions}.
 */
public final class ItemActions {

    private ItemActions() {}

    @Nullable
    public static ItemAction parse(@Nullable JsonElement element) {
        if (element == null) return null;
        if (element.isJsonArray()) {
            List<ItemAction> actions = new ArrayList<>();
            for (JsonElement child : element.getAsJsonArray()) {
                ItemAction action = parse(child);
                if (action == null) return null;
                actions.add(action);
            }
            if (actions.isEmpty()) return null;
            return ctx -> actions.forEach(a -> a.run(ctx));
        }
        if (!element.isJsonObject()) return null;
        JsonObject json = element.getAsJsonObject();
        ItemAction inner = ItemActionTypes.get(GsonHelper.getAsString(json, "type", ""))
                .map(t -> t.parse(json)).orElse(null);
        if (inner == null) return null;
        if (!json.has("on")) return inner;
        ItemSelector selector = ItemSelectors.parse(json.get("on"));
        if (selector == null) return null;
        ItemAction core = inner;
        return ctx -> {
            if (ctx.holder() == null) return;
            for (ItemStack stack : selector.select(ctx.holder())) {
                core.run(new ItemActionContext(stack, ctx.holder()));
            }
        };
    }
}
