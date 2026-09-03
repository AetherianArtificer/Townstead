package com.aetherianartificer.townstead.food;

import com.aetherianartificer.townstead.block.ServingPlateBlockEntity;
import com.aetherianartificer.townstead.compat.pizzadelight.PizzaDelightCompat;
import com.aetherianartificer.townstead.hunger.FoodSafety;
import com.aetherianartificer.townstead.hunger.HungerData;
import com.aetherianartificer.townstead.hunger.VillagerConsumptionManager;
import com.aetherianartificer.townstead.needs.Amenities;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
//? if >=1.21 {
import net.minecraft.core.component.DataComponents;
//?}
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/** Movement and interaction glue for the serving-plate surface. Food rules remain on the food. */
public final class ServingPlateService {
    private static final ResourceLocation SOURCE_ID = Objects.requireNonNull(
            ResourceLocation.tryParse("townstead:serving_plate_food"));

    public record Prepared(ItemStack display, ItemStack serving, int portions) {}

    private ServingPlateService() {}

    public static void bootstrap() {
        Amenities.registerWorldSource(new PlateFoodSource());
    }

    public static boolean canServe(ItemStack stack) { return prepare(stack) != null; }

    /** Worker automation honors the menu authored by its assigned building. */
    public static boolean canAutoServe(ServerLevel level, VillagerEntityMCA villager, ItemStack stack) {
        return canServe(stack) && BuildingServingMenus.allowsAssigned(level, villager, stack);
    }

