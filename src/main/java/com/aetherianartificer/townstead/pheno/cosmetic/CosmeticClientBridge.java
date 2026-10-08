package com.aetherianartificer.townstead.pheno.cosmetic;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The client's copy of every tracked entity's cosmetics, keyed by network id. Plain data with no
 * client-only types, so the shared condition can read it without loading client classes on a
 * dedicated server.
 */
public final class CosmeticClientBridge {

    private static final Map<Integer, List<CosmeticWear.Worn>> WORN = new ConcurrentHashMap<>();

    private CosmeticClientBridge() {}

    public static void apply(CosmeticWearS2CPayload payload) {
        if (payload.worn().isEmpty()) WORN.remove(payload.entityId());
        else WORN.put(payload.entityId(), List.copyOf(payload.worn()));
    }

    public static void clear() {
        WORN.clear();
    }

    @Nullable
    static ResourceLocation worn(LivingEntity entity, EquipmentSlot slot) {
        List<CosmeticWear.Worn> list = WORN.get(entity.getId());
        if (list == null) return null;
        long now = entity.level().getGameTime();
        for (CosmeticWear.Worn worn : list) {
            if (worn.slot() == slot && worn.until() > now) return worn.item();
        }
        return null;
    }
}
