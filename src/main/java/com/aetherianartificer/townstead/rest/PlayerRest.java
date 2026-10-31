package com.aetherianartificer.townstead.rest;

import com.aetherianartificer.townstead.root.gene.types.RestGeneType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The rest-spot rules for players: who may lie down where, at what time of day, and whether it
 * sets their spawn. Loader events call in with the vanilla answer and get the rest spot's.
 */
public final class PlayerRest {
    private PlayerRest() {}

    public enum Verdict { VANILLA, ALLOW, NOT_NOW, NOT_YOURS, PREVENTED }

    /** Whether the player may lie down in this block now; VANILLA leaves vanilla's answer. */
    public static Verdict canSleep(ServerPlayer player, BlockPos pos, BlockState state) {
        RestGeneType.Instance prevent = RestGenes.preventing(player);
        if (prevent != null) {
            if (!prevent.message().isEmpty()) player.displayClientMessage(Component.translatable(prevent.message()), true);
            return Verdict.PREVENTED;
        }
        if (RestSpots.all().isEmpty()) return Verdict.VANILLA;
        if (!RestSpots.permits(player, state)) {
            player.displayClientMessage(Component.translatable("message.townstead.rest.not_yours"), true);
            return Verdict.NOT_YOURS;
        }
        RestSpot spot = RestSpots.used(player, state);
        if (spot == null) return Verdict.VANILLA;
        boolean day = isDay(player.level());
        return switch (spot.time()) {
            case NIGHT -> Verdict.VANILLA;
            case ANY -> Verdict.ALLOW;
            case DAY -> {
                if (day) yield Verdict.ALLOW;
                player.displayClientMessage(Component.translatable("message.townstead.rest.day_only"), true);
                yield Verdict.NOT_NOW;
            }
        };
    }

    /** Whether a sleeping player may stay asleep; null leaves vanilla's answer. */
    public static @Nullable Boolean canKeepSleeping(Player player) {
        RestSpot spot = RestEvents.sleepingIn(player);
        if (spot == null) return null;
        return switch (spot.time()) {
            case NIGHT -> null;
            case ANY -> true;
            case DAY -> isDay(player.level());
        };
    }

    /** Whether setting the spawn here should be stopped: a rest spot that does not set it. */
    public static boolean blocksSpawn(Player player, @Nullable BlockPos pos, boolean forced) {
        if (forced || pos == null || RestSpots.all().isEmpty()) return false;
        RestSpot spot = RestSpots.used(player, player.level().getBlockState(pos));
        return spot != null && !spot.setsSpawn();
    }

    private static boolean isDay(Level level) {
        long time = level.getDayTime() % 24000L;
        return time < 12542L || time > 23459L;
    }
}
