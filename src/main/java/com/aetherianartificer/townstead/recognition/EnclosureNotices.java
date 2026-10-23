package com.aetherianartificer.townstead.recognition;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/** What a player is told about an enclosure: a short line, and an outline where it is open. */
public final class EnclosureNotices {
    /** How far from a change a player may be and still be the one working on it. */
    private static final double NEARBY = 24.0;

    private EnclosureNotices() {}

    public static void problem(ServerPlayer player, SiteRequirements.Problem problem) {
        if (player == null || problem == null) return;
        Component line = switch (problem.reason()) {
            case OPEN -> Component.translatable("townstead.enclosure.open");
            case TOO_LARGE -> Component.translatable("townstead.enclosure.too_large", problem.limit());
            case TOO_SMALL -> Component.translatable("townstead.enclosure.too_small", problem.limit());
        };
        player.displayClientMessage(line, true);
        if (problem.reason() == SiteRequirements.Reason.OPEN) outline(player, problem.at());
    }

    public static void opened(ServerPlayer player, String buildingType, BlockPos gap) {
        if (player == null) return;
        player.displayClientMessage(Component.translatable("townstead.enclosure.opened", typeName(buildingType)), true);
        outline(player, gap);
    }

    public static void closed(ServerPlayer player, String buildingType) {
        if (player == null) return;
        player.displayClientMessage(Component.translatable("townstead.enclosure.closed", typeName(buildingType)), true);
    }

    public static void added(ServerPlayer player, String buildingType) {
        if (player == null) return;
        player.displayClientMessage(Component.translatable("townstead.enclosure.added", typeName(buildingType)), true);
    }

    /** The player most likely working on a change here, if any is close enough to see it. */
    public static @Nullable ServerPlayer nearest(ServerLevel level, BlockPos pos) {
        Player player = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, NEARBY, false);
        return player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
    }

    private static Component typeName(String buildingType) {
        return Component.translatable("buildingType." + buildingType);
    }

    private static void outline(ServerPlayer player, BlockPos at) {
        if (at == null) return;
        EnclosureGapS2CPayload payload = new EnclosureGapS2CPayload(at);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        //?} else if forge {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
        *///?}
    }
}
