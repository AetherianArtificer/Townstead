package com.aetherianartificer.townstead.performance;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/** Small dependency-free embodiment vocabulary used by mappings and safe fallbacks. */
public final class VanillaPerformanceProvider implements PerformanceProvider {
    public static final String ID = "townstead:vanilla";
    private static final ResourceLocation SWING = ResourceLocation.tryParse("townstead:swing");
    private static final ResourceLocation STAND = ResourceLocation.tryParse("townstead:stand");
    private static final Set<ResourceLocation> SUPPORTED = Set.of(SWING, STAND);

    @Override public String id() { return ID; }
    @Override public int priority() { return -100; }
    @Override public boolean supports(PerformanceRequest request) { return SUPPORTED.contains(request.performance()); }

    @Override
    public @Nullable PerformanceHandle play(ServerLevel level, PerformanceRequest request) {
        if (SWING.equals(request.performance())) request.actor().swing(InteractionHand.MAIN_HAND);
        else if (STAND.equals(request.performance()) && request.actor() instanceof Mob mob) mob.getNavigation().stop();
        else return null;
        return PerformanceHandle.NONE;
    }
}
