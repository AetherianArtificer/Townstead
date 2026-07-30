package com.aetherianartificer.townstead.assign;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry, loader and provider for datapack wheel actions.
 *
 * <p>Loads {@code data/<ns>/wheel_action/*.json}; one bad file warns and is skipped rather than
 * taking the pack down with it.</p>
 */
public final class WheelActions extends SimpleJsonResourceReloadListener implements AssignableProvider {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(Townstead.MOD_ID + "/WheelActions");
    private static final Gson GSON = new Gson();
    private static volatile Map<ResourceLocation, WheelAction> ENTRIES = Map.of();

    public WheelActions() {
        super(GSON, "wheel_action");
    }

    public static WheelAction byId(ResourceLocation id) {
        return id == null ? null : ENTRIES.get(id);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<String, String> lang = DataPackLang.loadLangIndex(resourceManager);
        Map<ResourceLocation, WheelAction> parsed = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation file = entry.getKey();
            try {
                parsed.put(file, WheelAction.parse(file,
                        GsonHelper.convertToJsonObject(entry.getValue(), file.toString()), lang));
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse wheel_action {}: {}", file, ex.getMessage());
            }
        }
        ENTRIES = Map.copyOf(parsed);
        LOGGER.info("Loaded {} wheel actions", parsed.size());
    }

    @Override
    public void collect(ServerPlayer player, List<Assignable> out) {
        for (WheelAction action : ENTRIES.values()) {
            if (!allowed(player, action)) continue;
            out.add(new Assignable(action.id(), action.name(), action.icon(), action.source(),
                    action.kind(), action.cooldownTicks(), 0, "", 0, clientValue(action)));
        }
    }

    @Override
    public boolean invoke(ServerPlayer player, ResourceLocation id) {
        WheelAction action = ENTRIES.get(id);
        if (action == null) return false;
        // Re-checked on invoke, not just on collect. A catalogue is a snapshot, and the advancement
        // that made this offer valid can be revoked between browsing and pressing.
        if (!allowed(player, action)) return false;
        // A declared cooldown was parsed, shipped and DRAWN, and enforced by nobody, so an action
        // advertising a minute ran as fast as the key repeated. The wheel's ring was describing a
        // rule that did not exist.
        if (!AssignCooldowns.isReady(player, id, player.level().getGameTime())) return false;
        return switch (action.kind()) {
            case COMMAND -> runCommand(player, action);
            // The client performs these and never asks us to, so arriving here means a stale or
            // forged press. Reporting success would have made an unimplemented ITEM kind look like
            // it worked, which is worse than not having it.
            default -> false;
        };
    }

    /** Only client-performed kinds hand anything down; a command's text stays on the server. */
    private static String clientValue(WheelAction action) {
        return action.kind() == Assignable.Kind.KEYBIND ? action.value() : "";
    }

    /**
     * Whether this player may have this action at all.
     *
     * <p>Gating lives here rather than in the UI because a client can send any slot it likes. A
     * datapack that could run arbitrary commands for anyone would be a way to hand every player
     * operator rights through a resource file.</p>
     */
    private static boolean allowed(ServerPlayer player, WheelAction action) {
        if (action.requiresAdvancement() == null) return true;
        //? if >=1.21 {
        net.minecraft.advancements.AdvancementHolder holder =
                player.server.getAdvancements().get(action.requiresAdvancement());
        //?} else {
        /*net.minecraft.advancements.Advancement holder =
                player.server.getAdvancements().getAdvancement(action.requiresAdvancement());
        *///?}
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    /**
     * Runs the command as the SERVER, with the player as context.
     *
     * <p>Deliberately not as the player: running with their permission level would make every
     * datapack action useless for anything interesting, and running with elevated rights AS them
     * would leak those rights to anything else reading the command source. The server runs it,
     * silently, positioned on the player.</p>
     *
     * <p>At the server's OWN function permission level, not the console's. {@code
     * createCommandSourceStack} is level 4, and {@code allowed} defaults to true when a file
     * declares no {@code requires}, so any pack dropped on a server could hand every player an
     * op-level command on a keypress. This is the level an operator already chose for datapack
     * functions, which is exactly what these are.</p>
     */
    private static boolean runCommand(ServerPlayer player, WheelAction action) {
        var server = player.server;
        var source = server.createCommandSourceStack()
                .withPermission(server.getFunctionCompilationLevel())
                .withEntity(player)
                .withLevel(player.serverLevel())
                .withPosition(player.position())
                .withRotation(player.getRotationVector())
                .withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, action.value());
        AssignCooldowns.start(player, action.id(), action.cooldownTicks());
        return true;
    }
}
