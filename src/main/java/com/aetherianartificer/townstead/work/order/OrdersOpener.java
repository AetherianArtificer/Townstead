package com.aetherianartificer.townstead.work.order;

import com.aetherianartificer.townstead.work.order.net.OrderEditC2SPayload;
import com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload;
import com.aetherianartificer.townstead.work.order.net.OrdersSnapshotS2CPayload.Option;
import com.aetherianartificer.townstead.work.site.Worksite;
import com.aetherianartificer.townstead.work.site.WorksiteBindings;
import com.aetherianartificer.townstead.work.site.WorksiteKey;
import com.aetherianartificer.townstead.work.site.WorksiteRegister;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Opening a worksite's orders, and taking an edit back.
 *
 * <p>The three doors in the design — asking a worker, an Order Board, an Order Sheet — all end
 * here, because they open the same list. What differs between them is only what you had to build
 * to get in.</p>
 */
public final class OrdersOpener {

    private OrdersOpener() {}

    /** How far from a position to accept an already-registered standalone site as "here". */
    private static final int NEAR_RADIUS = 48;

    /** The registered worksite at this position, if any binding claims it. */
    @Nullable
    public static Worksite siteAt(ServerLevel level, BlockPos pos) {
        return siteAt(level, pos, false);
    }

    /**
     * The worksite covering this position, optionally minting one that nobody has worked yet.
     *
     * <p>Creating on demand is right for every door: a player standing in a kitchen asking what it
     * makes is a deliberate act, and waiting for a cook to happen by first would make the whole
     * feature invisible in a room that has not been used today.</p>
     */
    @Nullable
    public static Worksite siteAt(ServerLevel level, BlockPos pos, boolean createIfMissing) {
        WorksiteRegister register = WorksiteRegister.get(level.getServer());
        for (WorksiteBindings.Binding binding : WorksiteBindings.all()) {
            WorksiteKey key = binding.keyAt(level, pos);
            Worksite found = register.find(key);
            if (found != null) return found;
            if (!createIfMissing || key == null) continue;
            // The anchor binding claims any block at all, so it may only create where a real
            // station stands. A room binding already refuses anywhere outside a building.
            if (WorksiteBindings.ANCHOR.equals(key.binding())
                    && !com.aetherianartificer.townstead.work.station.Stations.isStation(level, pos)) {
                continue;
            }
            Worksite created = com.aetherianartificer.townstead.work.site.Worksites.of(level, key);
            if (created != null) return created;
        }
        // No binding claims this exact spot; fall back to the nearest registered standalone site so
        // a board beside the smoker opens the smoker's list rather than nothing at all.
        Worksite best = null;
        double bestDist = Double.MAX_VALUE;
        for (Worksite site : register.all()) {
            if (!site.key().dimension().equals(level.dimension().location())) continue;
            if (!WorksiteBindings.ANCHOR.equals(site.key().binding())) continue;
            double dist = site.key().pos().distSqr(pos);
            if (dist < bestDist && dist <= (double) NEAR_RADIUS * NEAR_RADIUS) {
                bestDist = dist;
                best = site;
            }
        }
        return best;
    }

    /** Sends the screen for a worksite. Returns false when there is nothing to show. */
    public static boolean open(ServerPlayer player, @Nullable Worksite site) {
        if (site == null) return false;
        ServerLevel level = player.serverLevel();
        OrderContext context = context(level, site);
        send(player, OrdersService.snapshot(level, site, context, options(level, site),
                WorksiteCatalogs.stationsFor(level, site)));
        OrdersWatchers.watch(player, site);
        return true;
    }

    /** Applies an edit and immediately sends the corrected state back, so the screen never guesses. */
    public static void edit(ServerPlayer player, OrderEditC2SPayload payload) {
        if (payload.action() == OrderEditC2SPayload.Action.CLOSED) {
            OrdersWatchers.forget(player);
            return;
        }
        ServerLevel level = player.serverLevel();
        OrdersService.apply(level, player, payload);
        Worksite site = WorksiteRegister.get(level.getServer()).byId(payload.worksiteId());
        if (site != null) open(player, site);
    }

    /** What this worksite could be asked to make, gathered from whichever engines work here. */
    private static List<Option> options(ServerLevel level, Worksite site) {
        return WorksiteCatalogs.optionsFor(level, site);
    }

    /**
     * The world answers an order needs. Stock is counted over the worksite's own extent and the
     * inventories of its assigned workers, so carrying goods between station and shelf does not
     * make them disappear from the order sheet.
     */
    public static OrderContext context(ServerLevel level, Worksite site) {
        return new OrderContext() {
            @Override
            public int stockOf(ResourceLocation item, Order.CountScope scope) {
                return WorksiteStock.count(level, site, item, scope);
            }

            @Override
            public int stockOfTag(ResourceLocation tagId, Order.CountScope scope) {
                return WorksiteStock.countTag(level, site, tagId, scope);
            }

            @Override
            public int villagerCount() {
                return WorksiteStock.villagers(level, site);
            }

            @Override
            public boolean mayWork(Order order) {
                if (order.villager() == null && order.profession() == null
                        && order.minRank() <= 0) return true;
                if (site.villageId() == Worksite.NO_VILLAGE) return false;
                for (net.conczin.mca.server.world.data.Village village
                        : net.conczin.mca.server.world.data.VillageManager.get(level)) {
                    if (village.getId() != site.villageId()) continue;
                    for (net.conczin.mca.entity.VillagerEntityMCA resident
                            : village.getResidents(level)) {
                        if (WorksiteOrders.contextFor(level, site, resident).mayWork(order)) {
                            return true;
                        }
                    }
                    return false;
                }
                return false;
            }
        };
    }

    private static void send(ServerPlayer player, OrdersSnapshotS2CPayload payload) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
        *///?}
    }
}
