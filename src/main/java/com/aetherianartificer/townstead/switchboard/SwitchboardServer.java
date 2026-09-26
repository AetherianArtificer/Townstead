package com.aetherianartificer.townstead.switchboard;

import com.google.gson.JsonElement;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?} else if forge {
/*import com.aetherianartificer.townstead.TownsteadNetwork;
*///?}

import com.aetherianartificer.townstead.pheno.power.Power;
import com.aetherianartificer.townstead.pheno.power.Powers;
import com.aetherianartificer.townstead.root.ExpressedGenes;
import com.aetherianartificer.townstead.root.ability.GeneAbilityTicker;
import com.aetherianartificer.townstead.root.attribute.GeneAttributeApplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves each world's Switchboard values and keeps {@link Switchboard} and every client in step.
 * Precedence: a value the modpack locks, then the world's own value, then the modpack default,
 * then the TOML.
 */
public final class SwitchboardServer {
    private SwitchboardServer() {}

    public enum Result { OK, UNKNOWN, INVALID, LOCKED }

    private static volatile Set<String> worldKeys = Set.of();

    /** The keys this world set itself, as opposed to values from the modpack or the config file. */
    public static Set<String> worldKeys() {
        return worldKeys;
    }

    public static void onServerStarting(MinecraftServer s) {
        SwitchboardPack.reload();
        com.aetherianartificer.townstead.root.RootDiscovery.onServerStarting(s);
        SwitchboardSavedData data = SwitchboardSavedData.get(s);
        if (data.created()) {
            // A world that already ran without Townstead's Switchboard skips the new-world pass.
            if (s.overworld().getGameTime() > 0) data.markSetupDone();
            else data.setDirty();
        }
        refresh(s, false);
    }

    public static boolean canConfigure(ServerPlayer player) {
        return player.hasPermissions(2) || player.server.isSingleplayerOwner(player.getGameProfile());
    }

    public static void onLogin(ServerPlayer player) {
        syncTo(player);
        RecipeGates.sync(player);
        SwitchboardSavedData data = SwitchboardSavedData.get(player.server);
        if (!data.setupDone() && SwitchboardPack.showOnNewWorld() && canConfigure(player)) open(player, true);
    }

    public static void open(ServerPlayer player, boolean firstJoin) {
        send(player, SwitchboardOpenS2CPayload.of(firstJoin, SwitchboardSavedData.get(player.server).values(),
                SwitchboardPack.values(), SwitchboardPack.locked(), SwitchboardCatalog.build()));
    }

    public static void handleRequest(ServerPlayer player) {
        if (canConfigure(player)) open(player, false);
    }

    public static void handleSave(ServerPlayer player, SwitchboardSaveC2SPayload payload) {
        if (!canConfigure(player)) return;
        Map<String, JsonElement> accepted = new LinkedHashMap<>();
        payload.worldValues().forEach((key, value) -> {
            if (!SwitchboardPack.isLocked(key) && Switchboard.parse(key, value) != null) accepted.put(key, value);
        });
        SwitchboardSavedData data = SwitchboardSavedData.get(player.server);
        data.replaceAll(accepted);
        if (payload.finishSetup()) data.markSetupDone();
        refresh(player.server);
    }

    public static void onServerStopping() {
        worldKeys = Set.of();
        Switchboard.clear();
        com.aetherianartificer.townstead.root.RootDiscovery.onServerStopping();
    }

    public static Map<String, JsonElement> effective(SwitchboardSavedData data) {
        return effective(SwitchboardPack.values(), SwitchboardPack.locked(), data.values());
    }

    static Map<String, JsonElement> effective(Map<String, JsonElement> pack, Set<String> locked,
                                              Map<String, JsonElement> world) {
        Map<String, JsonElement> out = new LinkedHashMap<>(pack);
        world.forEach((key, value) -> {
            if (!locked.contains(key)) out.put(key, value);
        });
        return out;
    }

    public static void refresh(MinecraftServer s) {
        refresh(s, true);
    }

    private static void refresh(MinecraftServer s, boolean announce) {
        Map<String, JsonElement> before = Switchboard.overrides();
        Map<String, Boolean> systemsBefore = systemStates();
        boolean killBefore = com.aetherianartificer.townstead.needs.NeedPace.canKill();
        boolean rootsBefore = Systems.on(Systems.ROOTS);
        boolean careersBefore = Systems.on(Systems.CAREERS);
        Map<LivingEntity, List<Power>> granted = rootsBefore || careersBefore ? grantedPowers(s) : Map.of();
        SwitchboardSavedData data = SwitchboardSavedData.get(s);
        worldKeys = Set.copyOf(data.values().keySet());
        Switchboard.apply(effective(data));
        Powers.dataReloaded();
        if (announce && !killBefore && com.aetherianartificer.townstead.needs.NeedPace.canKill()) {
            com.aetherianartificer.townstead.needs.NeedPace.graceUntil(s.overworld().getGameTime() + 24000L);
        }
        if (rootsBefore && !Systems.on(Systems.ROOTS) || careersBefore && !Systems.on(Systems.CAREERS)) {
            // Passive effects only undo themselves while their power is still granted, so clear them once.
            granted.forEach((entity, powers) -> {
                GeneAttributeApplier.removeFor(entity, powers);
                GeneAbilityTicker.resetPassives(entity);
            });
        }
        SwitchboardSyncS2CPayload payload = SwitchboardSyncS2CPayload.of(Switchboard.overrides());
        for (ServerPlayer player : s.getPlayerList().getPlayers()) {
            send(player, payload);
            com.aetherianartificer.townstead.Townstead.townstead$sendRootData(player);
        }
        if (announce) announce(s, before, systemsBefore);
    }

    private static Map<String, Boolean> systemStates() {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (String system : com.aetherianartificer.townstead.TownsteadConfig.SYSTEM_NAMES) out.put(system, Systems.on(system));
        return out;
    }

    private static void announce(MinecraftServer s, Map<String, JsonElement> before, Map<String, Boolean> systemsBefore) {
        Map<String, JsonElement> after = Switchboard.overrides();
        Set<String> changed = new java.util.TreeSet<>();
        for (String key : before.keySet()) if (!before.get(key).equals(after.get(key))) changed.add(key);
        for (String key : after.keySet()) if (!after.get(key).equals(before.get(key))) changed.add(key);
        Set<String> on = new java.util.TreeSet<>();
        Set<String> off = new java.util.TreeSet<>();
        systemStates().forEach((system, now) -> {
            if (now && !systemsBefore.get(system)) on.add(system);
            if (!now && systemsBefore.get(system)) off.add(system);
        });
        if (changed.isEmpty() && on.isEmpty() && off.isEmpty()) return;
        for (ServerPlayer player : s.getPlayerList().getPlayers()) {
            if (on.isEmpty()) RecipeGates.sync(player);
            else RecipeGates.restore(player);
        }
        com.aetherianartificer.townstead.api.impl.v1.ApiEvents.worldSettingsChanged(s, changed, on, off);
    }

    private static Map<LivingEntity, List<Power>> grantedPowers(MinecraftServer s) {
        Map<LivingEntity, List<Power>> out = new IdentityHashMap<>();
        for (ServerLevel level : s.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof LivingEntity living && ExpressedGenes.canCarry(living)) {
                    out.put(living, List.copyOf(Powers.active(living)));
                }
            }
        }
        return out;
    }

    public static void syncTo(ServerPlayer player) {
        send(player, SwitchboardSyncS2CPayload.of(Switchboard.overrides()));
    }

    public static Result set(MinecraftServer s, String key, JsonElement value) {
        if (!Switchboard.isKnown(key)) return Result.UNKNOWN;
        if (SwitchboardPack.isLocked(key)) return Result.LOCKED;
        if (Switchboard.parse(key, value) == null) return Result.INVALID;
        SwitchboardSavedData.get(s).set(key, value);
        refresh(s);
        return Result.OK;
    }

    public static Result reset(MinecraftServer s, String key) {
        if (!Switchboard.isKnown(key)) return Result.UNKNOWN;
        if (SwitchboardPack.isLocked(key)) return Result.LOCKED;
        SwitchboardSavedData.get(s).remove(key);
        refresh(s);
        return Result.OK;
    }

    /** Where the value in effect comes from, for display. */
    public static String sourceOf(MinecraftServer s, String key) {
        if (SwitchboardPack.isLocked(key) && SwitchboardPack.values().containsKey(key)) return "modpack (locked)";
        if (SwitchboardSavedData.get(s).values().containsKey(key)) return "world";
        if (SwitchboardPack.values().containsKey(key)) return "modpack";
        return "config";
    }

    //? if neoforge {
    private static void send(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
    //?} else if forge {
    /*private static void send(ServerPlayer player, Object payload) {
        TownsteadNetwork.sendToPlayer(player, payload);
    }
    *///?}
}
