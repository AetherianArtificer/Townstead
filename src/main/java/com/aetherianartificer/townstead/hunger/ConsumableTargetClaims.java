package com.aetherianartificer.townstead.hunger;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Short-lived reservations for consumable targets so large groups do not
 * repeatedly converge on the same container slot, crop, or dropped item.
 */
public final class ConsumableTargetClaims {
    private static final Map<String, UUID> CLAIM_OWNER = new ConcurrentHashMap<>();
    private static final Map<String, Long> CLAIM_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<String>> OWNER_TO_KEYS = new ConcurrentHashMap<>();
    private static final Object CLAIM_LOCK = new Object();
    private static final LongAdder CLAIM_GRANTS = new LongAdder();
    private static final LongAdder CLAIM_CONFLICTS = new LongAdder();

    private ConsumableTargetClaims() {}

    public static boolean tryClaimPos(ServerLevel level, UUID owner, String category, BlockPos pos, long untilTick) {
        if (level == null || owner == null || category == null || pos == null) return false;
        return tryClaim(level, owner, ConsumableClaimKeys.posClaimKey(level.dimension().location() == null ? null : level.dimension().location().toString(), category, pos.asLong()), untilTick);
    }

    public static boolean tryClaimItem(ServerLevel level, UUID owner, String category, ItemEntity item, long untilTick) {
        if (level == null || owner == null || category == null || item == null) return false;
        return tryClaim(level, owner, claimKey(level.dimension().location(), category, "item:" + item.getUUID()), untilTick);
    }

    public static boolean tryClaimSlot(ServerLevel level, UUID owner, String category, NearbyItemSources.ContainerSlot slot, long untilTick) {
        if (level == null || owner == null || category == null || slot == null || slot.pos() == null) return false;
        return tryClaim(level, owner, slotClaimKey(
                level.dimension().location() == null ? null : level.dimension().location().toString(),
                category,
                slot.pos().asLong(),
                slot.slot(),
                slot.isItemHandler(),
                slot.side() == null ? null : slot.side().getName()
        ), untilTick);
    }

    public static boolean isClaimedByOtherPos(ServerLevel level, UUID owner, String category, BlockPos pos) {
        if (level == null || owner == null || category == null || pos == null) return false;
        return isClaimedByOther(level, owner, ConsumableClaimKeys.posClaimKey(level.dimension().location() == null ? null : level.dimension().location().toString(), category, pos.asLong()));
    }

    public static boolean isClaimedByOtherItem(ServerLevel level, UUID owner, String category, ItemEntity item) {
        if (level == null || owner == null || category == null || item == null) return false;
        return isClaimedByOther(level, owner, claimKey(level.dimension().location(), category, "item:" + item.getUUID()));
    }

    public static boolean isClaimedByOtherSlot(ServerLevel level, UUID owner, String category, NearbyItemSources.ContainerSlot slot) {
        if (level == null || owner == null || category == null || slot == null || slot.pos() == null) return false;
        return isClaimedByOther(level, owner, slotClaimKey(
                level.dimension().location() == null ? null : level.dimension().location().toString(),
                category,
                slot.pos().asLong(),
                slot.slot(),
                slot.isItemHandler(),
                slot.side() == null ? null : slot.side().getName()
        ));
    }

    public static void releaseSlot(ServerLevel level, UUID owner, String category, NearbyItemSources.ContainerSlot slot) {
        if (level == null || owner == null || category == null || slot == null || slot.pos() == null) return;
        release(owner, slotClaimKey(
                level.dimension().location() == null ? null : level.dimension().location().toString(),
                category,
                slot.pos().asLong(),
                slot.slot(),
                slot.isItemHandler(),
                slot.side() == null ? null : slot.side().getName()
        ));
    }

    public static void releaseAll(UUID owner) {
        if (owner == null) return;
        synchronized (CLAIM_LOCK) {
            Set<String> keys = OWNER_TO_KEYS.remove(owner);
            if (keys == null || keys.isEmpty()) return;
            for (String key : keys) {
                UUID existingOwner = CLAIM_OWNER.get(key);
                if (owner.equals(existingOwner)) {
                    CLAIM_OWNER.remove(key);
                    CLAIM_UNTIL.remove(key);
                }
            }
        }
    }

    private static boolean tryClaim(ServerLevel level, UUID owner, String key, long untilTick) {
        synchronized (CLAIM_LOCK) {
            pruneExpired(level, key);
            UUID existingOwner = CLAIM_OWNER.get(key);
            if (existingOwner != null && !existingOwner.equals(owner)) {
                CLAIM_CONFLICTS.increment();
                return false;
            }
            CLAIM_OWNER.put(key, owner);
            CLAIM_UNTIL.put(key, untilTick);
            OWNER_TO_KEYS.computeIfAbsent(owner, ignored -> new HashSet<>()).add(key);
            CLAIM_GRANTS.increment();
            return true;
        }
    }

    private static void release(UUID owner, String key) {
        synchronized (CLAIM_LOCK) {
            UUID existingOwner = CLAIM_OWNER.get(key);
            if (!owner.equals(existingOwner)) return;
            CLAIM_OWNER.remove(key);
            CLAIM_UNTIL.remove(key);
            Set<String> keys = OWNER_TO_KEYS.get(owner);
            if (keys != null) {
                keys.remove(key);
                if (keys.isEmpty()) {
                    OWNER_TO_KEYS.remove(owner);
                }
            }
        }
    }

    private static boolean isClaimedByOther(ServerLevel level, UUID owner, String key) {
        synchronized (CLAIM_LOCK) {
            pruneExpired(level, key);
            UUID existingOwner = CLAIM_OWNER.get(key);
            return existingOwner != null && !owner.equals(existingOwner);
        }
    }

    private static void pruneExpired(ServerLevel level, String key) {
        Long until = CLAIM_UNTIL.get(key);
        if (until == null || until > level.getGameTime()) return;
        UUID existingOwner = CLAIM_OWNER.remove(key);
        CLAIM_UNTIL.remove(key);
        if (existingOwner != null) {
            Set<String> keys = OWNER_TO_KEYS.get(existingOwner);
            if (keys != null) {
                keys.remove(key);
                if (keys.isEmpty()) {
                    OWNER_TO_KEYS.remove(existingOwner);
                }
            }
        }
    }

    private static String claimKey(ResourceLocation dimensionId, String category, String localKey) {
        return (dimensionId == null ? "unknown" : dimensionId.toString()) + "|" + category + "|" + localKey;
    }

    static String slotClaimKey(String dimensionId, String category, long posAsLong, int slot, boolean itemHandler, @Nullable String sideName) {
        ResourceLocation resourceLocation = null;
        if (dimensionId != null && !dimensionId.isBlank()) {
            //? if >=1.21 {
            resourceLocation = ResourceLocation.parse(dimensionId);
            //?} else {
            /*resourceLocation = new ResourceLocation(dimensionId);
            *///?}
        }
        String rawKey = ConsumableClaimKeys.slotClaimKey(dimensionId, category, posAsLong, slot, itemHandler, sideName);
        int localKeyStart = rawKey.indexOf('|', rawKey.indexOf('|') + 1) + 1;
        return claimKey(resourceLocation, category, rawKey.substring(localKeyStart));
    }

    public static Snapshot snapshot() {
        return new Snapshot(CLAIM_GRANTS.sum(), CLAIM_CONFLICTS.sum(), CLAIM_OWNER.size());
    }

    public record Snapshot(long grants, long conflicts, int activeClaims) {}
}
