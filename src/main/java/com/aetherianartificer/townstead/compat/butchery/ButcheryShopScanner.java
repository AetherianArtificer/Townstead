package com.aetherianartificer.townstead.compat.butchery;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Helpers that locate butchery-related buildings in the villager's village
 * and classify their capabilities.
 *
 * <p>The butcher's workflow spans multiple buildings: they may kill in a
 * Slaughterhouse, bleed and quarter there, walk to a Butcher Shop or
 * Smokehouse to smoke the cuts, and drop hides at a Tannery. Each task
 * picks the building it needs rather than pinning to a single "shop".
 *
 * <p>Tier is inferred from the MCA building type id. Recognized types:
 * <ul>
 *   <li>{@code butcher} (vanilla MCA) → tier 1 (smoking only).</li>
 *   <li>{@code compat/butchery/butcher_shop_lN} → tier N.</li>
 *   <li>{@code compat/butchery/slaughterhouse} → tier 2 (dedicated kill
 *       facility; qualifies for carcass work without a Butcher Shop upgrade).</li>
 * </ul>
 */
public final class ButcheryShopScanner {
    public static final int MIN_CARCASS_TIER = 2;
    private static final String COMPAT_PREFIX = "compat/butchery/butcher_shop_l";
    private static final String SLAUGHTERHOUSE_TYPE = "compat/butchery/slaughterhouse";
    private static final String SLAUGHTER_PEN_TYPE = "compat/butchery/slaughter_pen";

    //? if >=1.21 {
    private static final ResourceLocation HOOK_ID = ResourceLocation.parse("butchery:hook");
    //?} else {
    /*private static final ResourceLocation HOOK_ID = new ResourceLocation("butchery", "hook");
    *///?}

    private ButcheryShopScanner() {}

    public record ShopRef(Building building, int tier) {}

    /** Union of carcass-capable shops and slaughter pens. Butchers pull slaughter targets from any of these. */
    public record HuntRef(Building building, boolean isShop) {}

    /**
     * All butchery-related buildings in the villager's village, sorted by
     * tier descending (so callers iterating the list hit the most capable
     * buildings first).
     */
    public static List<ShopRef> allShops(ServerLevel level, VillagerEntityMCA villager) {
        Optional<Village> villageOpt = resolveVillage(villager);
        if (villageOpt.isEmpty()) return Collections.emptyList();
        List<ShopRef> out = new ArrayList<>();
        for (Building building : villageOpt.get().getBuildings().values()) {
            if (!building.isComplete()) continue;
            int tier = tierFromType(building.getType());
            if (tier <= 0) continue;
            out.add(new ShopRef(building, tier));
        }
        out.sort((a, b) -> Integer.compare(b.tier(), a.tier()));
        return out;
    }

    /**
     * Buildings capable of carcass processing (tier ≥ 2), in the order the
     * caller should consider them. Used by both slaughter and carcass tasks.
     */
    public static List<ShopRef> carcassCapableShops(ServerLevel level, VillagerEntityMCA villager) {
        List<ShopRef> all = allShops(level, villager);
        List<ShopRef> out = new ArrayList<>(all.size());
        for (ShopRef ref : all) {
            if (ref.tier() >= MIN_CARCASS_TIER) out.add(ref);
        }
        return out;
    }

    /** Highest-tier butchery-related building in the village, if any. */
    public static Optional<ShopRef> shopFor(ServerLevel level, VillagerEntityMCA villager) {
        List<ShopRef> all = allShops(level, villager);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    public static int tierFor(ServerLevel level, VillagerEntityMCA villager) {
        return shopFor(level, villager).map(ShopRef::tier).orElse(0);
    }

    public static boolean posInShop(@Nullable ShopRef shop, BlockPos pos) {
        if (shop == null || pos == null) return false;
        return shop.building().containsPos(pos);
    }

    /**
     * All buildings the butcher can pull slaughter targets from: carcass-capable
     * shops (which may legitimately hold livestock) and dedicated slaughter pens.
     * Generic fenced enclosures are intentionally ignored — players opt in by
     * building a slaughter pen with a blood grate.
     */
    public static List<HuntRef> huntableBuildings(ServerLevel level, VillagerEntityMCA villager) {
        Optional<Village> villageOpt = resolveVillage(villager);
        if (villageOpt.isEmpty()) return Collections.emptyList();
        List<HuntRef> out = new ArrayList<>();
        for (Building b : villageOpt.get().getBuildings().values()) {
            if (!b.isComplete()) continue;
            String type = b.getType();
            int tier = tierFromType(type);
            if (tier >= MIN_CARCASS_TIER) {
                out.add(new HuntRef(b, true));
            } else if (SLAUGHTER_PEN_TYPE.equals(type)) {
                out.add(new HuntRef(b, false));
            }
        }
        return out;
    }

    /** Hook position in any carcass-capable shop in the village, or null if none has one. */
    @Nullable
    public static BlockPos findHookInAnyShop(ServerLevel level, VillagerEntityMCA villager) {
        for (ShopRef ref : carcassCapableShops(level, villager)) {
            List<BlockPos> positions = ref.building().getBlocks().get(HOOK_ID);
            if (positions != null && !positions.isEmpty()) return positions.get(0);
        }
        return null;
    }

    private static int tierFromType(String type) {
        if (type == null) return 0;
        if (type.equals("butcher")) return 1;
        if (type.equals(SLAUGHTERHOUSE_TYPE)) return 2;
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
