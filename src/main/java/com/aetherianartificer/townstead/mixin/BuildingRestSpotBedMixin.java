package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.rest.RestSpots;
import net.conczin.mca.resources.data.BuildingType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Optional;

/** A home rest spot (a coffin, a bedroll) fills a building's bed requirement, so a room with one is a house. */
@Mixin(BuildingType.class)
public abstract class BuildingRestSpotBedMixin {
    private static final ResourceLocation TOWNSTEAD$BEDS = ResourceLocation.tryParse("minecraft:beds");

    @Shadow(remap = false)
    public abstract Map<ResourceLocation, Integer> getGroups();

    @Inject(method = "getGroupForBlock(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;",
            at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void townstead$restSpotById(ResourceLocation block, CallbackInfoReturnable<Optional<ResourceLocation>> cir) {
        if (cir.getReturnValue().isPresent() || block == null || !getGroups().containsKey(TOWNSTEAD$BEDS)) return;
        BuiltInRegistries.BLOCK.getOptional(block).ifPresent(found -> {
            if (RestSpots.isHomeBlock(found)) cir.setReturnValue(Optional.of(TOWNSTEAD$BEDS));
        });
    }

    @Inject(method = "getGroupForBlock(Lnet/minecraft/world/level/block/state/BlockState;)Ljava/util/Optional;",
            at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void townstead$restSpotByState(BlockState state, CallbackInfoReturnable<Optional<ResourceLocation>> cir) {
        if (cir.getReturnValue().isPresent() || state == null || !getGroups().containsKey(TOWNSTEAD$BEDS)) return;
        if (RestSpots.isHome(state)) cir.setReturnValue(Optional.of(TOWNSTEAD$BEDS));
    }
}
