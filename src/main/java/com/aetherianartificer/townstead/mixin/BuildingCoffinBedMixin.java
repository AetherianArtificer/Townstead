package com.aetherianartificer.townstead.mixin;

import net.conczin.mca.resources.data.BuildingType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Optional;

/** A Vampirism coffin fills a building's bed requirement, so a room with one is a house. */
@Mixin(BuildingType.class)
public abstract class BuildingCoffinBedMixin {
    private static final ResourceLocation TOWNSTEAD$BEDS = ResourceLocation.tryParse("minecraft:beds");
    private static final TagKey<Block> TOWNSTEAD$COFFINS =
            TagKey.create(Registries.BLOCK, ResourceLocation.tryParse("vampirism:coffin"));

    @Shadow(remap = false)
    public abstract Map<ResourceLocation, Integer> getGroups();

    @Inject(method = "getGroupForBlock(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;",
            at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void townstead$coffinById(ResourceLocation block, CallbackInfoReturnable<Optional<ResourceLocation>> cir) {
        if (cir.getReturnValue().isPresent() || block == null) return;
        BuiltInRegistries.BLOCK.getOptional(block).ifPresent(found -> {
            if (found.defaultBlockState().is(TOWNSTEAD$COFFINS) && getGroups().containsKey(TOWNSTEAD$BEDS)) {
                cir.setReturnValue(Optional.of(TOWNSTEAD$BEDS));
            }
        });
    }

    @Inject(method = "getGroupForBlock(Lnet/minecraft/world/level/block/state/BlockState;)Ljava/util/Optional;",
            at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void townstead$coffinByState(BlockState state, CallbackInfoReturnable<Optional<ResourceLocation>> cir) {
        if (cir.getReturnValue().isPresent() || state == null) return;
        if (state.is(TOWNSTEAD$COFFINS) && getGroups().containsKey(TOWNSTEAD$BEDS)) {
            cir.setReturnValue(Optional.of(TOWNSTEAD$BEDS));
        }
    }
}
