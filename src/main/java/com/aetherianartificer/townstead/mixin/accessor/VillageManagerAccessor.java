package com.aetherianartificer.townstead.mixin.accessor;

import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(VillageManager.class)
public interface VillageManagerAccessor {
    @Accessor(value = "world", remap = false)
    ServerLevel townstead$world();
}
