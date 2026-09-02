package com.aetherianartificer.townstead.food;

import com.aetherianartificer.townstead.profession.ProfessionSites;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.work.order.OrderProducts;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Building-authored list of products workers may deliver to serving surfaces. */
public final class BuildingServingMenus {
    private static volatile Map<String, Set<ResourceLocation>> PRODUCTS = Map.of();

    private BuildingServingMenus() {}

    public static void replaceAll(Map<String, Set<ResourceLocation>> next) {
        Map<String, Set<ResourceLocation>> stable = new LinkedHashMap<>();
        next.forEach((type, products) -> stable.put(type, Set.copyOf(products)));
        PRODUCTS = Map.copyOf(stable);
    }

    /** Missing menu means unrestricted; an authored menu admits only its listed products. */
    public static boolean allows(String buildingType, ResourceLocation item, ResourceLocation product) {
        if (buildingType == null || !PRODUCTS.containsKey(buildingType)) return true;
        Set<ResourceLocation> products = PRODUCTS.getOrDefault(buildingType, Set.of());
        return products.contains(item) || products.contains(product);
    }

    public static boolean allowsAssigned(ServerLevel level, VillagerEntityMCA villager, ItemStack stack) {
        if (level == null || villager == null || stack == null || stack.isEmpty()) return false;
        Set<ResourceLocation> menu = assignedMenu(level, villager);
        if (menu == null) return true;
        return menu.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                || menu.contains(OrderProducts.key(stack));
    }

    /** The menu authored for this worker's assigned building, or null when it has none. */
    @Nullable
    public static Set<ResourceLocation> assignedMenu(ServerLevel level, VillagerEntityMCA villager) {
        if (level == null || villager == null) return null;
        ResourceLocation professionId = BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(villager.getVillagerData().getProfession());
        var profession = ProfessionDefs.byId(professionId);
        if (profession == null) return null;
        String buildingType = ProfessionSites.assignedSite(level, villager, profession)
                .map(site -> site.building() == null ? null : site.building().getType())
                .orElse(null);
        return buildingType == null ? null : PRODUCTS.get(buildingType);
    }
}
