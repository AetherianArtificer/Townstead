package com.aetherianartificer.townstead.work.order;

import com.aetherianartificer.townstead.work.site.Worksite;
import com.aetherianartificer.townstead.work.site.Worksites;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Asking a worksite's order list what to do next, from any task at all.
 *
 * <p>The order seam used to live entirely inside {@code ProducerWorkTask}, which made orders a
 * feature of the three trades that happen to extend it. Most work in Townstead is not shaped like
 * that: a butcher's grinder runs its own state machine and picks from an enum of recipes in
 * declaration order, and that hardcoded order is exactly the thing a player should be able to
 * overrule. Nothing about consulting a list requires a particular base class — only an output id
 * per candidate.</p>
 */
public final class WorksiteOrders {

    private WorksiteOrders() {}

    /**
     * How this worker answers an order's questions about the world.
     *
     * <p>Nothing here is specific to any trade: stock is counted over the worksite's own extent,
     * the census is the village's residents — which is what "per villager" means — and eligibility
     * is a named villager and a minimum rank.</p>
     */
    public static OrderContext contextFor(ServerLevel level, Worksite site, VillagerEntityMCA villager) {
        return new OrderContext() {
            // Every worker assigned here counts, even off shift. The fallback preserves the
            // acting worker's transient stock if their assignment changed during this task.
            @Override
            public int stockOf(ResourceLocation item, Order.CountScope scope) {
                int total = WorksiteStock.count(level, site, item, scope);
                return WorksiteStock.isAssociated(level, site, villager)
                        ? total : total + WorksiteStock.carried(villager, item);
            }

            @Override
            public int stockOfTag(ResourceLocation tagId, Order.CountScope scope) {
                int total = WorksiteStock.countTag(level, site, tagId, scope);
                return WorksiteStock.isAssociated(level, site, villager)
                        ? total : total + WorksiteStock.carriedTag(villager, tagId);
            }

            @Override
            public int villagerCount() {
                return WorksiteStock.villagers(level, site);
            }

            @Override
            public boolean mayWork(Order order) {
                if (order.villager() != null && !order.villager().equals(villager.getUUID())) return false;
                if (order.profession() != null) {
                    ResourceLocation actual = BuiltInRegistries.VILLAGER_PROFESSION
                            .getKey(villager.getVillagerData().getProfession());
                    actual = com.aetherianartificer.townstead.profession.def.ProfessionDefs
                            .canonicalId(actual);
                    if (!order.profession().equals(actual)) return false;
                }
                return order.minRank() <= 0 || rankOf(villager) >= order.minRank();
            }
        };
    }

    /** This worker's rank in whatever career they are working, or 1 when it cannot be read. */
    public static int rankOf(VillagerEntityMCA villager) {
        com.aetherianartificer.townstead.villager.ProfessionXpStore store =
                com.aetherianartificer.townstead.profession.career.CareerTreeRows.storeOf(villager);
        if (store == null) return 1;
        ResourceLocation career = BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(villager.getVillagerData().getProfession());
        return career == null ? 1
                : Math.max(1, com.aetherianartificer.townstead.villager.ProfessionProgress
                        .getTier(store, career));
    }

    /**
     * Whether a worksite permits this job right now.
     *
     * <p>The gate for the work that has no output to count — slaughtering, dressing a carcass,
     * mopping blood, carrying a delivery. There is nothing to make "twenty of", so the list grants
     * or withholds permission instead:</p>
     *
     * <ul>
     *   <li><strong>Not on the list</strong> — allowed, unless the worksite is told to work the
     *       list only, in which case it is not.</li>
     *   <li><strong>On the list</strong> — allowed, unless that line is held.</li>
     * </ul>
     *
     * <p>A worksite that has never been given a list therefore behaves exactly as it did before
     * anyone wrote one, which is the only safe default for work that was already happening.</p>
     */
    /** The gate a job asks from wherever the worker is standing. */
    public static boolean allowsHere(ServerLevel level, VillagerEntityMCA villager,
                                     ResourceLocation activity) {
        return allows(level, villager.blockPosition(), activity);
    }

    public static boolean allows(ServerLevel level, @Nullable BlockPos worksiteAnchor,
                                 ResourceLocation activity) {
        if (worksiteAnchor == null || activity == null) return true;
        // Work that simply has to happen is never withheld, including at a worksite told to stand
        // down. Refusing it would leave finished goods where they were made and carcasses hanging.
        if (!com.aetherianartificer.townstead.work.WorkActivities.isDiscretionary(activity)) return true;
        // Asked from brain eligibility, so the common case has to cost nothing: resolving a
        // worksite walks villages and buildings, and almost no world has an activity line at all.
        com.aetherianartificer.townstead.work.site.WorksiteRegister register =
                level.getServer() == null ? null
                        : com.aetherianartificer.townstead.work.site.WorksiteRegister.get(level.getServer());
        if (register == null || !register.anyActivityOrders()) return true;

        Worksite site = Worksites.of(level, worksiteAnchor);
        if (site == null) return true;
        if (!com.aetherianartificer.townstead.work.WorkActivities
                .isDiscretionary(level, site, activity)) return true;
        OrderList orders = site.orders();
        for (Order order : orders.orders()) {
            if (!order.isActivity() || !order.output().equals(activity)) continue;
            return !order.paused();
        }
        // Absent from the list. Only a worksite told to work the list only refuses it.
        return !orders.listOnly();
    }

