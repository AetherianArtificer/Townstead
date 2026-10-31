package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.culture.VillageLayouts;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Plans a staged culture village with its stage's jigsaw depth and street reach. */
@Mixin(JigsawStructure.class)
public abstract class JigsawStructureVillageStageMixin {
    //? if neoforge {
    @ModifyExpressionValue(method = "findGenerationPoint", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/level/levelgen/structure/structures/JigsawStructure;maxDepth:I"))
    //?} else {
    /*@ModifyExpressionValue(method = "m_214086_", remap = false, at = @At(value = "FIELD", remap = false,
            target = "Lnet/minecraft/world/level/levelgen/structure/structures/JigsawStructure;f_227607_:I"))
    *///?}
    private int townstead$stageDepth(int depth) {
        VillageLayouts.Stage stage = VillageLayouts.planning();
        return stage != null ? stage.size() : depth;
    }

    //? if neoforge {
    @ModifyExpressionValue(method = "findGenerationPoint", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/level/levelgen/structure/structures/JigsawStructure;maxDistanceFromCenter:I"))
    //?} else {
    /*@ModifyExpressionValue(method = "m_214086_", remap = false, at = @At(value = "FIELD", remap = false,
            target = "Lnet/minecraft/world/level/levelgen/structure/structures/JigsawStructure;f_227611_:I"))
    *///?}
    private int townstead$stageReach(int reach) {
        VillageLayouts.Stage stage = VillageLayouts.planning();
        return stage != null ? stage.maxDistance() : reach;
    }
}
