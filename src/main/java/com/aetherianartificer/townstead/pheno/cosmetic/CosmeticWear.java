package com.aetherianartificer.townstead.pheno.cosmetic;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Display-only equipment: an item shown in a slot for a while without touching real gear or
 * creating anything. Server state lives in the entity's persistent data, keyed by slot with a
 * world game-time expiry, so it survives unload and restart like other pheno state; clients get
 * it through {@link CosmeticWearS2CPayload} and read it from {@code CosmeticClientStore}.
 */
public final class CosmeticWear {

    static final String STORAGE_KEY = "TownsteadCosmetics";

    private CosmeticWear() {}

    public record Worn(EquipmentSlot slot, ResourceLocation item, long until) {}

    public static void wear(LivingEntity entity, EquipmentSlot slot, ResourceLocation item, int ticks) {
        long until = entity.level().getGameTime() + Math.max(1, ticks);
        CompoundTag all = entity.getPersistentData().getCompound(STORAGE_KEY);
        CompoundTag one = new CompoundTag();
        one.putString("item", item.toString());
        one.putLong("until", until);
        all.put(slot.getName(), one);
        entity.getPersistentData().put(STORAGE_KEY, all);
        broadcast(entity);
    }

    /** What is showing in the slot right now, on either side, or null. */
    @Nullable
    public static ResourceLocation worn(LivingEntity entity, EquipmentSlot slot) {
        if (entity.level().isClientSide()) return CosmeticClientBridge.worn(entity, slot);
        CompoundTag one = entity.getPersistentData().getCompound(STORAGE_KEY).getCompound(slot.getName());
        if (!one.contains("item") || entity.level().getGameTime() >= one.getLong("until")) return null;
        return ResourceLocation.tryParse(one.getString("item"));
    }

    @Nullable
    public static Item wornItem(LivingEntity entity, EquipmentSlot slot) {
        ResourceLocation id = worn(entity, slot);
        return id == null ? null : BuiltInRegistries.ITEM.get(id);
    }

    static List<Worn> active(LivingEntity entity) {
        List<Worn> out = new ArrayList<>();
        CompoundTag all = entity.getPersistentData().getCompound(STORAGE_KEY);
        long now = entity.level().getGameTime();
        for (String key : all.getAllKeys()) {
            CompoundTag one = all.getCompound(key);
            ResourceLocation item = ResourceLocation.tryParse(one.getString("item"));
            long until = one.getLong("until");
            EquipmentSlot slot = slotByName(key);
            if (slot != null && item != null && until > now) out.add(new Worn(slot, item, until));
        }
        return out;
    }

    @Nullable
    public static EquipmentSlot slotByName(String name) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getName().equals(name)) return slot;
        }
        return null;
    }

    static void broadcast(LivingEntity entity) {
        CosmeticWearS2CPayload payload = new CosmeticWearS2CPayload(entity.getId(), active(entity));
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload);
        //?} else {
        /*if (entity instanceof ServerPlayer self) com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(self, payload);
        com.aetherianartificer.townstead.TownsteadNetwork.sendToTrackingEntity(entity, payload);
        *///?}
    }

    /** Catch-up for a player who starts tracking an entity already in costume. */
    public static void syncToWatcher(ServerPlayer watcher, LivingEntity target) {
        List<Worn> active = active(target);
        if (active.isEmpty()) return;
        CosmeticWearS2CPayload payload = new CosmeticWearS2CPayload(target.getId(), active);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(watcher, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(watcher, payload);
        *///?}
    }
}
