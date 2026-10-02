package com.aetherianartificer.townstead.mixin.accessor;

import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.UUID;

/** 1.20.1 SRG: {@code f_265881_} target. */
@Mixin(ItemEntity.class)
public interface ItemEntityOwnerAccessor {
    //? if neoforge {
    @Accessor("target") UUID townstead$pickupOwner();
    //?} else {
    /*@Accessor(value = "f_265881_", remap = false) UUID townstead$pickupOwner();
    *///?}
}
