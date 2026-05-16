package com.aetherianartificer.townstead.shift.template;

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
 * (world SavedData) shift templates. Built-ins live in a static map populated
 * by the JSON reload listener; user templates live in
 * {@link ShiftTemplateSavedData}.
 */
public final class ShiftTemplateRegistry {

    private static final Map<ResourceLocation, ShiftTemplate> BUILT_INS = new LinkedHashMap<>();
    private static final AtomicReference<MinecraftServer> SERVER_REF = new AtomicReference<>();
    private static volatile Consumer<MinecraftServer> CHANGE_LISTENER;

    private ShiftTemplateRegistry() {}

    public static void setServer(MinecraftServer server) {
        SERVER_REF.set(server);
    }

    public static void clearServer() {
        SERVER_REF.set(null);
    }

    public static void setChangeListener(Consumer<MinecraftServer> listener) {
        CHANGE_LISTENER = listener;
    }

    public static void setBuiltIns(Collection<ShiftTemplate> templates) {
        synchronized (BUILT_INS) {
            BUILT_INS.clear();
            List<ShiftTemplate> sorted = new ArrayList<>(templates);
            sorted.sort(Comparator.comparing(t -> t.id().toString()));
            for (ShiftTemplate t : sorted) BUILT_INS.put(t.id(), t);
        }
        fireChangedAsync();
    }

    public static List<ShiftTemplate> getBuiltIns() {
        synchronized (BUILT_INS) {
            return new ArrayList<>(BUILT_INS.values());
        }
    }

    public static Optional<ShiftTemplate> findBuiltIn(ResourceLocation id) {
        synchronized (BUILT_INS) {
            return Optional.ofNullable(BUILT_INS.get(id));
        }
    }

    /** Built-ins first (alpha by id), then user templates alpha by display name. */
    public static List<ShiftTemplate> combinedFor(MinecraftServer server) {
        List<ShiftTemplate> out = new ArrayList<>();
        synchronized (BUILT_INS) {
            out.addAll(BUILT_INS.values());
        }
        if (server != null) {
            List<ShiftTemplate> user = ShiftTemplateSavedData.get(server).snapshot();
            user.sort(Comparator.comparing(ShiftTemplate::displayName, String.CASE_INSENSITIVE_ORDER));
            out.addAll(user);
        }
        return Collections.unmodifiableList(out);
    }

    public static Optional<ShiftTemplate> resolve(MinecraftServer server, ResourceLocation id) {
        Optional<ShiftTemplate> builtIn = findBuiltIn(id);
        if (builtIn.isPresent()) return builtIn;
        if (server == null) return Optional.empty();
        return ShiftTemplateSavedData.get(server).get(id);
    }

    public static void fireChangedAsync() {
        MinecraftServer server = SERVER_REF.get();
        Consumer<MinecraftServer> listener = CHANGE_LISTENER;
        if (server == null || listener == null) return;
        server.execute(() -> listener.accept(server));
    }
}
