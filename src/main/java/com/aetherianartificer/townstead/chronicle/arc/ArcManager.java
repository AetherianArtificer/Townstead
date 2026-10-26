package com.aetherianartificer.townstead.chronicle.arc;

import com.aetherianartificer.townstead.chronicle.Chronicles;
import com.aetherianartificer.townstead.chronicle.model.Arc;
import com.aetherianartificer.townstead.chronicle.store.ChronicleSavedData;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lifecycle facade for story arcs. Open arcs are cached in memory (the archive
 * row is the durable copy); closing stamps the end day and updates the row.
 */
public final class ArcManager {

    private static final Map<Long, Arc> OPEN_ARCS = new ConcurrentHashMap<>();

    private ArcManager() {}

    public static Arc open(MinecraftServer server, String type, int villageId,
                           long startDay, Map<String, String> params) {
        long id = ChronicleSavedData.get(server).assignArcId();
        Arc arc = new Arc(id, type, villageId, Arc.STATUS_OPEN, startDay, startDay, params);
        OPEN_ARCS.put(id, arc);
        Chronicles.appendArc(server, arc);
        return arc;
    }

    public static @Nullable Arc openArc(long arcId) {
        return OPEN_ARCS.get(arcId);
    }

    public static void close(MinecraftServer server, long arcId, long endDay) {
        Arc arc = OPEN_ARCS.remove(arcId);
        if (arc == null) return;
        Chronicles.appendArc(server, arc.closed(endDay));
    }

    public static int openCount() {
        return OPEN_ARCS.size();
    }

    public static void clearAll() {
        OPEN_ARCS.clear();
    }
}
