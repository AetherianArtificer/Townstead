package com.aetherianartificer.townstead.village;

import com.aetherianartificer.townstead.api.impl.v1.ApiEvents;
import com.aetherianartificer.townstead.api.v1.model.NeedBand;
import com.aetherianartificer.townstead.api.v1.model.VillageId;
import com.aetherianartificer.townstead.api.v1.model.VillageNeedsSummary;
import net.minecraft.server.MinecraftServer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Watches each known village's aggregate need bands and posts an event when one crosses an
 * edge. Runs on the memory-lifecycle cadence, one village per pass, so a server with many
 * villages spreads the cost rather than paying it all at once.
 */
public final class VillageNeedBands {
    private static int cursor;

    private VillageNeedBands() {}

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        ResidentRegister register = ResidentRegister.get(server);
        var villages = register.knownVillages();
        if (villages.isEmpty()) return;
        cursor = Math.floorMod(cursor, villages.size());
        VillageId village = villages.get(cursor++);
        check(server, register, village);
    }

    static void check(MinecraftServer server, ResidentRegister register, VillageId village) {
        Optional<VillageNeedsSummary> summary = register.summary(server, village);
        if (summary.isEmpty()) return;
        VillageWatchSavedData watch = VillageWatchSavedData.get(server);
        String key = VillageWatchSavedData.keyOf(village);
        Map<String, String> previous = watch.needBands(key);
        Map<String, String> next = new LinkedHashMap<>();
        for (Map.Entry<String, VillageNeedsSummary.NeedStat> stat : summary.get().byNeed().entrySet()) {
            NeedBand band = stat.getValue().band();
            next.put(stat.getKey(), band.id());
            String before = previous.get(stat.getKey());
            if (before == null || before.equals(band.id())) continue;
            ApiEvents.needsBandChanged(server, village, stat.getKey(), NeedBand.byId(before), band, summary.get());
        }
        if (!next.equals(previous)) watch.putNeedBands(key, next);
    }
}
