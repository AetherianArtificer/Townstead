package com.aetherianartificer.townstead.root.ability;

import com.aetherianartificer.townstead.root.gene.types.AbilityGeneType;
import com.aetherianartificer.townstead.pheno.power.Power;
import com.aetherianartificer.townstead.pheno.power.Powers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-entity state for toggle-mode ability genes, stored as the DEVIATION from each
 * gene's authored default. A player's state is saved with their player data and comes
 * back on login; a villager's is transient. For the common default-off toggle the deviation
 * set IS the on-set, so single-arg reads keep their historical meaning; default-on
 * toggles must read through the default-aware overload. Flipped by the Root Ability
 * key; read by the ability ticker to decide whether a toggle ability applies this tick.
 */
public final class AbilityToggles {

    private static final Map<UUID, Set<ResourceLocation>> FLIPPED = new ConcurrentHashMap<>();
    private static final String SAVE_KEY = "townstead:ability_toggles";

    private AbilityToggles() {}

    /** Raw deviation state — for a default-off toggle this reads as "on". */
    public static boolean isOn(LivingEntity entity, ResourceLocation geneId) {
        Set<ResourceLocation> set = FLIPPED.get(entity.getUUID());
        return set != null && set.contains(geneId);
    }

    /** Effective state of a toggle-mode ability gene: its authored default XOR the flip. */
    public static boolean isOn(LivingEntity entity, ResourceLocation geneId, boolean defaultOn) {
        return defaultOn != isOn(entity, geneId);
    }

    /**
     * Effective state resolved through the gene registry, for callers holding only the id
     * (the {@code toggled} condition, diagnostics). Unknown / non-ability genes read as
     * default-off (the raw deviation).
     */
    public static boolean isOnEffective(LivingEntity entity, ResourceLocation geneId) {
        com.aetherianartificer.townstead.root.gene.Gene gene =
                com.aetherianartificer.townstead.root.gene.GeneRegistry.byId(geneId);
        boolean defaultOn = gene != null
                && gene.instance() instanceof AbilityGeneType.Instance ability
                && ability.mode() == AbilityGeneType.Mode.TOGGLE
                && ability.defaultOn();
        return defaultOn != isOn(entity, geneId);
    }

    /** Flip a toggle ability's state; returns whether it now deviates from its default. */
    public static boolean flip(LivingEntity entity, ResourceLocation geneId) {
        Set<ResourceLocation> set = FLIPPED.computeIfAbsent(entity.getUUID(), k -> ConcurrentHashMap.newKeySet());
        if (set.contains(geneId)) {
            set.remove(geneId);
            save(entity, set);
            Powers.invalidate(entity);
            return false;
        }
        set.add(geneId);
        save(entity, set);
        Powers.invalidate(entity);
        return true;
    }

    /**
     * Force a toggle's deviation state (villager AI deploys and folds wings without a
     * keybind). {@code on} is the effective state for the default-off genes this serves.
     */
    public static void set(LivingEntity entity, ResourceLocation geneId, boolean on) {
        Set<ResourceLocation> set = FLIPPED.computeIfAbsent(entity.getUUID(), k -> ConcurrentHashMap.newKeySet());
        boolean changed = on ? set.add(geneId) : set.remove(geneId);
        if (!changed) return;
        save(entity, set);
        Powers.invalidate(entity);
    }

    /** Load a player's saved toggles on login; call before the login sync. */
    public static void restore(net.minecraft.world.entity.player.Player player) {
        net.minecraft.nbt.CompoundTag owner = player.getPersistentData()
                .getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
        net.minecraft.nbt.ListTag list = owner.getList(SAVE_KEY, net.minecraft.nbt.Tag.TAG_STRING);
        Set<ResourceLocation> set = ConcurrentHashMap.newKeySet();
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            if (id != null) set.add(id);
        }
        if (set.isEmpty()) FLIPPED.remove(player.getUUID());
        else FLIPPED.put(player.getUUID(), set);
        Powers.invalidate(player);
    }

    /** Write the live state onto the respawned player, so a death does not drop the saved copy. */
    public static void onClone(net.minecraft.world.entity.player.Player replacement) {
        Set<ResourceLocation> set = FLIPPED.get(replacement.getUUID());
        save(replacement, set == null ? Set.of() : set);
    }

    private static void save(LivingEntity entity, Set<ResourceLocation> set) {
        if (!(entity instanceof net.minecraft.world.entity.player.Player player) || player.level().isClientSide()) return;
        net.minecraft.nbt.CompoundTag data = player.getPersistentData();
        net.minecraft.nbt.CompoundTag owner = data.getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (ResourceLocation id : set) list.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));
        if (list.isEmpty()) owner.remove(SAVE_KEY);
        else owner.put(SAVE_KEY, list);
        data.put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, owner);
    }

    /** Whether the entity has any toggle deviating from its default — a cheap gate for per-tick AI. */
    public static boolean hasAny(LivingEntity entity) {
        Set<ResourceLocation> set = FLIPPED.get(entity.getUUID());
        return set != null && !set.isEmpty();
    }

    /**
     * Every expressed gene whose state lives in this map and is currently EFFECTIVELY on:
     * toggle-mode abilities (default XOR flip) AND standalone {@code pheno:toggle} genes
     * (default-off). Both sync paths must use this — filtering by one gene kind silently
     * strands the other kind's state server-side (the client gates then never fire,
     * invisibly). The wire carries effective state, so clients never need the defaults.
     */
    private static List<String> onSet(LivingEntity entity) {
        List<String> on = new ArrayList<>();
        for (Power gene : Powers.active(entity)) {
            boolean state;
            if (gene.component() instanceof AbilityGeneType.Instance ability
                    && ability.mode() == AbilityGeneType.Mode.TOGGLE) {
                state = isOn(entity, gene.id(), ability.defaultOn());
            } else if (gene.component()
                    instanceof com.aetherianartificer.townstead.root.gene.types.ToggleGeneType.Instance) {
                state = isOn(entity, gene.id());
            } else {
                continue;
            }
            if (state) on.add(gene.id().toString());
        }
        return on;
    }

    /** Sync a non-player entity's on-set to everyone tracking it (a villager's deployed wings). */
    public static void syncEntity(LivingEntity entity) {
        AbilityTogglesS2CPayload payload = new AbilityTogglesS2CPayload(entity.getId(), onSet(entity));
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntity(entity, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToTrackingEntity(entity, payload);
        *///?}
    }

    /**
     * Send one entity's effective on-set to a single watcher (StartTracking / login
     * catch-up), so default-on toggles read correctly client-side before any flip.
     */
    public static void syncToWatcher(ServerPlayer watcher, LivingEntity target) {
        AbilityTogglesS2CPayload payload = new AbilityTogglesS2CPayload(target.getId(), onSet(target));
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(watcher, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(watcher, payload);
        *///?}
    }

    public static void clear(UUID uuid) {
        FLIPPED.remove(uuid);
    }

    /**
     * Sync the player's live on-set, keyed by network id: to the player themself
     * (so the controlling client can predict toggle-driven movement abilities) AND
     * to everyone tracking them (so toggle-gated visuals — deployed wings — render
     * on other clients too). Carries only currently-expressed toggle genes that are on.
     */
    public static void syncTo(ServerPlayer player) {
        AbilityTogglesS2CPayload payload = new AbilityTogglesS2CPayload(player.getId(), onSet(player));
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
        com.aetherianartificer.townstead.TownsteadNetwork.sendToTrackingEntity(player, payload);
        *///?}
    }
}
