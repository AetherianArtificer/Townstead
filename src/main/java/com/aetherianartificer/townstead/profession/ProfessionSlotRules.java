package com.aetherianartificer.townstead.profession;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class ProfessionSlotRules {
    private static final String NONE_ID = "minecraft:none";
    private static final String NITWIT_ID = "minecraft:nitwit";
    private static final String GUARD_ID = "mca:guard";
    private static final String ARCHER_ID = "mca:archer";
    private static final String COOK_ID = "townstead:cook";
    private static final String BARISTA_ID = "townstead:barista";

    private ProfessionSlotRules() {}

    public enum SlotPolicy {
        UNLIMITED,
        POI_LIMITED,
        CUSTOM_BUILDING_SLOTS
    }

    public static SlotPolicy classify(VillagerProfession profession) {
        String professionId = professionKey(profession);
        boolean hasJobSite = profession != null && profession.heldJobSite() != PoiType.NONE;
        return classify(professionId, hasJobSite);
    }

    static SlotPolicy classify(String professionId, boolean hasJobSite) {
        if (professionId == null || professionId.isBlank() || NONE_ID.equals(professionId) || NITWIT_ID.equals(professionId)) {
            return SlotPolicy.UNLIMITED;
        }
        if (COOK_ID.equals(professionId) || BARISTA_ID.equals(professionId)) {
            return SlotPolicy.CUSTOM_BUILDING_SLOTS;
        }
        if (GUARD_ID.equals(professionId) || ARCHER_ID.equals(professionId)) {
            return SlotPolicy.UNLIMITED;
        }
        return hasJobSite ? SlotPolicy.POI_LIMITED : SlotPolicy.UNLIMITED;
    }

    public static boolean requiresJobSite(VillagerProfession profession) {
        return classify(profession) == SlotPolicy.POI_LIMITED;
    }

    public static boolean isAlwaysVisible(VillagerProfession profession) {
        String professionId = professionKey(profession);
        return GUARD_ID.equals(professionId) || ARCHER_ID.equals(professionId);
    }

    public static String professionKey(VillagerProfession profession) {
        ResourceLocation key = profession == null ? null : BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
        return key != null ? key.toString() : NONE_ID;
    }
}
