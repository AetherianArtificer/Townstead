package com.aetherianartificer.townstead.politics.relations;

import com.aetherianartificer.townstead.politics.state.BondInstance;
import com.aetherianartificer.townstead.politics.state.Faction;
import com.aetherianartificer.townstead.politics.state.Party;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * How factions stand toward outsiders and each other. A faction's welcomes (disposition groups its
 * people are kin to) come from every welcome source; its allies from every alliance source.
 * Townstead's own sources are the Charter declaration and the accord Bond; other mods add theirs
 * (Vampirism totem control, and later Warstead treaties and Capitals pacts). An ally's welcomes
 * extend to the faction, one step only.
 */
public final class FactionRelations {
    public static final ResourceLocation ACCORD = ResourceLocation.tryParse("townstead:accord");
    private static final long CACHE_TICKS = 100;

    /** Groups a faction welcomes by some means other than its own declaration. */
    @FunctionalInterface
    public interface WelcomeSource {
        Set<String> welcomes(MinecraftServer server, Faction faction);
    }

    /** Factions allied with this one by some means other than an accord. */
    @FunctionalInterface
    public interface AllianceSource {
        Collection<ResourceLocation> allies(MinecraftServer server, Faction faction);
    }

    private static final List<WelcomeSource> WELCOMES = new CopyOnWriteArrayList<>();
    private static final List<AllianceSource> ALLIANCES = new CopyOnWriteArrayList<>();
    private static final Map<ResourceLocation, Cached> CACHE = new HashMap<>();

    private record Cached(long at, Set<String> welcomes, Set<ResourceLocation> allies) {}

    static {
        WELCOMES.add((server, faction) -> PoliticalSavedData.get(server).welcomes(faction.id()));
        ALLIANCES.add(FactionRelations::accords);
    }

    private FactionRelations() {}

    public static void register(WelcomeSource source) {
        WELCOMES.add(source);
    }

    public static void register(AllianceSource source) {
        ALLIANCES.add(source);
    }

    /** The groups this faction's people are kin to, its own and its allies'. */
    public static Set<String> welcomes(MinecraftServer server, ResourceLocation faction) {
        return cached(server, faction).welcomes();
    }

    public static Set<ResourceLocation> allies(MinecraftServer server, ResourceLocation faction) {
        return cached(server, faction).allies();
    }

    public static boolean allied(MinecraftServer server, ResourceLocation a, ResourceLocation b) {
        return !a.equals(b) && allies(server, a).contains(b);
    }

    /** Drop cached answers, after a declaration or Bond changes. */
    public static void invalidate() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    private static Cached cached(MinecraftServer server, ResourceLocation faction) {
        long now = server.overworld().getGameTime();
        synchronized (CACHE) {
            Cached cached = CACHE.get(faction);
            if (cached != null && now - cached.at() < CACHE_TICKS) return cached;
        }
        PoliticalSavedData data = PoliticalSavedData.get(server);
        Faction self = data.faction(faction);
        Cached result;
        if (self == null || !self.active()) {
            result = new Cached(now, Set.of(), Set.of());
        } else {
            Set<ResourceLocation> allies = new LinkedHashSet<>();
            for (AllianceSource source : ALLIANCES) allies.addAll(source.allies(server, self));
            allies.remove(faction);
            Set<String> welcomes = new LinkedHashSet<>(own(server, self));
            for (ResourceLocation ally : allies) {
                Faction other = data.faction(ally);
                if (other != null && other.active()) welcomes.addAll(own(server, other));
            }
            result = new Cached(now, Set.copyOf(welcomes), Set.copyOf(allies));
        }
        synchronized (CACHE) {
            CACHE.put(faction, result);
        }
        return result;
    }

    private static Set<String> own(MinecraftServer server, Faction faction) {
        Set<String> out = new LinkedHashSet<>();
        for (WelcomeSource source : WELCOMES) out.addAll(source.welcomes(server, faction));
        return out;
    }

    private static Collection<ResourceLocation> accords(MinecraftServer server, Faction faction) {
        Party self = Party.faction(faction.id());
        Set<ResourceLocation> out = new LinkedHashSet<>();
        for (BondInstance bond : PoliticalSavedData.get(server).activeBonds(self)) {
            if (!bond.kind().equals(ACCORD)) continue;
            Party other = bond.other(self);
            if (other != null && other.isFaction()) out.add(other.faction());
        }
        return out;
    }
}