    public static @Nullable Prepared prepare(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null && "pizzadelight:pizza".equals(id.toString())) {
            ItemStack slice = PizzaDelightCompat.sliceFromPizzaItem(stack);
            if (!slice.isEmpty()) return new Prepared(stack.copyWithCount(1), slice, 4);
        }
        int servings = com.aetherianartificer.townstead.needs.Consumables.servings(stack);
        if (servings > 0) {
            return new Prepared(stack.copyWithCount(1), stack.copyWithCount(1),
                    servings);
        }
        //? if >=1.21 {
        FoodProperties food = stack.get(DataComponents.FOOD);
        //?} else {
        /*FoodProperties food = stack.getFoodProperties(null);
        *///?}
        if (food != null) return new Prepared(stack.copyWithCount(1), stack.copyWithCount(1), 1);
        // A serving plate is food storage, not a general display pedestal. Accepting arbitrary
        // items here let tools and raw ingredients occupy a restaurant's only delivery surface
        // forever: neither players nor hungry villagers could consume them, and the worker then
        // had nowhere to put the next finished dish.
        return null;
    }

    public static @Nullable BlockPos findEmpty(ServerLevel level, VillagerEntityMCA villager,
                                                Set<Long> bounds, Predicate<ItemStack> matcher,
                                                Set<Long> rejected) {
        if (bounds == null || bounds.isEmpty()) return null;
        return bounds.stream().filter(packed -> rejected == null || !rejected.contains(packed))
                .map(BlockPos::of)
                .filter(level::isLoaded)
                .filter(pos -> ServingSurfaces.contains(level.getBlockState(pos)))
                .filter(pos -> isEmptySurface(level, pos))
                .filter(pos -> hasServable(level, villager, matcher))
                .min(Comparator.comparingDouble(pos -> villager.distanceToSqr(
                        pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5)))
                .orElse(null);
    }

    private static boolean hasServable(ServerLevel level, VillagerEntityMCA villager,
                                       Predicate<ItemStack> matcher) {
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack stack = villager.getInventory().getItem(i);
            if (!stack.isEmpty() && matcher.test(stack) && canAutoServe(level, villager, stack)) return true;
        }
        return false;
    }

    /** Moves one matching finished dish from the worker onto the plate. */
    public static int depositMatchingAt(ServerLevel level, VillagerEntityMCA villager,
                                        BlockPos pos, Predicate<ItemStack> matcher) {
        if (!ServingSurfaces.contains(level.getBlockState(pos)) || !isEmptySurface(level, pos)) return 0;
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack stack = villager.getInventory().getItem(i);
            if (stack.isEmpty() || !matcher.test(stack) || !canAutoServe(level, villager, stack)) continue;
            if (!putOnSurface(level, pos, stack.copyWithCount(1))) return 0;
            stack.shrink(1);
            villager.getInventory().setChanged();
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.7F, 1.05F);
            return 1;
        }
        return 0;
    }

    /** Whether any serving surface inside these worksite cells is waiting for a dish. */
    public static boolean hasEmptySurface(ServerLevel level, Set<Long> bounds) {
        if (level == null || bounds == null || bounds.isEmpty()) return false;
        for (long packed : bounds) {
            BlockPos pos = BlockPos.of(packed);
            if (!level.isLoaded(pos)) continue;
            if (ServingSurfaces.contains(level.getBlockState(pos)) && isEmptySurface(level, pos)) return true;
        }
        return false;
    }

    public static boolean isServingSurface(ServerLevel level, BlockPos pos) {
        return level != null && pos != null && level.isLoaded(pos)
                && ServingSurfaces.contains(level.getBlockState(pos));
    }

    private static boolean isEmptySurface(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ServingPlateBlockEntity plate) return plate.isEmpty();
        SurfaceSlot slot = firstSurfaceSlot(level, pos, true);
        return slot != null;
    }

    private static boolean putOnSurface(ServerLevel level, BlockPos pos, ItemStack one) {
        if (level.getBlockEntity(pos) instanceof ServingPlateBlockEntity plate) return plate.place(one);
        if (!blockAccepts(level, pos, one)) return false;
        SurfaceSlot slot = firstSurfaceSlot(level, pos, true);
        if (slot == null) return false;
        return slot.set(one);
    }

    private static boolean blockAccepts(ServerLevel level, BlockPos pos, ItemStack stack) {
        Object block = level.getBlockState(pos).getBlock();
        try {
            Object accepted = block.getClass().getMethod("canInsertStack", ItemStack.class).invoke(block, stack);
            return !(accepted instanceof Boolean value) || value;
        } catch (ReflectiveOperationException ignored) {
            return true;
        }
    }

    /** Common Container API first; a small reflective bridge covers storage-style plate mods. */
    private static @Nullable SurfaceSlot firstSurfaceSlot(ServerLevel level, BlockPos pos, boolean empty) {
        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) return null;
        if (blockEntity instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (container.getItem(i).isEmpty() == empty) {
                    int slot = i;
                    return new SurfaceSlot(container.getItem(slot), stack -> {
                        container.setItem(slot, stack);
                        container.setChanged();
                        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
                        return true;
                    });
                }
            }
            return null;
        }
        try {
            Object inventory = blockEntity.getClass().getMethod("getInventory").invoke(blockEntity);
            if (!(inventory instanceof List<?> list)) return null;
            for (int i = 0; i < list.size(); i++) {
                if (!(list.get(i) instanceof ItemStack current) || current.isEmpty() != empty) continue;
                int slot = i;
                return new SurfaceSlot(current, stack -> {
                    try {
                        blockEntity.getClass().getMethod("setStack", int.class, ItemStack.class)
                                .invoke(blockEntity, slot, stack);
                        blockEntity.setChanged();
                        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
                        return true;
                    } catch (ReflectiveOperationException ex) {
                        return false;
                    }
                });
            }
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
        return null;
    }

    private record SurfaceSlot(ItemStack stack, java.util.function.Function<ItemStack, Boolean> setter) {
        boolean set(ItemStack value) { return setter.apply(value); }
    }

    public static boolean feedPlayer(ServerLevel level, Player player, ServingPlateBlockEntity plate) {
        if (plate.isEmpty()) return false;
        ItemStack serving = plate.servingStack().copyWithCount(1);
        ConsumptionTransaction.Result result = ConsumptionTransaction.consumePlayer(
                level, player, serving, plate::consumeOne);
        if (!result.committed()) return false;
        if (!result.succeeded()) {
            com.aetherianartificer.townstead.Townstead.LOGGER.warn(
                    "Committed serving at {} failed during native consumption: {}",
                    plate.getBlockPos(), result.detail());
            return true;
        }
        ItemStack remainder = result.remainder();
        if (!remainder.isEmpty()) routePlayerRemainder(level, player, plate.getBlockPos(),
                remainder, result.destination());
        level.playSound(null, plate.getBlockPos(), SoundEvents.GENERIC_EAT,
                SoundSource.PLAYERS, 0.8F, 0.9F);
        return true;
    }

    private static void routePlayerRemainder(ServerLevel level, Player player, BlockPos source,
                                             ItemStack remainder,
                                             ConsumptionPolicy.RemainderDestination destination) {
        if (destination == ConsumptionPolicy.RemainderDestination.HOLDER) {
            if (!player.getInventory().add(remainder)) player.drop(remainder, false);
            return;
        }
        if (destination == ConsumptionPolicy.RemainderDestination.SOURCE) {
            Containers.dropItemStack(level, source.getX() + 0.5, source.getY() + 0.2,
                    source.getZ() + 0.5, remainder);
            return;
        }
        player.drop(remainder, false);
    }

    private static final class PlateFoodSource implements Amenities.WorldSource {
        @Override public ResourceLocation id() { return SOURCE_ID; }
        @Override public Set<ResourceLocation> blocks() { return ServingSurfaces.blockIds(); }
        @Override public boolean feeds(ServerLevel level, BlockPos pos) { return true; }
        @Override public boolean hydrates(ServerLevel level, BlockPos pos) { return false; }

        @Override
        public boolean available(ServerLevel level, BlockPos pos) {
            ItemStack serving = servingAt(level, pos);
            if (!VillagerConsumptionManager.permitsManagedVillagerConsumption(serving)) return false;
            return !serving.isEmpty() && FoodSafety.isSafeNutritiousFood(serving);
        }

        @Override
        public boolean use(ServerLevel level, VillagerEntityMCA villager, BlockPos pos) {
            ItemStack serving = servingAt(level, pos);
            if (!VillagerConsumptionManager.permitsManagedVillagerConsumption(serving)) return false;
            if (!FoodSafety.isSafeNutritiousFood(serving, villager)) return false;
            var needs = TownsteadVillagers.get(villager).needs();
            if (needs.hunger() >= HungerData.MAX_HUNGER || !consumeAt(level, pos)) return false;
            VillagerConsumptionManager.applyConsumption(villager, villager, serving, needs, pos);
            villager.swing(villager.getDominantHand());
            level.playSound(null, pos, SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.8F, 0.9F);
            return true;
        }
    }

    private static ItemStack servingAt(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ServingPlateBlockEntity plate && !plate.isEmpty()) {
            return plate.servingStack().copyWithCount(1);
        }
        SurfaceSlot slot = firstSurfaceSlot(level, pos, false);
        return slot == null ? ItemStack.EMPTY : slot.stack().copyWithCount(1);
    }

    private static boolean consumeAt(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ServingPlateBlockEntity plate) return plate.consumeOne();
        SurfaceSlot slot = firstSurfaceSlot(level, pos, false);
        if (slot == null) return false;
        ItemStack remaining = slot.stack().copy();
        remaining.shrink(1);
        return slot.set(remaining);
    }
}
