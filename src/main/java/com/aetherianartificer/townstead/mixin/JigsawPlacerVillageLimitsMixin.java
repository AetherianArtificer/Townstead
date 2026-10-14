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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Skips buildings that already fill their per-village maximum from a village layout. Filters the
 * shuffled list rather than redirecting the call, so another mod reweighting it still applies.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement$Placer")
public abstract class JigsawPlacerVillageLimitsMixin {
    @Shadow @Final private List<? super PoolElementStructurePiece> pieces;

    @ModifyExpressionValue(method = "tryPlacingChildren", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/pools/StructureTemplatePool;getShuffledTemplates(Lnet/minecraft/util/RandomSource;)Ljava/util/List;"))
    private List<StructurePoolElement> townstead$limitBuildings(List<StructurePoolElement> shuffled) {
        if (!VillageLayouts.hasLimits()) return shuffled;
        Map<ResourceLocation, Integer> placed = new HashMap<>();
        for (Object piece : pieces) {
            if (piece instanceof PoolElementStructurePiece element) {
                ResourceLocation id = template(element.getElement());
                if (id != null) placed.merge(id, 1, Integer::sum);
            }
        }
        List<StructurePoolElement> allowed = new ArrayList<>(shuffled.size());
        for (StructurePoolElement element : shuffled) {
            ResourceLocation id = template(element);
            Integer max = VillageLayouts.max(id);
            if (max == null || placed.getOrDefault(id, 0) < max) allowed.add(element);
        }
        return allowed;
    }

    private static @Nullable ResourceLocation template(StructurePoolElement element) {
        if (!(element instanceof SinglePoolElement single)) return null;
        return ((SinglePoolElementAccessor) single).townstead$template().left().orElse(null);
    }
}
