package com.aetherianartificer.townstead.api.impl.v1;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.api.v1.model.VillageId;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/** Shared plumbing for the v1 implementation: logging, write gating, id parsing, village lookup. */
final class ApiSupport {
    private ApiSupport() {}

    static String modVersion() {
        try {
            //? if neoforge {
            return net.neoforged.fml.ModList.get().getModContainerById(Townstead.MOD_ID)
                    .map(c -> c.getModInfo().getVersion().toString()).orElse("unknown");
            //?} else if forge {
            /*return net.minecraftforge.fml.ModList.get().getModContainerById(Townstead.MOD_ID)
                    .map(c -> c.getModInfo().getVersion().toString()).orElse("unknown");
            *///?}
        } catch (Throwable t) {
            return "unknown";
        }
    }

    /** Logs a swallowed failure. Every API method is total, so this is the only place they surface. */
    static void swallow(String where, Throwable t) {
        boolean verbose;
        try {
            verbose = TownsteadConfig.DEBUG_LOGGING.get();
        } catch (Throwable ignored) {
            verbose = false;
        }
        if (verbose) Townstead.LOGGER.warn("[TownsteadApi] {} failed", where, t);
        else Townstead.LOGGER.debug("[TownsteadApi] {} failed: {}", where, t.toString());
    }

    /** Whether writes attributed to {@code source} are accepted; a null source is always accepted. */
    static boolean writesAllowed(@Nullable ResourceLocation source) {
        return source == null || !TownsteadConfig.isApiSourceDenied(source.getNamespace());
    }

    static @Nullable ResourceLocation parseId(@Nullable String id) {
        if (id == null || id.isBlank()) return null;
        return ResourceLocation.tryParse(id.contains(":") ? id : "minecraft:" + id);
    }

    static @Nullable VillagerEntityMCA villager(@Nullable Entity entity) {
        return entity instanceof VillagerEntityMCA v ? v : null;
    }

    static @Nullable ServerLevel serverLevel(@Nullable Entity entity) {
        return entity != null && entity.level() instanceof ServerLevel level ? level : null;
    }

    static VillageId villageId(ServerLevel level, Village village) {
        return new VillageId(level.dimension().location(), village.getId());
    }

    static Optional<Village> findVillage(MinecraftServer server, VillageId id) {
        if (server == null || id == null) return Optional.empty();
        for (ServerLevel level : server.getAllLevels()) {
            if (!level.dimension().location().equals(id.dimension())) continue;
            return VillageManager.get(level).getOrEmpty(id.villageId());
        }
        return Optional.empty();
    }

    static @Nullable ServerLevel levelOf(MinecraftServer server, VillageId id) {
        if (server == null || id == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().equals(id.dimension())) return level;
        }
        return null;
    }

    /** Home village first, nearest village second, matching the chronicle's own resolution. */
    static Optional<VillageId> homeOf(@Nullable Entity entity) {
        ServerLevel level = serverLevel(entity);
        if (level == null) return Optional.empty();
        try {
            if (entity instanceof VillagerEntityMCA villager) {
                Optional<Village> home = villager.getResidency().getHomeVillage();
                if (home.isPresent()) return Optional.of(villageId(level, home.get()));
            }
            return Village.findNearest(entity).map(v -> villageId(level, v));
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    static @Nullable LivingEntity findLoaded(MinecraftServer server, UUID id) {
        if (server == null || id == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof LivingEntity living) return living;
        }
        return null;
    }

    static long gameTime(Entity entity) {
        return entity.level() == null ? 0L : entity.level().getGameTime();
    }
}
