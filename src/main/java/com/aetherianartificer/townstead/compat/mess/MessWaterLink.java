package com.aetherianartificer.townstead.compat.mess;

import com.aetherianartificer.bitofamess.api.v1.MessApiV1;
import com.aetherianartificer.bitofamess.api.v1.WaterApi;
import com.aetherianartificer.bitofamess.api.v1.WaterSurroundings;
import com.aetherianartificer.townstead.temperature.PoolClimate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.OptionalDouble;

/** Bit of a Mess's heated water, through its API (revision 2). Kept apart so an older Bit of a Mess never loads it. */
final class MessWaterLink {

    private MessWaterLink() {}

    private static WaterApi water() {
        MessApiV1 api = MessApiV1.get();
        return api.getApiRevision() >= 2 ? api.water() : null;
    }

    /** Townstead gives heated water its climate and its heaters' watts, so a bath and a room agree. */
    static void init() {
        WaterApi water = water();
        if (water == null) return;
        water.setClimate("townstead", (level, surface) -> PoolClimate.at(level, surface)
                .map(air -> new WaterSurroundings(air.water(), air.air(), air.humidity(), air.wind())));
        water.setHeaterPower("townstead", PoolClimate::watts);
    }

    static OptionalDouble poolCelsius(ServerLevel level, BlockPos pos) {
        WaterApi water = water();
        return water == null ? OptionalDouble.empty() : water.poolCelsius(level, pos);
    }

    static Optional<MessBridge.Pool> pool(ServerLevel level, BlockPos pos) {
        WaterApi water = water();
        if (water == null) return Optional.empty();
        return water.pool(level, pos).map(pool -> new MessBridge.Pool(pool.anchor().asLong(), pool.celsius(), pool.volume(),
                pool.airWatts(), pool.vapourKgPerSecond(), pool.heaters().stream().mapToLong(BlockPos::asLong).toArray()));
    }
}
