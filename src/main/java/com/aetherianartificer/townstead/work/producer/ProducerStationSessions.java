package com.aetherianartificer.townstead.work.producer;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import com.aetherianartificer.townstead.work.station.ShiftEndPolicy;
import com.aetherianartificer.townstead.work.station.WorkstationDef;
import com.aetherianartificer.townstead.work.station.Workstations;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProducerStationSessions {
    public record SessionSnapshot(
            UUID owner,
            @Nullable ResourceLocation recipeId,
            @Nullable ResourceLocation recipeOutputId,
            int expectedOutputCount,
            Map<ResourceLocation, Integer> stagedInputs,
            long untilTick,
            ShiftEndPolicy shiftEnd
    ) {
        public boolean isOwner(UUID uuid) {
            return owner != null && owner.equals(uuid);
        }

        public boolean mayResume(boolean onShift) {
            return onShift || shiftEnd == ShiftEndPolicy.FINISH;
        }
    }

    private static final Map<String, SessionSnapshot> SESSIONS = new ConcurrentHashMap<>();

    private ProducerStationSessions() {}

    public static void beginOrRefresh(
            ServerLevel level,
            UUID owner,
            BlockPos pos,
            @Nullable ResourceLocation recipeId,
            @Nullable ResourceLocation recipeOutputId,
            int expectedOutputCount,
            Map<ResourceLocation, Integer> stagedInputs,
            long untilTick
    ) {
        if (level == null || owner == null || pos == null) return;
        Map<ResourceLocation, Integer> snapshot = stagedInputs == null
                ? Map.of()
                : Collections.unmodifiableMap(new HashMap<>(stagedInputs));
        WorkstationDef definition = Workstations.byState(level.getBlockState(pos));
        ShiftEndPolicy shiftEnd = definition == null
                ? ShiftEndPolicy.FINISH : definition.shiftEnd();
        SESSIONS.put(
                ProducerClaimKeys.claimKey(level.dimension().location().toString(), pos.asLong()),
                new SessionSnapshot(owner, recipeId, recipeOutputId, Math.max(0, expectedOutputCount),
                        snapshot, untilTick, shiftEnd)
        );
    }

    public static @Nullable SessionSnapshot snapshot(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return null;
        String key = ProducerClaimKeys.claimKey(level.dimension().location().toString(), pos.asLong());
        SessionSnapshot session = SESSIONS.get(key);
        if (session == null) return null;
        if (session.untilTick() <= level.getGameTime()) {
            SESSIONS.remove(key);
            return null;
        }
        return session;
    }

    /** Whether the owner has a committed cycle whose station requires after-shift attendance. */
    public static boolean hasOwnedFinishingSession(ServerLevel level, UUID owner) {
        if (level == null || owner == null) return false;
        String prefix = level.dimension().location() + "|";
        long gameTime = level.getGameTime();
        boolean found = false;
        for (Map.Entry<String, SessionSnapshot> entry : SESSIONS.entrySet()) {
            if (!entry.getKey().startsWith(prefix)) continue;
            SessionSnapshot session = entry.getValue();
            if (session.untilTick() <= gameTime) {
                SESSIONS.remove(entry.getKey(), session);
                continue;
            }
            if (session.isOwner(owner) && session.shiftEnd() == ShiftEndPolicy.FINISH) found = true;
        }
        return found;
    }

    public static void release(ServerLevel level, UUID owner, BlockPos pos) {
        if (level == null || owner == null || pos == null) return;
        String key = ProducerClaimKeys.claimKey(level.dimension().location().toString(), pos.asLong());
        SessionSnapshot session = SESSIONS.get(key);
        if (session == null || !session.isOwner(owner)) return;
        SESSIONS.remove(key);
    }
}
