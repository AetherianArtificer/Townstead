package com.aetherianartificer.townstead.naming;

import com.aetherianartificer.townstead.rebirth.Rebirth;
import com.aetherianartificer.townstead.root.PlayerRoot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A player's side of naming. The rules are the ones villagers follow, in {@link Naming}: a player
 * picks a tradition, it settles their family name, and a typed name outranks what a rule made.
 */
public final class PlayerNames {

    private static final List<PlayerFamilySource> SOURCES = new CopyOnWriteArrayList<>();
    /** The name each player's sources last gave, so a change made in another mod gets synced. */
    private static final Map<UUID, String> LAST_AUTHORITATIVE = new ConcurrentHashMap<>();

    private PlayerNames() {}

    public static void addSource(PlayerFamilySource source) {
        if (source != null) SOURCES.add(source);
    }

    /** The family name another mod holds for this player, or empty. */
    public static String authoritative(Player player) {
        for (PlayerFamilySource source : SOURCES) {
            String name = source.authoritative(player);
            if (name != null && !name.isBlank()) return name.trim();
        }
        return "";
    }

    public static boolean isLocked(Player player) {
        return !authoritative(player).isEmpty();
    }

    static String fallback(Player player, NamingTradition tradition) {
        for (PlayerFamilySource source : SOURCES) {
            String name = source.fallback(player, tradition);
            if (name != null && !name.isBlank()) return name.trim();
        }
        return "";
    }

    /** Other mods change their names without telling anyone, so a held name is checked for changes. */
    public static void tick(MinecraftServer server) {
        if (SOURCES.isEmpty() || server.getTickCount() % 40 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String held = authoritative(player);
            if (!held.equals(LAST_AUTHORITATIVE.put(player.getUUID(), held))) settled(player);
        }
    }

    /** The name this player goes by without the family name, which a patronymic is built from. */
    public static String given(Player player) {
        String reborn = Rebirth.characterName(player);
        if (reborn != null) return reborn;
        Component custom = player.getCustomName();
        return (custom != null ? custom : player.getName()).getString().trim();
    }

    public static NamingTradition.Order order(Player player) {
        NamingTradition tradition = Naming.traditionOf(player);
        return tradition == null ? NamingTradition.Order.GIVEN_FIRST : tradition.order();
    }

    /** Sets a chosen family name. An empty one hands the name back to the player's tradition. */
    public static void setFamily(ServerPlayer player, String name) {
        if (isLocked(player)) {
            settled(player);
            return;
        }
        String typed = name == null ? "" : name.trim();
        PlayerRoot.setFamilyName(player, typed);
        PlayerRoot.setFamilyNameFixed(player, !typed.isEmpty());
        settled(player);
    }

    /**
     * Moves the player to a culture, or to a tradition with no culture. A culture brings its own
     * tradition. As with a villager, a chosen family name stays.
     */
    public static void setIdentity(ServerPlayer player, String cultureId, String traditionId) {
        String culture = cultureId == null ? "" : cultureId.trim();
        String tradition = traditionId == null ? "" : traditionId.trim();
        if (!culture.isEmpty()) {
            com.aetherianartificer.townstead.culture.Culture chosen = com.aetherianartificer.townstead.culture.Cultures.get(culture);
            if (chosen == null || chosen.isSubculture() || !NamingTraditions.culturePickable(chosen)) {
                settled(player);
                return;
            }
            culture = chosen.id().toString();
            tradition = TraditionAssignment.ofCulture(culture);
        } else if (!tradition.isEmpty() && NamingTraditions.get(ResourceLocation.tryParse(tradition)) == null) {
            settled(player);
            return;
        }
        PlayerRoot.setCulture(player, culture);
        PlayerRoot.setNamingTradition(player, tradition);
        PlayerRoot.setNameList(player, "");
        if (!PlayerRoot.isFamilyNameFixed(player)) PlayerRoot.setFamilyName(player, "");
        settled(player);
    }

    /** A new life keeps the player's naming tradition and settles a family name of its own. */
    public static void startNewLife(ServerPlayer player) {
        PlayerRoot.setNameList(player, "");
        PlayerRoot.setFamilyName(player, "");
        PlayerRoot.setFamilyNameFixed(player, false);
        settled(player);
    }

    public static void onLogin(ServerPlayer player) {
        recordFamily(player);
        NameSyncTarget.syncPlayer(player);
        NameSyncTarget.sendPlayerFamilies(player);
    }

    /** Whether this player's tab list entry should be their display name rather than their username. */
    public static boolean composedInTabList(Player player) {
        return !player.level().isClientSide()
                && (Rebirth.characterName(player) != null || !Naming.familyName(player).isEmpty());
    }

    private static void settled(ServerPlayer player) {
        player.refreshDisplayName();
        player.refreshTabListName();
        NameSyncTarget.syncPlayer(player);
        if (recordFamily(player)) {
            for (ServerPlayer online : player.server.getPlayerList().getPlayers()) {
                NameSyncTarget.sendPlayerFamilies(online);
            }
        }
    }

    private static boolean recordFamily(ServerPlayer player) {
        return NamingRegisterSavedData.get(player.server)
                .putPlayerFamily(player.getUUID(), Naming.familyName(player), order(player));
    }

    /** {@code name} with this player's family name added, in the configured style. */
    public static Component compose(Player player, Component name) {
        if (!com.aetherianartificer.townstead.switchboard.Systems.on(com.aetherianartificer.townstead.switchboard.Systems.NAMING)) return name;
        String composed = player.level().isClientSide()
                ? NameClientStore.styled(player.getId(), name.getString(), DisplayNames.style())
                : DisplayNames.style().apply(name.getString(), Naming.familyName(player), order(player));
        return composed.equals(name.getString()) ? name : Component.literal(composed).withStyle(name.getStyle());
    }
}
