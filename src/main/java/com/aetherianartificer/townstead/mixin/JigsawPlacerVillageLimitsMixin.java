package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.culture.VillageLayouts;
import com.aetherianartificer.townstead.mixin.accessor.SinglePoolElementAccessor;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies a village layout's ranges to the buildings a village tries next: skips those at their
 * maximum and tries those short of their minimum first. Reorders the shuffled list rather than
 * redirecting the call, so another mod reweighting it still applies.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement$Placer")
public abstract class JigsawPlacerVillageLimitsMixin {
    //? if neoforge {
    @Shadow @Final private List<? super PoolElementStructurePiece> pieces;

    @ModifyExpressionValue(method = "tryPlacingChildren", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/pools/StructureTemplatePool;getShuffledTemplates(Lnet/minecraft/util/RandomSource;)Ljava/util/List;"))
    //?} else {
    /*@Shadow(remap = false) @Final private List<? super PoolElementStructurePiece> f_210319_;

    @ModifyExpressionValue(method = "m_227264_", remap = false, at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/world/level/levelgen/structure/pools/StructureTemplatePool;m_227362_(Lnet/minecraft/util/RandomSource;)Ljava/util/List;"))
    *///?}
    private List<StructurePoolElement> townstead$limitBuildings(List<StructurePoolElement> shuffled) {
        if (!VillageLayouts.hasLimits()) return shuffled;
        Map<ResourceLocation, Integer> placed = new HashMap<>();
        //? if neoforge {
        List<? super PoolElementStructurePiece> pieces = this.pieces;
        //?} else {
        /*List<? super PoolElementStructurePiece> pieces = this.f_210319_;
        *///?}
        for (Object piece : pieces) {
            if (piece instanceof PoolElementStructurePiece element) {
                ResourceLocation id = template(element.getElement());
                if (id != null) placed.merge(id, 1, Integer::sum);
            }
        }
        return VillageLayouts.arrange(shuffled, JigsawPlacerVillageLimitsMixin::template, placed);
    }

    private static @Nullable ResourceLocation template(StructurePoolElement element) {
        if (!(element instanceof SinglePoolElement single)) return null;
        return ((SinglePoolElementAccessor) single).townstead$template().left().orElse(null);
    }
}
