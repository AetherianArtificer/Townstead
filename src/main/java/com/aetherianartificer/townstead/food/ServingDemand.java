package com.aetherianartificer.townstead.food;

import com.aetherianartificer.townstead.work.order.OrderProducts;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/**
 * The serving menu read as standing demand.
 *
 * <p>An authored menu used to be a filter only: it said what may go on a plate, and plates filled
 * up as a side effect of whatever a worker happened to produce. Here an empty serving surface in
 * the worksite is treated as a request for one menu dish, which is what a player who wrote the
 * menu expected all along. Explicit orders still come first; this ranks just below them.</p>
 */
public final class ServingDemand {

    private ServingDemand() {}

    /**
     * Menu products this worker should be putting on plates right now. Empty when the building
     * has no menu or no plate in the worksite is empty. A dish the worker already carries is
     * the producer's business: it plates that before fetching or cooking another.
     */
    public static Set<ResourceLocation> standing(ServerLevel level, VillagerEntityMCA villager,
                                                 Set<Long> worksiteBounds) {
        if (level == null || villager == null || worksiteBounds == null || worksiteBounds.isEmpty()) {
            return Set.of();
        }
        Set<ResourceLocation> menu = BuildingServingMenus.assignedMenu(level, villager);
        if (menu == null || menu.isEmpty()) return Set.of();
        if (!ServingPlateService.hasEmptySurface(level, worksiteBounds)) return Set.of();
        return menu;
    }

    /** Whether this stack is one of the demanded dishes and can actually sit on a plate. */
    public static boolean matches(Set<ResourceLocation> demand, ItemStack stack) {
        if (demand == null || demand.isEmpty() || stack == null || stack.isEmpty()) return false;
        if (!ServingPlateService.canServe(stack)) return false;
        return demand.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                || demand.contains(OrderProducts.key(stack));
    }
}
