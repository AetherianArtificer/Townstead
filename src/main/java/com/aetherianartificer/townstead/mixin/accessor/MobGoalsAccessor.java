package com.aetherianartificer.townstead.mixin.accessor;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Mob.class)
public interface MobGoalsAccessor {
    @Accessor("goalSelector") GoalSelector townstead$goalSelector();

    @Accessor("targetSelector") GoalSelector townstead$targetSelector();
}
