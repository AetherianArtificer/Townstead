package com.aetherianartificer.townstead.compat.butchery;

import com.aetherianartificer.townstead.compat.mca.McaRoomBinding;
import com.aetherianartificer.townstead.profession.def.WorkTaskTypes;
import com.aetherianartificer.townstead.work.WorkActivities;
import com.aetherianartificer.townstead.work.site.Worksite;

import net.conczin.mca.server.world.data.Building;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;

/**
 * Declares the butcher's jobs so a worksite's order list can name and order them.
 *
 * <p>Each probe is the same question the task's own start condition asks, and nothing more — is
 * there an animal, a carcass, a golem, a head, a hook, a puddle, a parcel. They are asked while
 * another job decides whether to defer, so they stay read-only and no more expensive than the
 * check they mirror.</p>
 *
 * <p>Each is also declared as belonging to a butchery, so a kitchen is not offered the chance to
 * order some slaughtering.</p>
 */
public final class ButcheryActivities {

    private ButcheryActivities() {}

    /**
     * Four of these are choices and three are simply the job.
     *
     * <p>Nobody wants a butcher who will not carry the sausages to the shelf, dress the carcass
     * hanging in front of them, or hang what they cured — those are how the work happens, so they
     * are not offered and cannot be withheld. Killing somebody's animals, taking a hacksaw to a
     * golem, hammering skulls and mopping the floor are all things a player might reasonably want
     * stopped.</p>
     */
    public static void bootstrap() {
        register(WorkTaskTypes.SLAUGHTER, "Slaughter", Items.IRON_SWORD, true,
                SlaughterWorkTask::hasWorkWaiting);
        register(WorkTaskTypes.DISMANTLE, "Dismantle golems", Items.SHEARS, true,
                GolemProcessingTask::hasWorkWaiting);
        register(WorkTaskTypes.HAMMER, "Break heads", Items.IRON_PICKAXE, true,
                HeadHammeringTask::hasWorkWaiting);
        register(WorkTaskTypes.CLEAN, "Clean blood", Items.SPONGE, true,
                BloodCleanupTask::hasWorkWaiting);

        register(WorkTaskTypes.BUTCHER, "Dress carcasses", Items.IRON_AXE, false,
                CarcassWorkTask::hasActionableWork);
        register(WorkTaskTypes.CURE, "Hang sausages", Items.STRING, false,
                SausageHookTask::hasWorkWaiting);
        register(WorkTaskTypes.DELIVER, "Deliver", Items.CHEST, false,
                ButcherDeliveryTask::hasWorkWaiting);
    }

    private static void register(net.minecraft.resources.ResourceLocation type, String label,
                                 net.minecraft.world.item.Item icon, boolean discretionary,
                                 WorkActivities.Probe probe) {
        WorkActivities.register(type, label,
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(icon),
                discretionary, probe, ButcheryActivities::isButchery);
    }

    /**
     * Whether butcher's work is done at this worksite.
     *
     * <p>Asked the same way the catalogue's scoping asks it — does a trade declaring this work
     * claim the place — rather than by a second, stricter test of its own. Those two disagreed: the
     * butcher's job site is a <em>smoker</em>, so a smoker in a plain building made a butcher work
     * there while a building-type check said it was not a butchery and hid every job.</p>
     */
    private static boolean isButchery(ServerLevel level, Worksite site) {
        if (!ButcheryCompat.isLoaded()) return false;
        return com.aetherianartificer.townstead.work.site.WorksiteWork
                .typesAt(level, site,
                        com.aetherianartificer.townstead.work.site.Worksites.extentOf(level, site))
                .contains(WorkTaskTypes.BUTCHER);
    }
}
