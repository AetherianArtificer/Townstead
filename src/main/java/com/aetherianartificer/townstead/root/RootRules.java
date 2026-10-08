package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.switchboard.Switchboard;
import com.aetherianartificer.townstead.switchboard.WorldKeys;
import com.aetherianartificer.townstead.switchboard.WorldKeys.RootState;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Who may have a Root in this world, and how often it spawns. Combines the config blocklist with the
 * world's Switchboard values: each Root's own state and rate, and the switch and rate of every
 * species, ancestry, lineage and pack it resolves through. Rates multiply.
 */
public final class RootRules {
    private RootRules() {}

    public static RootState state(ResourceLocation rootId) {
        return (RootState) Switchboard.content(WorldKeys.rootState(LegacyNamespace.canonical(rootId).toString()));
    }

    /** Off for everyone: the config blocklist, the Root's own state, or a switched-off group. */
    public static boolean isOff(@Nullable ResourceLocation rootId) {
        if (rootId == null) return false;
        if (RootBlocklist.isBlocked(rootId) || state(rootId) == RootState.OFF) return true;
        for (Map.Entry<String, String> group : groups(rootId).entrySet()) {
            if (!(Boolean) Switchboard.content(WorldKeys.groupOn(group.getKey(), group.getValue()))) return true;
        }
        return false;
    }

    public static boolean villagersSpawn(@Nullable ResourceLocation rootId) {
        if (rootId == null || isOff(rootId)) return false;
        RootState state = state(rootId);
        return state == RootState.EVERYONE || state == RootState.VILLAGERS || state == RootState.DISCOVERABLE;
    }

    public static boolean playersChoose(@Nullable ResourceLocation rootId) {
        if (rootId == null || isOff(rootId)) return false;
        RootState state = state(rootId);
        return state == RootState.EVERYONE || state == RootState.PLAYERS
                || state == RootState.DISCOVERABLE && RootDiscovery.isDiscovered(rootId);
    }

    /** Discoverable and not found yet: players cannot see or choose it. */
    public static boolean awaitsDiscovery(@Nullable ResourceLocation rootId) {
        return rootId != null && !isOff(rootId) && state(rootId) == RootState.DISCOVERABLE
                && !RootDiscovery.isDiscovered(rootId);
    }

    /** The Root's spawn multiplier: its own rate times the rate of each group it belongs to. */
    public static double rate(ResourceLocation rootId) {
        double rate = (Double) Switchboard.content(WorldKeys.rootRate(LegacyNamespace.canonical(rootId).toString()));
        for (Map.Entry<String, String> group : groups(rootId).entrySet()) {
            rate *= (Double) Switchboard.content(WorldKeys.groupRate(group.getKey(), group.getValue()));
        }
        return rate;
    }

    /** The species, ancestry, lineage and pack a Root resolves through, by dimension. */
    public static Map<String, String> groups(ResourceLocation rootId) {
        Map<String, String> out = new LinkedHashMap<>();
        ResourceLocation id = LegacyNamespace.canonical(rootId);
        ResourceLocation species = RootRegistry.effectiveSpecies(id);
        if (species != null) out.put("species", LegacyNamespace.canonical(species).toString());
        Root root = RootRegistry.byId(id);
        ResourceLocation ancestry = root == null ? null : root.ancestry();
        if (root != null && root.lineage() != null) {
            out.put("lineage", LegacyNamespace.canonical(root.lineage()).toString());
            Lineage lineage = LineageRegistry.byId(root.lineage());
            if (ancestry == null && lineage != null) ancestry = lineage.ancestry();
        }
        if (ancestry != null) out.put("ancestry", LegacyNamespace.canonical(ancestry).toString());
        out.put("pack", id.getNamespace());
        return out;
    }
}
