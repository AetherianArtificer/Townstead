package com.aetherianartificer.townstead.clothing.wardrobe;

import com.aetherianartificer.townstead.clothing.ClothingEntry;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * The Wardrobe's two weather switches: warm layers when cold, light layers when hot. They only
 * gate what a villager does for the weather; outfits a player or a pack picks are untouched.
 */
public final class WeatherLayers {

    private WeatherLayers() {}

    public static boolean on(@Nullable VillagerEntityMCA villager, boolean warmLayer) {
        MinecraftServer server = villager == null ? null : villager.getServer();
        if (server == null) return true;
        return WardrobeAssignments.get(server).layersOn(villager.getUUID(), warmLayer);
    }

    /** Whether the villager may put this piece on for the weather. */
    public static boolean mayWear(VillagerEntityMCA villager, @Nullable ClothingEntry entry) {
        boolean warm = entry != null && entry.isWarm();
        boolean cool = entry != null && entry.isCool();
        if (warm != cool) return on(villager, warm);
        return on(villager, true) || on(villager, false);
    }

    /** Whether the villager may take this piece off for the weather: a warm piece in heat, a cool one in cold. */
    public static boolean mayShed(VillagerEntityMCA villager, @Nullable ClothingEntry entry) {
        boolean warm = entry != null && entry.isWarm();
        boolean cool = entry != null && entry.isCool();
        if (warm != cool) return on(villager, !warm);
        return on(villager, true) || on(villager, false);
    }
}
