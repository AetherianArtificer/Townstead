package com.aetherianartificer.townstead.profession;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * Recognising a career by registry id, for the moments when profession defs are not loaded.
 *
 * <p>Everything at runtime should ask {@link com.aetherianartificer.townstead.work.WorkTaskDeclarations}
 * instead — a career is what its def says it works. Two places genuinely cannot: the villager
 * trades event fires before datapacks load, and a dedicated-server client has no defs at all.
 * Those read this table, which is why the ids are written out rather than derived.</p>
 */
public final class ProfessionAliases {

    /**
     * Early-load Cook carriers. At runtime the data registry distinguishes root aliases from
     * Path-specific identities (notably Chef); this list only answers whether Cook UI applies.
     */
    public static final String[] COOK = {
            "townstead:cook",
            "chefsdelight:cook",
            "chefsdelight:chef",
            "vca:cook",
            "villagerclothingaddition:cook"
    };

    /** Professions that mean "beverage artisan". Paths and compatibility aliases resolve through data. */
    public static final String[] BEVERAGE_ARTISAN = { "townstead:beverage_artisan" };

    private ProfessionAliases() {}

    /** Whether this profession is registered under any of these ids. */
    public static boolean isAnyOf(VillagerProfession profession, String... ids) {
        if (profession == null) return false;
        for (String id : ids) {
            //? if >=1.21 {
            ResourceLocation key = ResourceLocation.parse(id);
            //?} else {
            /*ResourceLocation key = new ResourceLocation(id);
            *///?}
            if (!BuiltInRegistries.VILLAGER_PROFESSION.containsKey(key)) continue;
            if (BuiltInRegistries.VILLAGER_PROFESSION.get(key) == profession) return true;
        }
        return false;
    }
}
