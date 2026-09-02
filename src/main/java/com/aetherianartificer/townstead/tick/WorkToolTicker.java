package com.aetherianartificer.townstead.tick;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.schedule.Activity;
import com.aetherianartificer.townstead.hunger.FishermanSupplyManager;
import com.aetherianartificer.townstead.profession.def.WorkTaskTypes;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * While a villager is on a WORK shift, display their profession's tool of trade
 * in their main hand. Data-authored Job tools appear only while their Job has runnable work;
 * code-owned professions retain their own semantic display rules. The previous main-hand item
 * is stashed and restored when the shift ends, the profession changes, or no usable tool is in
 * inventory.
 */
public final class WorkToolTicker {
    private static final int CHECK_INTERVAL_TICKS = 20;
    private WorkToolTicker() {}

    private static final Map<UUID, ItemStack> PREVIOUS_MAIN_HAND = new ConcurrentHashMap<>();

    public static void tick(VillagerEntityMCA villager) {
        if (villager.level().isClientSide) return;
        if ((villager.level().getGameTime() + villager.getId()) % CHECK_INTERVAL_TICKS != 0) return;

        Brain<?> brain = villager.getBrain();
        long dayTime = villager.level().getDayTime() % 24000L;
        Activity current = brain.getSchedule().getActivityAt((int) dayTime);
        if (current != Activity.WORK) {
            restore(villager);
            return;
        }

        Set<net.minecraft.resources.ResourceLocation> actionableTasks = new HashSet<>();
        if (villager.level() instanceof ServerLevel level) {
            for (var task : com.aetherianartificer.townstead.work.WorkTaskDeclarations.all(villager)) {
                if (!com.aetherianartificer.townstead.work.job.WorkJobs.forTask(task.type()).isEmpty()
                        && com.aetherianartificer.townstead.work.WorkActivities
                        .hasWork(level, villager, task.type())) {
                    actionableTasks.add(task.type());
                }
            }
        }
        Predicate<ItemStack> matcher = stack ->
                matchesDeclaredTool(villager, stack, actionableTasks);

        ItemStack currentMain = villager.getMainHandItem();
        UUID id = villager.getUUID();

        // Fast path: already holding a matching tool and state is tracked.
        if (matcher.test(currentMain) && PREVIOUS_MAIN_HAND.containsKey(id)) {
            return;
        }

        int slot = findSlot(villager.getInventory(), matcher);
        if (slot < 0) {
            restore(villager);
            return;
        }

        ItemStack tool = villager.getInventory().getItem(slot);
        if (!PREVIOUS_MAIN_HAND.containsKey(id)) {
            ItemStack stash = matcher.test(currentMain) ? ItemStack.EMPTY : currentMain.copy();
            PREVIOUS_MAIN_HAND.put(id, stash);
        }
        villager.setItemInHand(InteractionHand.MAIN_HAND, tool.copy());
    }

    public static void forget(VillagerEntityMCA villager) {
        PREVIOUS_MAIN_HAND.remove(villager.getUUID());
    }

    /**
     * Job documents already name every item a generic executor may put in a worker's hand. Read
     * those selectors directly so a new profession, tool family, or integration mod does not need
     * a ticker rule. The few code-owned engines retain their own semantic matchers.
     */
    private static boolean matchesDeclaredTool(
            VillagerEntityMCA villager, ItemStack stack,
            Set<net.minecraft.resources.ResourceLocation> actionableTasks) {
        if (stack.isEmpty()) return false;
        for (var task : com.aetherianartificer.townstead.work.WorkTaskDeclarations.all(villager)) {
            if (task.type().equals(WorkTaskTypes.HARVEST)
                    && stack.getItem() instanceof HoeItem) return true;
            if (task.type().equals(WorkTaskTypes.FISH)
                    && FishermanSupplyManager.isFishingRod(stack)) return true;
            if (task.type().equals(WorkTaskTypes.SHEAR)
                    && com.aetherianartificer.townstead.shepherd.ShepherdShearToolCompatRegistry
                    .isCompatibleShears(stack)) return true;
            for (var job : com.aetherianartificer.townstead.work.job.WorkJobs.forTask(task.type())) {
                // A Job document names every item that could be used, not what can be used now.
                // Idle workers should not flash those possible tools when no source or target is
                // runnable.
                if (!actionableTasks.contains(task.type())) continue;
                if (job.source() != null && job.source().matches(stack)) return true;
                if (job.target() == null) continue;
                for (var interaction : job.target().interactions()) {
                    if (interaction.matches(stack)) return true;
                }
            }
        }
        return false;
    }

    private static void restore(VillagerEntityMCA villager) {
        ItemStack prev = PREVIOUS_MAIN_HAND.remove(villager.getUUID());
        if (prev == null) return;
        villager.setItemInHand(InteractionHand.MAIN_HAND, prev);
    }

    private static int findSlot(SimpleContainer inv, Predicate<ItemStack> matcher) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (matcher.test(inv.getItem(i))) return i;
        }
        return -1;
    }
}
