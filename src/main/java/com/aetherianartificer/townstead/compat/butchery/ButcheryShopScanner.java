package com.aetherianartificer.townstead.compat.butchery;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Helpers that locate a villager's assigned butcher shop and report its tier.
 * Used by the carcass workflow driver to (a) gate stages by tier and (b) scope
 * the carcass search to the shop footprint.
 *
 * Tier is inferred from the MCA building type id. Compat Butcher Shop buildings
 * are named {@code compat/butchery/butcher_shop_l<N>}; the vanilla MCA
 * {@code butcher} type (priority 1) also maps to tier 1 so non-Butchery
 * villages still get a sensible lookup.
 */
public final class ButcheryShopScanner {
    public static final int MIN_CARCASS_TIER = 2;
    private static final String COMPAT_PREFIX = "compat/butchery/butcher_shop_l";

    private ButcheryShopScanner() {}

    public record ShopRef(Building building, int tier) {}

    public static Optional<ShopRef> shopFor(ServerLevel level, VillagerEntityMCA villager) {
        Optional<Village> villageOpt = resolveVillage(villager);
        if (villageOpt.isEmpty()) return Optional.empty();
        Village village = villageOpt.get();

        ShopRef best = null;
        for (Building building : village.getBuildings().values()) {
            if (!building.isComplete()) continue;
            int tier = tierFromType(building.getType());
            if (tier <= 0) continue;
            if (best == null || tier > best.tier()) {
                best = new ShopRef(building, tier);
            }
        }
        return Optional.ofNullable(best);
    }

    public static int tierFor(ServerLevel level, VillagerEntityMCA villager) {
        return shopFor(level, villager).map(ShopRef::tier).orElse(0);
    }

    public static boolean posInShop(@Nullable ShopRef shop, BlockPos pos) {
        if (shop == null || pos == null) return false;
        return shop.building().containsPos(pos);
    }

    private static int tierFromType(String type) {
        if (type == null) return 0;
        if (type.equals("butcher")) return 1;
        if (type.startsWith(COMPAT_PREFIX)) {
            String tail = type.substring(COMPAT_PREFIX.length());
            try {
                int t = Integer.parseInt(tail);
                if (t >= 1 && t <= 3) return t;
            } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    private static Optional<Village> resolveVillage(VillagerEntityMCA villager) {
        Optional<Village> home = villager.getResidency().getHomeVillage();
        if (home.isPresent() && home.get().isWithinBorder(villager)) return home;
        Optional<Village> nearest = Village.findNearest(villager);
        if (nearest.isPresent() && nearest.get().isWithinBorder(villager)) return nearest;
        return Optional.empty();
    }
}