    /**
     * Whether a job should stand aside because something the list puts above it has work waiting.
     *
     * <p>This is what makes position mean something for work that has no count. Each job answers
     * for itself through {@link com.aetherianartificer.townstead.work.WorkActivities}, so the order
     * of deference comes from the player's list rather than from which task happened to name which
     * other one in its own start condition.</p>
     *
     * <p>Only lines <em>above</em> this one are asked, and the walk stops at the first that has
     * work, so a job at the top of the list costs nothing at all.</p>
     */
    public static boolean outranked(ServerLevel level, VillagerEntityMCA villager,
                                    @Nullable BlockPos worksiteAnchor, ResourceLocation activity) {
        if (worksiteAnchor == null || activity == null) return false;
        com.aetherianartificer.townstead.work.site.WorksiteRegister register =
                level.getServer() == null ? null
                        : com.aetherianartificer.townstead.work.site.WorksiteRegister.get(level.getServer());
        if (register == null || !register.anyActivityOrders()) return false;

        Worksite site = Worksites.of(level, worksiteAnchor);
        if (site == null) return false;
        for (Order order : site.orders().orders()) {
            if (!order.isActivity()) continue;
            if (order.output().equals(activity)) return false;   // reached our own line first
            if (order.paused()) continue;
            if (com.aetherianartificer.townstead.work.WorkActivities
                    .hasWork(level, villager, order.output())) {
                return true;
            }
        }
        // Not on the list at all: everything listed and unheld comes first.
        return anyListedHasWork(level, villager, site);
    }

    private static boolean anyListedHasWork(ServerLevel level, VillagerEntityMCA villager, Worksite site) {
        for (Order order : site.orders().orders()) {
            if (!order.isActivity() || order.paused()) continue;
            if (com.aetherianartificer.townstead.work.WorkActivities
                    .hasWork(level, villager, order.output())) {
                return true;
            }
        }
        return false;
    }

    /** The whole gate a job asks: am I permitted here, and is anything above me waiting? */
    public static boolean mayStart(ServerLevel level, VillagerEntityMCA villager,
                                   ResourceLocation activity) {
        BlockPos here = villager.blockPosition();
        return allows(level, here, activity) && !outranked(level, villager, here, activity);
    }

    /**
     * The candidates a task should try, in the order the worksite wants them tried.
     *
     * <p>Ordered lines that still want work come first, in list position; everything else keeps its
     * own order behind them. That suits a task which walks its candidates and takes the first it
     * can actually prepare — the list changes the <em>preference</em> without pretending to know
     * whether the ingredients are there.</p>
     *
     * <p>When the worksite is told to work the list only, the unordered tail is dropped: an empty
     * result means stand down, which is different from "no preference".</p>
     */
    public static <T> List<T> preference(ServerLevel level, VillagerEntityMCA villager,
                                         @Nullable BlockPos worksiteAnchor,
                                         List<T> candidates, Function<T, ResourceLocation> outputOf) {
        if (candidates.isEmpty() || worksiteAnchor == null) return candidates;
        Worksite site = Worksites.of(level, worksiteAnchor);
        if (site == null) return candidates;

        OrderList orders = site.orders();
        boolean listOnly = orders.listOnly();
        if (orders.isEmpty()) return listOnly ? List.of() : candidates;

        OrderContext context = contextFor(level, site, villager);
        List<T> wanted = new ArrayList<>();
        for (Order order : orders.orders()) {
            if (!order.wantsWork(context) || !context.mayWork(order)) continue;
            for (T candidate : candidates) {
                ResourceLocation output = outputOf.apply(candidate);
                if (order.matches(output) && !wanted.contains(candidate)) {
                    wanted.add(candidate);
                }
            }
        }
        if (listOnly) return List.copyOf(wanted);

        List<T> out = new ArrayList<>(wanted);
        for (T candidate : candidates) {
            ResourceLocation output = outputOf.apply(candidate);
            // Listed outputs stay under the sheet's authority even when their line is satisfied,
            // paused, or assigned elsewhere. The unordered tail is for genuinely unlisted work.
            if (!orders.governs(output) && !out.contains(candidate)) out.add(candidate);
        }
        return List.copyOf(out);
    }

    /**
     * Global station-selection rank for an output. This is asked before a worker chooses which
     * machine to walk to, preventing a lower line supported by a nearby pot from jumping ahead
     * of the first line whose recipe belongs on a skillet across the room.
     */
    public static int outputPriority(ServerLevel level, VillagerEntityMCA villager,
                                     @Nullable Worksite site, ResourceLocation output) {
        if (site == null || output == null) return 0;
        OrderList orders = site.orders();
        if (orders.isEmpty()) return orders.listOnly() ? Integer.MAX_VALUE : 0;
        OrderContext context = contextFor(level, site, villager);
        return orders.priority(output, context);
    }
}
