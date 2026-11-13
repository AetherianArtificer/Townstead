package com.aetherianartificer.townstead.mixin.client;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The client level's part entity table. Forge and NeoForge both add it, under this name. */
@Mixin(ClientLevel.class)
public interface ClientLevelPartsAccessor {
    @Accessor(value = "partEntities", remap = false)
    Int2ObjectMap<Object> townstead$parts();
}
