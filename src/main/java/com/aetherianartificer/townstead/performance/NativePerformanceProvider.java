package com.aetherianartificer.townstead.performance;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/** Built-in clip provider. It has no optional animation-mod dependency. */
public final class NativePerformanceProvider implements PerformanceProvider {
    public static final String ID = "townstead_performance:native";
    public static final String CLIP_NAMESPACE = "townstead_performance";

    @Override public String id() { return ID; }
    @Override public int priority() { return 50; }

    @Override
    public boolean supports(PerformanceRequest request) {
        return CLIP_NAMESPACE.equals(request.performance().getNamespace());
    }

    @Override public boolean supportsMapped(PerformanceRequest request) {
        // The server cannot inspect client resource packs. An explicit native mapping selects
        // the backend; the client validates the clip ID against its loaded animation registry.
        return true;
    }

    @Override
    public @Nullable PerformanceHandle play(ServerLevel level, PerformanceRequest request) {
        NativePerformanceS2CPayload start = new NativePerformanceS2CPayload(request.actor().getId(),
                request.channel(), request.performance().toString(), request.durationTicks(), request.priority());
        send(request, start);
        return () -> send(request, new NativePerformanceS2CPayload(request.actor().getId(),
                request.channel(), "", 0, request.priority()));
    }

    private static void send(PerformanceRequest request, NativePerformanceS2CPayload payload) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(request.actor(), payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToTrackingEntity(request.actor(), payload);
        if (request.actor() instanceof ServerPlayer player) {
            com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
        }
        *///?}
    }
}
