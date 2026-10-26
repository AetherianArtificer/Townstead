package com.aetherianartificer.townstead.clothing;

import com.aetherianartificer.townstead.spirit.SpiritTotals;
import com.aetherianartificer.townstead.spirit.VillageSpiritAggregator;
import com.aetherianartificer.townstead.spirit.VillageSpiritCache;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.ToDoubleFunction;

/** The community spirit shares of the village an entity stands in, as the fashion bias reads them. */
public final class VillageSpirits {

    public static final ToDoubleFunction<String> NONE = axis -> 0.0;

    private VillageSpirits() {}

    public static @Nullable Village villageOf(@Nullable LivingEntity entity) {
        if (entity == null || !(entity.level() instanceof ServerLevel level)) return null;
        Optional<Village> village = VillageManager.get(level).findNearestVillage(entity.blockPosition(), Village.MERGE_MARGIN);
        return village.orElse(null);
    }

    public static SpiritTotals totalsOf(@Nullable LivingEntity entity) {
        Village village = villageOf(entity);
        if (village == null) return SpiritTotals.empty();
        ServerLevel level = (ServerLevel) entity.level();
        VillageSpiritCache.Entry cached = VillageSpiritCache.get(level, village.getId());
        if (cached != null) return cached.totals();
        var snapshot = VillageSpiritAggregator.snapshotFor(level, village);
        SpiritTotals totals = snapshot.totals();
        VillageSpiritCache.put(level, village.getId(),
                new VillageSpiritCache.Entry(totals, VillageSpiritAggregator.readoutFor(totals), snapshot.contributors()));
        return totals;
    }

    /** Share per axis, 0 to 1, of the village the entity stands in; zero everywhere outside one. */
    public static ToDoubleFunction<String> sharesOf(@Nullable LivingEntity entity) {
        SpiritTotals totals = totalsOf(entity);
        if (totals.total() <= 0) return NONE;
        return totals::shareOf;
    }

    /** The axis with the greatest share, or null when the village has no spirit yet. */
    public static @Nullable String dominantOf(@Nullable LivingEntity entity) {
        SpiritTotals totals = totalsOf(entity);
        if (totals.total() <= 0) return null;
        String best = null;
        int bestPoints = 0;
        for (var e : totals.perSpirit().entrySet()) {
            if (e.getValue() != null && e.getValue() > bestPoints) {
                bestPoints = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }
}
