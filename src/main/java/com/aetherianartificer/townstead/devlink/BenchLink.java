package com.aetherianartificer.townstead.devlink;

import com.aetherianartificer.townstead.Townstead;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Bench Link: a dev-only, loopback HTTP server that lets the Benchstead Blockbench plugin read the
 * running game's roots, rigs and attachments. Off by default; root authors turn it on with the
 * Bench Link item or {@code /townstead bench open}, both of which need permission level 2.
 *
 * <p>While it runs, {@code <game dir>/townstead/bench-link.json} holds the port and session token
 * so the plugin can connect without the author typing either. Stopping deletes the file. The
 * server lives on the logical server, so it serves the integrated server in single player and a
 * dedicated server on the same machine alike.</p>
 */
public final class BenchLink {

    /** Bumped when an endpoint's shape changes incompatibly; the plugin checks it on connect. */
    public static final int PROTOCOL = 1;

    private static volatile @Nullable MinecraftServer server;
    private static volatile @Nullable BenchHttp http;
    private static volatile @Nullable String token;
    private static volatile int subjectId = -1;

    private BenchLink() {}

    public static boolean running() {
        return http != null;
    }

    public static int port() {
        BenchHttp current = http;
        return current == null ? 0 : current.port();
    }

    public static String token() {
        String current = token;
        return current == null ? "" : current;
    }

    public static int sessions() {
        BenchHttp current = http;
        return current == null ? 0 : current.streamCount();
    }

    static @Nullable MinecraftServer server() {
        return server;
    }

    /** Starts the server for {@code srv}. Returns false when it was already running or failed to bind. */
    public static synchronized boolean start(MinecraftServer srv) {
        if (http != null) return false;
        byte[] secret = new byte[24];
        new SecureRandom().nextBytes(secret);
        String newToken = HexFormat.of().formatHex(secret);
        try {
            server = srv;
            token = newToken;
            http = new BenchHttp(newToken, new BenchApi(srv));
        } catch (IOException e) {
            Townstead.LOGGER.error("Bench Link could not open a local port", e);
            server = null;
            token = null;
            return false;
        }
        writeDiscovery(srv);
        Townstead.LOGGER.info("Bench Link listening on {} (discovery file {})", http.boundAddress(), discoveryFile(srv));
        BenchLinkStatus.broadcast(srv);
        return true;
    }

    /** Stops the server, closes every Blockbench session and removes the discovery file. */
    public static synchronized boolean stop() {
        BenchHttp current = http;
        MinecraftServer srv = server;
        if (current == null) return false;
        current.close();
        http = null;
        token = null;
        subjectId = -1;
        if (srv != null) {
            deleteDiscovery(srv);
            BenchLinkStatus.broadcast(srv);
        }
        server = null;
        Townstead.LOGGER.info("Bench Link stopped");
        return true;
    }

    /** The preview subject's entity id, or -1. */
    public static int subjectId() {
        return subjectId;
    }

    /** Makes {@code entity} the preview subject that the plugin follows. */
    public static void setSubject(@Nullable Entity entity) {
        subjectId = entity == null ? -1 : entity.getId();
        JsonObject event = new JsonObject();
        event.addProperty("id", subjectId);
        if (entity != null) event.addProperty("name", entity.getName().getString());
        emit("subject", event);
        MinecraftServer srv = server;
        if (srv != null) BenchLinkStatus.broadcast(srv);
    }

    /** Called after a data reload so open Blockbench sessions re-read the roots. */
    public static void onReload() {
        emit("reload", new JsonObject());
    }

    /** Sends one SSE event to every connected session; a no-op while stopped. */
    public static void emit(String event, JsonObject data) {
        BenchHttp current = http;
        if (current != null) current.broadcast(event, data.toString());
    }

    static void onStreamsChanged() {
        MinecraftServer srv = server;
        if (srv != null) srv.execute(() -> BenchLinkStatus.broadcast(srv));
    }

    static boolean mayUse(ServerPlayer player) {
        return player.hasPermissions(2);
    }

    private static Path discoveryFile(MinecraftServer srv) {
        //? if >=1.21 {
        Path root = srv.getServerDirectory();
        //?} else {
        /*Path root = srv.getServerDirectory().toPath();
        *///?}
        return root.toAbsolutePath().normalize().resolve(Townstead.MOD_ID).resolve("bench-link.json");
    }

    private static void writeDiscovery(MinecraftServer srv) {
        JsonObject json = new JsonObject();
        json.addProperty("protocol", PROTOCOL);
        json.addProperty("port", port());
        json.addProperty("token", token());
        json.addProperty("world", BenchApi.worldName(srv));
        json.addProperty("pid", ProcessHandle.current().pid());
        Path file = discoveryFile(srv);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Townstead.LOGGER.warn("Bench Link could not write {}; connect with the port and token by hand", file, e);
        }
    }

    private static void deleteDiscovery(MinecraftServer srv) {
        try {
            Files.deleteIfExists(discoveryFile(srv));
        } catch (IOException e) {
            Townstead.LOGGER.warn("Bench Link could not delete its discovery file", e);
        }
    }
}
