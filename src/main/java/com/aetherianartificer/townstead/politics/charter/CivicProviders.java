package com.aetherianartificer.townstead.politics.charter;

import com.aetherianartificer.townstead.politics.state.SettlementRef;
import net.minecraft.server.level.ServerPlayer;
import java.util.LinkedHashMap;
import java.util.Map;

/** Optional, server-owned civic sources. Provider actions never become native organization mutations. */
public final class CivicProviders {
    public interface Provider {
        String id();
        default boolean ownsGovernment(net.minecraft.server.MinecraftServer server, SettlementRef settlement) { return false; }
        CharterSnapshotS2CPayload.Civic read(ServerPlayer viewer, SettlementRef settlement);
        default boolean opensScreen(String action) { return false; }
        boolean execute(ServerPlayer viewer, SettlementRef settlement, String actor, String action);
    }
    private static final Map<String, Provider> PROVIDERS = new LinkedHashMap<>();
    static { register(new com.aetherianartificer.townstead.compat.mcacapitals.CapitalsCivicProvider()); }
    private CivicProviders() {}
    public static synchronized void register(Provider provider) {
        if (PROVIDERS.putIfAbsent(provider.id(), provider) != null) throw new IllegalArgumentException("Duplicate civic provider " + provider.id());
    }
    public static CharterSnapshotS2CPayload.Civic read(ServerPlayer viewer, SettlementRef settlement) {
        for (Provider provider : PROVIDERS.values()) {
            var view = provider.read(viewer, settlement);
            if (view != null) return view;
        }
        return null;
    }
    public static boolean ownsGovernment(net.minecraft.server.MinecraftServer server, SettlementRef settlement) {
        return PROVIDERS.values().stream().anyMatch(provider -> provider.ownsGovernment(server, settlement));
    }
    public static boolean execute(ServerPlayer viewer, SettlementRef settlement, CharterSnapshotS2CPayload.Civic current, String actor, String action) {
        if (current == null || !current.actor().equals(actor) || current.actions().stream().noneMatch(a -> a.id().equals(action))) return false;
        var provider = PROVIDERS.get(current.provider());
        return provider != null && provider.execute(viewer, settlement, actor, action);
    }
    public static boolean opensScreen(CharterSnapshotS2CPayload.Civic current, String action) {
        var provider = current == null ? null : PROVIDERS.get(current.provider());
        return provider != null && provider.opensScreen(action);
    }
    public static long revision(ServerPlayer viewer, CharterSnapshotS2CPayload.Civic view) {
        return CharterMemberships.revision(viewer.server) ^ (view == null ? 0 : Integer.toUnsignedLong(view.hashCode()));
    }
}
