package com.aetherianartificer.townstead.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The level's part entity table, so parts can come and go after their bearer has joined. */
@Mixin(ServerLevel.class)
public interface ServerLevelPartsAccessor {
    //? if neoforge {
    @Accessor("dragonParts")
    //?} else {
    /*@Accessor(value = "f_143247_", remap = false)
    *///?}
    Int2ObjectMap<Object> townstead$parts();
}
