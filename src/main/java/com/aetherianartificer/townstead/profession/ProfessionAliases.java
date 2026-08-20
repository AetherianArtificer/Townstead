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

    /** Professions that mean "cook" to another mod, plus Townstead's own. */
    public static final String[] COOK = {
            "townstead:cook",
            "chefsdelight:cook",
            "chefsdelight:chef",
            "vca:cook",
            "villagerclothingaddition:cook"
    };

    /** Professions that mean "barista". */
    public static final String[] BARISTA = { "townstead:barista" };

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
