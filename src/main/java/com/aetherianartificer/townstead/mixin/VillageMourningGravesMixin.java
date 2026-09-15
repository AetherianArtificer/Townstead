package com.aetherianartificer.townstead.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

/**
 * MCA's village-scoped mourning scheduler shuffles the graves returned by
 * {@code Stream#toList()}, which is unmodifiable. Copy the result before MCA
 * shuffles it so villages with more than one mournable grave do not fail their
 * village tick. The optional injector is inert on MCA builds predating the
 * village-scoped scheduler.
 */
@Pseudo
@Mixin(targets = "net.conczin.mca.server.world.data.Village", remap = false)
public abstract class VillageMourningGravesMixin {
    @ModifyExpressionValue(
            method = "releaseMourningBurst",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/conczin/mca/entity/ai/Mourning;getMournableGraves(Lnet/conczin/mca/server/world/data/Village;Lnet/minecraft/world/level/Level;)Ljava/util/List;"
            ),
            remap = false,
            require = 0
    )
    private List<BlockPos> townstead$mutableMourningGraves(List<BlockPos> graves) {
        return mutableCopy(graves);
    }

    @Unique
    private static <T> List<T> mutableCopy(List<T> values) {
        return new ArrayList<>(values);
    }
}
