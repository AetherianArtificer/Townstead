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

/** The factions a person belongs to: each one they are a citizen of, and its sovereign. */
public final class FactionMembership {
    private static final long CACHE_TICKS = 100;
    private static final Map<LivingEntity, Cached> CACHE = new WeakHashMap<>();

    private record Cached(long at, Set<ResourceLocation> factions) {}

    private FactionMembership() {}

    public static Set<ResourceLocation> of(LivingEntity entity) {
        MinecraftServer server = entity.getServer();
        if (server == null) return Set.of();
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
            if (!bond.kind().equals(FactionBonds.CITIZENSHIP)) continue;
            Party other = bond.other(self);
            ResourceLocation faction = other == null ? null : other.faction();
            if (faction == null) continue;
            out.add(faction);
            out.add(FactionBonds.sovereign(data, faction));
        }
        return Set.copyOf(out);
    }

    public static void invalidate() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }
}
