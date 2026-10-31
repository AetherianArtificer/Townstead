package com.aetherianartificer.townstead.tick;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.schedule.Activity;
import com.aetherianartificer.townstead.hunger.FishermanSupplyManager;
import com.aetherianartificer.townstead.profession.def.WorkTaskTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;

import com.aetherianartificer.townstead.villager.HandProps;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * While a villager is on a WORK shift, display their profession's tool of trade
 * in their main hand. Data-authored Job tools appear only while their Job has runnable work;
 * code-owned professions retain their own semantic display rules. The tool shown is a
 * {@link HandProps prop}: the real one stays in the inventory, and the prop is cleared when the
 * shift ends, the profession changes, or no usable tool is in inventory.
 */
public final class WorkToolTicker {
    private static final int CHECK_INTERVAL_TICKS = 20;
    private static final String GRIEVE_ID = "mca:grieve";
    private WorkToolTicker() {}

    public static void tick(VillagerEntityMCA villager) {
        if (villager.level().isClientSide) return;
        if (com.aetherianartificer.townstead.hunger.VillagerConsumptionManager.isHoldingServing(villager)) return;
        if ((villager.level().getGameTime() + villager.getId()) % CHECK_INTERVAL_TICKS != 0) return;

        Brain<?> brain = villager.getBrain();
        // A mourner holds MCA's flower and gets its hand back when mourning ends; leave the hand alone.
        if (isGrieving(brain)) return;
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

        // A matching tool MCA handed over stays as it is.
        ItemStack held = villager.getMainHandItem();
        if (matcher.test(held) && !HandProps.isProp(held)) return;

        int slot = findSlot(villager.getInventory(), matcher);
        if (slot < 0) {
            restore(villager);
            return;
        }
        // A matching prop stays only while a real tool is still in the inventory.
        if (matcher.test(held)) return;
        HandProps.show(villager, InteractionHand.MAIN_HAND, villager.getInventory().getItem(slot));
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
                    && com.aetherianartificer.townstead.farming.Farmland.isHoe(stack)) return true;
            if (task.type().equals(WorkTaskTypes.FISH)
                    && FishermanSupplyManager.isFishingRod(stack)) return true;
            for (var job : com.aetherianartificer.townstead.work.job.WorkJobs.forTask(task.type())) {
                // A Job document names every item that could be used, not what can be used now.
                // Idle workers should not flash those possible tools when no source or target is
                // runnable.
                if (!actionableTasks.contains(task.type())) continue;
                if (job.source() != null && job.source().matches(stack)) return true;
                if (job.entityTarget() != null) {
                    for (var interaction : job.entityTarget().interactions()) {
                        if (interaction.matches(stack)) return true;
                    }
                }
                if (job.target() == null) continue;
                for (var interaction : job.target().interactions()) {
                    if (interaction.matches(stack)) return true;
                }
            }
        }
        return false;
    }

    private static void restore(VillagerEntityMCA villager) {
        HandProps.clear(villager, InteractionHand.MAIN_HAND);
    }

    // Matched by id: the activity constant lives in a different MCA class on each version.
    private static boolean isGrieving(Brain<?> brain) {
        return brain.getActiveNonCoreActivity()
                .map(activity -> GRIEVE_ID.equals(String.valueOf(
                        net.minecraft.core.registries.BuiltInRegistries.ACTIVITY.getKey(activity))))
                .orElse(false);
    }

    private static int findSlot(SimpleContainer inv, Predicate<ItemStack> matcher) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (matcher.test(inv.getItem(i))) return i;
        }
        return -1;
    }
}
