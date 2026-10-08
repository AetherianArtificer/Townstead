package com.aetherianartificer.townstead.food;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import com.aetherianartificer.townstead.needs.Consumables;

import java.util.function.BooleanSupplier;
import java.util.function.Function;

/** Executes one isolated player serving through the item's native finish contract. */
public final class ConsumptionTransaction {
    public enum Status { SUCCESS, DENIED, UNSUPPORTED, NO_ACCOUNTING, COMMITTED_ERROR, ERROR }

    public record Result(Status status, int consumed, ItemStack remainder,
                         ConsumptionPolicy.RemainderDestination destination, String detail) {
        public Result {
            remainder = remainder == null ? ItemStack.EMPTY : remainder.copy();
            destination = destination == null
                    ? ConsumptionPolicy.RemainderDestination.HOLDER : destination;
            detail = detail == null ? "" : detail;
            if (status != Status.SUCCESS && status != Status.COMMITTED_ERROR && consumed != 0) {
                throw new IllegalArgumentException("uncommitted transaction cannot consume an item");
            }
        }

        public boolean succeeded() { return status == Status.SUCCESS; }
        public boolean committed() { return consumed > 0; }
    }

    private ConsumptionTransaction() {}

    public static Result consumePlayer(ServerLevel level, Player player, ItemStack offered,
                                       BooleanSupplier commitSource) {
        if (level == null || player == null || offered == null || offered.isEmpty()) {
            return rejected(Status.DENIED, "missing level, consumer, or serving");
        }
        if (commitSource == null) return rejected(Status.DENIED, "missing serving source");
        Consumables.Definition definition = Consumables.resolve(
                offered, ConsumptionPolicy.Consumer.PLAYER);
        ConsumptionPolicy policy = definition == null ? null : definition.transaction();
        if (policy != null && !policy.permits(ConsumptionPolicy.Consumer.PLAYER)) {
            return rejected(Status.DENIED, "transaction policy does not permit player consumption");
        }
        if (policy != null && policy.mode() == ConsumptionPolicy.Mode.REPLACE_WITH_PHENO) {
            return rejected(Status.UNSUPPORTED, "player Pheno replacement is not implemented");
        }
        if (policy != null && policy.accounting() == ConsumptionPolicy.Accounting.NATIVE_RESULT) {
            return rejected(Status.UNSUPPORTED,
                    "native-result accounting cannot atomically reserve an external serving");
        }
        if (policy != null && !policy.effectAdmission().unrestricted()) {
            return rejected(Status.UNSUPPORTED,
                    "observe-native player consumption cannot selectively suppress native effects");
        }
        if (policy != null && policy.remainder().destination()
                == ConsumptionPolicy.RemainderDestination.STORAGE) {
            return rejected(Status.UNSUPPORTED,
                    "player consumption has no authoritative storage destination");
        }
        if (policy != null && policy.remainder().mode() == ConsumptionPolicy.RemainderMode.ITEM
                && (policy.remainder().item() == null
                || !BuiltInRegistries.ITEM.containsKey(policy.remainder().item()))) {
            return rejected(Status.ERROR, "declared remainder item is not registered");
        }

        ItemStack isolated = offered.copyWithCount(1);
        //? if >=1.21 {
        FoodProperties food = isolated.get(net.minecraft.core.component.DataComponents.FOOD);
        //?} else {
        /*FoodProperties food = isolated.getFoodProperties(player);
        *///?}
        if (food != null && !player.canEat(food.canAlwaysEat())) {
            return rejected(Status.DENIED, "consumer cannot currently eat this serving");
        }

        return execute(offered, policy, commitSource,
                serving -> serving.finishUsingItem(level, player));
    }

    /**
     * Commits the source before invoking a potentially side-effecting native finish operation.
     * Once committed, an exception is reported as committed rather than restoring a serving whose
     * effects may already have run. This deliberately prefers a visible loss over duplication.
     */
    static Result execute(ItemStack offered, ConsumptionPolicy policy,
                          BooleanSupplier commitSource,
                          Function<ItemStack, ItemStack> finish) {
        ItemStack isolated = offered.copyWithCount(1);
        ItemStack before = isolated.copy();
        AtomicServingCommit.Outcome<ItemStack> outcome = AtomicServingCommit.execute(
                commitSource, () -> finish.apply(isolated));
        if (!outcome.committed()) {
            return rejected(Status.NO_ACCOUNTING, outcome.detail());
        }
        if (outcome.status() == AtomicServingCommit.Status.COMMITTED_ERROR) {
            return new Result(Status.COMMITTED_ERROR, 1, ItemStack.EMPTY,
                    destination(policy), outcome.detail());
        }
        ItemStack nativeResult = outcome.value();
        boolean nativeAccounted = accounted(before, nativeResult);
        ItemStack remainder = resolveRemainder(policy, before, nativeResult, nativeAccounted);
        if (remainder == null) {
            return new Result(Status.COMMITTED_ERROR, 1, ItemStack.EMPTY,
                    destination(policy), "declared remainder item is not registered after commit");
        }
        return new Result(Status.SUCCESS, 1, remainder, destination(policy), "");
    }

    private static boolean accounted(ItemStack before, ItemStack after) {
        if (after == null || after.isEmpty()) return true;
        // A different item is the native remainder. The isolated input was consumed.
        //? if >=1.21 {
        if (!ItemStack.isSameItemSameComponents(before, after)) return true;
        //?} else {
        /*if (!ItemStack.isSameItemSameTags(before, after)) return true;
        *///?}
        return after.getCount() < before.getCount();
    }

    /** Null means an invalid authored explicit remainder. */
    private static ItemStack resolveRemainder(ConsumptionPolicy policy,
                                               ItemStack original, ItemStack nativeResult,
                                               boolean nativeAccounted) {
        if (policy == null || policy.remainder().mode() == ConsumptionPolicy.RemainderMode.NATIVE) {
            if (!nativeAccounted) {
                return com.aetherianartificer.townstead.hunger.VillagerConsumptionManager
                        .getConsumptionRemainder(original);
            }
            return nativeResult == null ? ItemStack.EMPTY : nativeResult.copy();
        }
        if (policy.remainder().mode() == ConsumptionPolicy.RemainderMode.NONE) return ItemStack.EMPTY;
        if (!BuiltInRegistries.ITEM.containsKey(policy.remainder().item())) return null;
        return BuiltInRegistries.ITEM.get(policy.remainder().item()).getDefaultInstance();
    }

    private static Result rejected(Status status, String detail) {
        return new Result(status, 0, ItemStack.EMPTY,
                ConsumptionPolicy.RemainderDestination.HOLDER, detail);
    }

    private static ConsumptionPolicy.RemainderDestination destination(ConsumptionPolicy policy) {
        return policy == null ? ConsumptionPolicy.RemainderDestination.HOLDER
                : policy.remainder().destination();
    }
}
