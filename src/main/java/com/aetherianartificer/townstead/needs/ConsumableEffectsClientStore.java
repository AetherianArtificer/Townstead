package com.aetherianartificer.townstead.needs;

import com.aetherianartificer.townstead.compat.thirst.DataDrivenThirstCompat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/** Client copy of the server's expanded consumable effects. */
public final class ConsumableEffectsClientStore {
    private static volatile Map<ResourceLocation, NeedEffectProjection> EFFECTS = Map.of();

    private ConsumableEffectsClientStore() {}

    public static void setFrom(ConsumableEffectsSyncPayload payload) {
        Map<ResourceLocation, NeedEffectProjection> effects = new LinkedHashMap<>();
        Map<ResourceLocation, Consumables.ResolvedEffect> resolved = new LinkedHashMap<>();
        for (ConsumableEffectsSyncPayload.Row row : payload.rows()) {
            effects.put(row.item(), row.projection());
            resolved.put(row.item(), row.resolvedEffect());
        }
        EFFECTS = Map.copyOf(effects);
        DataDrivenThirstCompat.installResolved(resolved);
    }

    public static NeedEffectProjection projection(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return NeedEffectProjection.NONE;
        return EFFECTS.getOrDefault(BuiltInRegistries.ITEM.getKey(stack.getItem()), NeedEffectProjection.NONE);
    }

    public static void clear() {
        EFFECTS = Map.of();
        DataDrivenThirstCompat.installResolved(Map.of());
    }
}
