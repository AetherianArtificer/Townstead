package com.aetherianartificer.townstead.mixin.accessor;

import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets one entity's walk cycle be copied onto another drawn in its place. */
@Mixin(WalkAnimationState.class)
public interface WalkAnimationStateAccessor {
    //? if neoforge {
    @Accessor("speedOld") float townstead$speedOld();
    @Accessor("speedOld") void townstead$setSpeedOld(float value);
    @Accessor("speed") void townstead$setSpeed(float value);
    @Accessor("position") void townstead$setPosition(float value);
    //?} else {
    /*@Accessor(value = "f_267406_", remap = false) float townstead$speedOld();
    @Accessor(value = "f_267406_", remap = false) void townstead$setSpeedOld(float value);
    @Accessor(value = "f_267371_", remap = false) void townstead$setSpeed(float value);
    @Accessor(value = "f_267358_", remap = false) void townstead$setPosition(float value);
    *///?}
}
