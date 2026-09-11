package com.aetherianartificer.townstead.hangout;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.mca.McaBuildings;
import com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver;
import com.aetherianartificer.townstead.hunger.NearbyItemSources;
import com.aetherianartificer.townstead.hunger.VillagerConsumptionManager;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

import java.util.ArrayList;
import java.util.Comparator;

/** Seated self-service uses real venue stock and the normal held-item consumption lifecycle. */
final class HangoutDrinks {
    private HangoutDrinks() {}

    static void tick(ServerLevel level, VillagerEntityMCA guest, HangoutVisit visit,
                     HangoutVenue venue, long now) {
        if (!venue.amenities().contains("drink") || !guest.isPassenger()
                || now < visit.nextDrinkAt() || VillagerConsumptionManager.isConsuming(guest)) return;
        // Empty venues are checked infrequently; a serving is followed by a full minute's pause.
        visit.deferDrink(now + 200);
        if (serve(level, guest, visit)) visit.deferDrink(now + 1200);
    }

    private static boolean serve(ServerLevel level, VillagerEntityMCA guest, HangoutVisit visit) {
        if (!TownsteadConfig.ENABLE_CONTAINER_SOURCING.get()) return false;
        // Reuse the reloadable house-round admission rules, without its thirst/start condition.
        HangoutActivity round = HangoutData.activities().get(
                net.minecraft.resources.ResourceLocation.tryParse("townstead:tavern_round"));
        if (round == null) return false;
        ConditionContext context = new ConditionContext(guest);
        if (round.participantWhen() != null && !round.participantWhen().test(context)
                || round.serviceWhen() != null && !round.serviceWhen().test(context)) return false;

        Village village = guest.getResidency().getHomeVillage()
                .orElseGet(() -> Village.findNearest(guest).orElse(null));
        if (village == null) return false;
        var building = McaBuildings.byId(village, visit.buildingId());
        if (building == null || !building.isComplete()) return false;
        var slots = new ArrayList<NearbyItemSources.ContainerSlot>();
        NearbyItemSources.collectMatchingSlots(level, guest, 16, 8, HangoutDrinks::isDrink,
                stack -> 1, visit.venueAnchor(), slot -> {
                    if (McaBuildings.contains(level, village, building, slot.pos())) slots.add(slot);
                });
        slots.sort(Comparator.comparingDouble(NearbyItemSources.ContainerSlot::distanceSqr));
        for (var slot : slots) {
            if (slot.container() == null || slot.slot() >= slot.container().getContainerSize()
                    || !isDrink(slot.container().getItem(slot.slot()))) continue;
            ItemStack drink = NearbyItemSources.extractOne(level, slot);
            if (drink.isEmpty()) continue;
            // Recheck the live extraction: the cached inventory view can have changed.
            if (isDrink(drink) && VillagerConsumptionManager.startRecreationalDrink(guest, drink, slot.pos())) {
                return true;
            }
            // A rejected serving remains a real item, including when the guest's inventory is full.
            ItemStack overflow = guest.getInventory().addItem(drink);
            if (!overflow.isEmpty()) guest.spawnAtLocation(overflow);
        }
        return false;
    }

    private static boolean isDrink(ItemStack stack) {
        if (stack.isEmpty() || !VillagerConsumptionManager.permitsManagedVillagerConsumption(stack)) return false;
        var bridge = ThirstBridgeResolver.get();
        return stack.getUseAnimation() == UseAnim.DRINK
                || bridge != null && bridge.itemRestoresThirst(stack);
    }
}
