package com.aetherianartificer.townstead.compat.curios;

import net.minecraft.resources.ResourceLocation;
//? if neoforge {
import net.neoforged.neoforge.items.IItemHandlerModifiable;
//?} else {
/*import net.minecraftforge.items.IItemHandlerModifiable;
*///?}

/**
 * One Curios slot on an entity, enough to back a menu {@code Slot}: the slot type id, the index within
 * that type, the handler holding the type's stacks, the empty-slot icon sprite (block atlas), and
 * whether the slot type lets its wearer hide the curio.
 */
public record CurioSlotSpec(String id, int index, IItemHandlerModifiable handler, ResourceLocation icon,
                            boolean canToggleRender) {}
