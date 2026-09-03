package com.aetherianartificer.townstead.compat.curios;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.ISlotType;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The typed half of the Curios integration. Only {@link CuriosCompat} calls in here, and only after
 * confirming Curios is loaded, so this is the one class allowed to import Curios types.
 */
final class CuriosBridge {

    private CuriosBridge() {}

    static Optional<ICuriosItemHandler> inventory(LivingEntity entity) {
        //? if neoforge {
        return CuriosApi.getCuriosInventory(entity);
        //?} else {
        /*return CuriosApi.getCuriosInventory(entity).resolve();
        *///?}
    }

    private static Optional<ICurio> curio(ItemStack stack) {
        //? if neoforge {
        return CuriosApi.getCurio(stack);
        //?} else {
        /*return CuriosApi.getCurio(stack).resolve();
        *///?}
    }

    static void forEachWorn(LivingEntity entity, Consumer<ItemStack> out) {
        inventory(entity).ifPresent(handler -> {
            for (ICurioStacksHandler stacks : handler.getCurios().values()) {
                IDynamicStackHandler dynamic = stacks.getStacks();
                for (int i = 0; i < dynamic.getSlots(); i++) {
                    ItemStack stack = dynamic.getStackInSlot(i);
                    if (!stack.isEmpty()) out.accept(stack);
                }
            }
        });
    }

    static void forEachWornVisible(LivingEntity entity, BiConsumer<String, ItemStack> out) {
        inventory(entity).ifPresent(handler -> handler.getCurios().forEach((id, stacks) -> {
            IDynamicStackHandler main = stacks.getStacks();
            IDynamicStackHandler cosmetic = stacks.getCosmeticStacks();
            for (int i = 0; i < main.getSlots(); i++) {
                ItemStack stack = cosmetic.getStackInSlot(i);
                if (stack.isEmpty() && i < stacks.getRenders().size() && stacks.getRenders().get(i)) {
                    stack = main.getStackInSlot(i);
                }
                if (!stack.isEmpty()) out.accept(id, stack);
            }
        }));
    }

    static void removeWhere(LivingEntity entity, Predicate<ItemStack> test, Consumer<ItemStack> onRemoved) {
        inventory(entity).ifPresent(handler -> {
            for (ICurioStacksHandler stacks : handler.getCurios().values()) {
                IDynamicStackHandler dynamic = stacks.getStacks();
                for (int i = 0; i < dynamic.getSlots(); i++) {
                    ItemStack stack = dynamic.getStackInSlot(i);
                    if (stack.isEmpty() || !test.test(stack)) continue;
                    ItemStack removed = stack.copy();
                    dynamic.setStackInSlot(i, ItemStack.EMPTY);
                    onRemoved.accept(removed);
                }
            }
        });
    }

    static List<CurioSlotSpec> slotSpecs(LivingEntity entity) {
        Optional<ICuriosItemHandler> inventory = inventory(entity);
        if (inventory.isEmpty()) return List.of();
        ICuriosItemHandler handler = inventory.get();
        // The entity slot map carries Curios' display order; the handler map may not.
        Map<String, ISlotType> types = CuriosApi.getEntitySlots(entity);
        List<CurioSlotSpec> specs = new ArrayList<>();
        for (Map.Entry<String, ISlotType> type : types.entrySet()) {
            ICurioStacksHandler stacks = handler.getStacksHandler(type.getKey()).orElse(null);
            if (stacks == null) continue;
            IDynamicStackHandler dynamic = stacks.getStacks();
            for (int i = 0; i < dynamic.getSlots(); i++) {
                specs.add(new CurioSlotSpec(type.getKey(), i, dynamic, type.getValue().getIcon(),
                        type.getValue().canToggleRendering()));
            }
        }
        return specs;
    }

    static boolean canEquip(LivingEntity entity, String slotId, int index, ItemStack stack) {
        SlotContext context = new SlotContext(slotId, entity, index, false, true);
        return CuriosApi.isStackValid(context, stack)
                && curio(stack).map(c -> c.canEquip(context)).orElse(true);
    }

    static boolean canUnequip(LivingEntity entity, String slotId, int index, ItemStack stack) {
        SlotContext context = new SlotContext(slotId, entity, index, false, true);
        return curio(stack).map(c -> c.canUnequip(context)).orElse(true);
    }

    static void onEquipFromUse(LivingEntity entity, String slotId, int index, ItemStack stack) {
        SlotContext context = new SlotContext(slotId, entity, index, false, true);
        curio(stack).ifPresent(c -> c.onEquipFromUse(context));
    }

    static boolean isRendered(LivingEntity entity, String slotId, int index) {
        return inventory(entity)
                .flatMap(handler -> handler.getStacksHandler(slotId))
                .map(stacks -> index < stacks.getRenders().size() && stacks.getRenders().get(index))
                .orElse(false);
    }

    static void setRendered(LivingEntity entity, String slotId, int index, boolean render) {
        inventory(entity)
                .flatMap(handler -> handler.getStacksHandler(slotId))
                .ifPresent(stacks -> {
                    if (index < stacks.getRenders().size()) stacks.getRenders().set(index, render);
                });
    }

    static boolean equipFirstFree(LivingEntity entity, ItemStack stack) {
        for (CurioSlotSpec spec : slotSpecs(entity)) {
            if (!spec.handler().getStackInSlot(spec.index()).isEmpty()) continue;
            if (!canEquip(entity, spec.id(), spec.index(), stack)) continue;
            ItemStack worn = stack.copy();
            worn.setCount(1);
            spec.handler().setStackInSlot(spec.index(), worn);
            return true;
        }
        return false;
    }
}
