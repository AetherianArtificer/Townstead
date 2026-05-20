package com.aetherianartificer.townstead.shift.weekplan;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Server-side registry combining built-in (data pack JSON) and user-defined
 * (world SavedData) week plans. Mirrors {@code ShiftTemplateRegistry}.
 */
public final class WeekPlanRegistry {

    private static final Map<ResourceLocation, WeekPlan> BUILT_INS = new LinkedHashMap<>();
    private static final AtomicReference<MinecraftServer> SERVER_REF = new AtomicReference<>();
    private static volatile Consumer<MinecraftServer> CHANGE_LISTENER;

    private WeekPlanRegistry() {}

    public static void setServer(MinecraftServer server) {
        SERVER_REF.set(server);
    }

    public static void clearServer() {
        SERVER_REF.set(null);
    }

    public static void setChangeListener(Consumer<MinecraftServer> listener) {
        CHANGE_LISTENER = listener;
    }

    public static void setBuiltIns(Collection<WeekPlan> plans) {
        synchronized (BUILT_INS) {
            BUILT_INS.clear();
            List<WeekPlan> sorted = new ArrayList<>(plans);
            sorted.sort(Comparator.comparing(p -> p.id().toString()));
            for (WeekPlan p : sorted) BUILT_INS.put(p.id(), p);
        }
        fireChangedAsync();
    }

    public static List<WeekPlan> getBuiltIns() {
        synchronized (BUILT_INS) {
            return new ArrayList<>(BUILT_INS.values());
        }
    }

    public static Optional<WeekPlan> findBuiltIn(ResourceLocation id) {
        synchronized (BUILT_INS) {
            return Optional.ofNullable(BUILT_INS.get(id));
        }
    }

    /** Built-ins first (alpha by id), then user plans alpha by display name. */
    public static List<WeekPlan> combinedFor(MinecraftServer server) {
        List<WeekPlan> out = new ArrayList<>();
        synchronized (BUILT_INS) {
            out.addAll(BUILT_INS.values());
        }
        if (server != null) {
            List<WeekPlan> user = WeekPlanSavedData.get(server).snapshot();
            user.sort(Comparator.comparing(WeekPlan::displayName, String.CASE_INSENSITIVE_ORDER));
            out.addAll(user);
        }
        return Collections.unmodifiableList(out);
    }

    public static Optional<WeekPlan> resolve(MinecraftServer server, ResourceLocation id) {
        Optional<WeekPlan> builtIn = findBuiltIn(id);
        if (builtIn.isPresent()) return builtIn;
        if (server == null) return Optional.empty();
        return WeekPlanSavedData.get(server).get(id);
    }

    public static void fireChangedAsync() {
        MinecraftServer server = SERVER_REF.get();
        Consumer<MinecraftServer> listener = CHANGE_LISTENER;
        if (server == null || listener == null) return;
        server.execute(() -> listener.accept(server));
    }
}
