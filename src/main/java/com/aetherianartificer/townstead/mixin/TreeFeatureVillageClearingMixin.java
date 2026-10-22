package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.culture.VillageClearings;
import net.minecraft.world.level.levelgen.feature.AbstractHugeMushroomFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Trees and huge mushrooms do not grow in culture villages: their buildings, streets and the space around them. */
@Mixin({TreeFeature.class, AbstractHugeMushroomFeature.class})
public abstract class TreeFeatureVillageClearingMixin {
    //? if neoforge {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_142674_", remap = false, at = @At("HEAD"), cancellable = true)
    *///?}
    private void townstead$keepOutOfVillages(FeaturePlaceContext<?> context, CallbackInfoReturnable<Boolean> cir) {
        if (VillageClearings.blocks(context.origin())) cir.setReturnValue(false);
    }
}
