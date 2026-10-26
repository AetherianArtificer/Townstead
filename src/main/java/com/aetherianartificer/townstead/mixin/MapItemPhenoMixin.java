package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.root.hook.PhenoHooks;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.MapItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Intercept for the {@code map_fill_radius} modifier target. {@code update} fills a circle of
 * {@code 128 / 2^scale} map pixels around the holder, 128 blocks at every scale; the first 128
 * constant is that radius on both versions (checked in the 1.20.1 and 1.21.1 bytecode). 1.20.1
 * SRG: {@code m_42893_} update.
 */
@Mixin(MapItem.class)
public abstract class MapItemPhenoMixin {

    //? if neoforge {
    @ModifyConstant(method = "update", constant = @Constant(intValue = 128, ordinal = 0))
    //?} else {
    /*@ModifyConstant(method = "m_42893_", constant = @Constant(intValue = 128, ordinal = 0), remap = false)
    *///?}
    private int townstead$mapFillRadius(int radius, @Local(argsOnly = true) Entity holder) {
        return PhenoHooks.mapFillRadius(holder, radius);
    }
}
