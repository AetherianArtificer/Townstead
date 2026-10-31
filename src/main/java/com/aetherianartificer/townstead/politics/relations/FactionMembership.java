package com.aetherianartificer.townstead.politics.relations;

import com.aetherianartificer.townstead.politics.state.BondInstance;
import com.aetherianartificer.townstead.politics.state.FactionBonds;
import com.aetherianartificer.townstead.politics.state.Party;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The factions a person belongs to: each one they are a citizen of or a member of by its own
 * membership Bond (an order's oath), with its sovereign.
 */
public final class FactionMembership {
    private static final long CACHE_TICKS = 100;
    private static final Map<LivingEntity, Cached> CACHE = new WeakHashMap<>();

    private record Cached(long at, Set<ResourceLocation> factions) {}

    private FactionMembership() {}

    public static Set<ResourceLocation> of(LivingEntity entity) {
        MinecraftServer server = entity.getServer();
        // Only people join factions; mobs are asked about on every target change.
        if (server == null || !(entity instanceof net.minecraft.world.entity.player.Player
                || entity instanceof net.minecraft.world.entity.npc.AbstractVillager)) return Set.of();
        long now = entity.level().getGameTime();
        synchronized (CACHE) {
            Cached cached = CACHE.get(entity);
            if (cached != null && now - cached.at() < CACHE_TICKS) return cached.factions();
        }
        Set<ResourceLocation> factions = of(PoliticalSavedData.get(server), entity.getUUID());
        synchronized (CACHE) {
            CACHE.put(entity, new Cached(now, factions));
        }
        return factions;
    }

    static Set<ResourceLocation> of(PoliticalSavedData data, UUID person) {
        Set<ResourceLocation> out = new LinkedHashSet<>();
        Party self = Party.person(person);
        for (BondInstance bond : data.activeBonds(self)) {
            Party other = bond.other(self);
            ResourceLocation faction = other == null ? null : other.faction();
            if (faction == null || !membership(data, faction, bond.kind())) continue;
            out.add(faction);
            out.add(FactionBonds.sovereign(data, faction));
        }
        return Set.copyOf(out);
    }

    private static boolean membership(PoliticalSavedData data, ResourceLocation faction, ResourceLocation bond) {
        if (bond.equals(FactionBonds.CITIZENSHIP)) return true;
        var record = data.faction(faction);
        var kind = record == null ? null
                : com.aetherianartificer.townstead.politics.definition.PoliticalDefinitions.snapshot().kind(record.kind());
        return kind != null && bond.equals(kind.membership().bond());
    }

    /** The disposition group membership gives this person, from the first faction whose kind names one. */
    public static @org.jetbrains.annotations.Nullable String groupOf(LivingEntity entity) {
        Set<ResourceLocation> factions = of(entity);
        if (factions.isEmpty()) return null;
        PoliticalSavedData data = PoliticalSavedData.get(entity.getServer());
        for (ResourceLocation id : factions) {
            var record = data.faction(id);
            var kind = record == null ? null
                    : com.aetherianartificer.townstead.politics.definition.PoliticalDefinitions.snapshot().kind(record.kind());
            if (kind != null && kind.members().group() != null) return kind.members().group();
        }
        return null;
    }

    public static void invalidate() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }
}
