package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.switchboard.Switchboard;
import com.aetherianartificer.townstead.switchboard.Systems;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;
//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Which Discoverable Roots this server has found, and who found each. Discovery is server-wide: the
 * first player to befriend a villager of a Discoverable Root makes it choosable for everyone.
 */
public final class RootDiscovery {
    private RootDiscovery() {}

    /** Root id to the name of the player who discovered it. */
    private static volatile Map<String, String> discovered = Map.of();

    public static boolean isDiscovered(ResourceLocation rootId) {
        return discovered.containsKey(LegacyNamespace.canonical(rootId).toString());
    }

    public static @Nullable String discoveredBy(String rootId) {
        return discovered.get(rootId);
    }

    public static Map<String, String> all() {
        return discovered;
    }

    public static void onServerStarting(MinecraftServer server) {
        discovered = Map.copyOf(Data.get(server).found);
    }

    public static void onServerStopping() {
        discovered = Map.of();
    }

    /** A villager's hearts with a player changed. Discovers the villager's Root when they are friends now. */
    public static void onHearts(Entity villager, UUID playerId, int hearts) {
        MinecraftServer server = villager.level().getServer();
        if (server == null) return;
        if (hearts < Switchboard.get(TownsteadConfig.ROOT_DISCOVERY_HEARTS) || !Systems.on(Systems.ROOTS)) return;
        String raw = RootAssignment.currentRoot(villager);
        ResourceLocation rootId = raw == null ? null : ResourceLocation.tryParse(raw);
        if (!RootRules.awaitsDiscovery(rootId)) return;
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) discover(server, rootId, player.getGameProfile().getName());
    }

    /** Marks a Root discovered. Returns false when it already was. */
    public static boolean discover(MinecraftServer server, ResourceLocation rootId, String by) {
        String id = LegacyNamespace.canonical(rootId).toString();
        if (discovered.containsKey(id)) return false;
        Data data = Data.get(server);
        data.found.put(id, by);
        data.setDirty();
        discovered = Map.copyOf(data.found);
        announce(server, new RootDiscoveredS2CPayload(id, by));
        return true;
    }

    /** Hides a discovered Root again. Players who already chose it keep it. */
    public static boolean forget(MinecraftServer server, ResourceLocation rootId) {
        String id = LegacyNamespace.canonical(rootId).toString();
        Data data = Data.get(server);
        if (data.found.remove(id) == null) return false;
        data.setDirty();
        discovered = Map.copyOf(data.found);
        announce(server, null);
        return true;
    }

    private static void announce(MinecraftServer server, @Nullable RootDiscoveredS2CPayload toast) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // Root data first, so the toast can name the Root from the fresh catalog.
            Townstead.townstead$sendRootData(player);
            if (toast == null) continue;
            //? if neoforge {
            PacketDistributor.sendToPlayer(player, toast);
            //?} else {
            /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, toast);
            *///?}
        }
    }

    static final class Data extends SavedData {
        private static final String FILE_ID = "townstead_root_discovery";
        private final Map<String, String> found = new LinkedHashMap<>();

        static Data get(MinecraftServer server) {
            //? if >=1.21 {
            return server.overworld().getDataStorage().computeIfAbsent(
                    new Factory<>(Data::new, Data::load), FILE_ID);
            //?} else {
            /*return server.overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, FILE_ID);
            *///?}
        }

        //? if >=1.21 {
        static Data load(CompoundTag tag, HolderLookup.Provider provider) {
        //?} else {
        /*static Data load(CompoundTag tag) {
        *///?}
            Data data = new Data();
            CompoundTag stored = tag.getCompound("found");
            for (String key : stored.getAllKeys()) data.found.put(key, stored.getString(key));
            return data;
        }

        //? if >=1.21 {
        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        //?} else {
        /*@Override
        public CompoundTag save(CompoundTag tag) {
        *///?}
            CompoundTag stored = new CompoundTag();
            found.forEach(stored::putString);
            tag.put("found", stored);
            return tag;
        }
    }
}
