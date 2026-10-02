package com.aetherianartificer.townstead.mixin.accessor;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 1.20.1 SRG: {@code f_21345_} goalSelector, {@code f_21346_} targetSelector. */
@Mixin(Mob.class)
public interface MobGoalsAccessor {
    //? if neoforge {
    @Accessor("goalSelector") GoalSelector townstead$goalSelector();

    @Accessor("targetSelector") GoalSelector townstead$targetSelector();
    //?} else {
    /*@Accessor(value = "f_21345_", remap = false) GoalSelector townstead$goalSelector();

    @Accessor(value = "f_21346_", remap = false) GoalSelector townstead$targetSelector();
    *///?}
}
