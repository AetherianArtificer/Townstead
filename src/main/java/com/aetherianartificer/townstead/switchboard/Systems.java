package com.aetherianartificer.townstead.switchboard;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Whether each Townstead system runs in this world. An off system stops acting: its tickers return,
 * its AI tasks never start, and its screens and lines stay hidden. Its saved data is left alone.
 */
public final class Systems {
    private Systems() {}

    public static final String CAREERS = "careers";
    public static final String WORK = "work";
    public static final String FARMING = "farming";
    public static final String FISHING = "fishing";
    public static final String SHEPHERDING = "shepherding";
    public static final String HOSPITALITY = "hospitality";
    public static final String CLOTHING = "clothing";
    public static final String SHIFTS = "shifts";
    public static final String HANGOUTS = "hangouts";
    public static final String REACTIONS = "reactions";
    public static final String ROOTS = "roots";
    public static final String CULTURES = "cultures";
    public static final String NAMING = "naming";
    public static final String CHRONICLES = "chronicles";
    public static final String POLITICS = "politics";
    public static final String SPIRIT = "spirit";
    public static final String CALENDAR = "calendar";
    public static final String QUESTS = "quests";

    /** Never throws: a system counts as on until the config and the world say otherwise. */
    public static boolean on(String system) {
        Supplier<Boolean> value;
        try {
            value = TownsteadConfig.SYSTEMS.get(system);
        } catch (LinkageError e) {
            // The config class cannot load without a mod loader, as in unit tests.
            return true;
        }
        if (value == null) return true;
        try {
            return Switchboard.get(value);
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static BooleanSupplier gate(String system) {
        return () -> on(system);
    }

    /**
     * The values that make Townstead do nothing in a world: every system switch off, the needs and
     * behaviors that carry their own switch off, and aging held still.
     */
    public static Map<String, JsonElement> everythingOff() {
        Map<String, JsonElement> out = new LinkedHashMap<>();
        for (String system : TownsteadConfig.SYSTEM_NAMES) out.put(key(system), new JsonPrimitive(false));
        List<Supplier<Boolean>> off = new ArrayList<>();
        Collections.addAll(off, TownsteadConfig.ENABLE_VILLAGER_HUNGER, TownsteadConfig.ENABLE_VILLAGER_THIRST,
                TownsteadConfig.ENABLE_VILLAGER_TEMPERATURE, TownsteadConfig.ENABLE_VILLAGER_FATIGUE,
                TownsteadConfig.ENABLE_CONVERSATIONS, TownsteadConfig.ENABLE_WORK_FEEDBACK, TownsteadConfig.ALLOW_LETHAL_WORK,
                TownsteadConfig.ENABLE_FEEDING_YOUNG, TownsteadConfig.ENABLE_HYDRATING_YOUNG,
                TownsteadConfig.ENABLE_NON_PARENT_CAREGIVERS, TownsteadConfig.ENABLE_WORK_SUPPLY_AUTOMATION,
                TownsteadConfig.ENABLE_HARVEST_OUTPUT_STORAGE, TownsteadConfig.ENABLE_FARM_ASSIST,
                TownsteadConfig.ENABLE_MCA_BUILDING_DISCOVERY, TownsteadConfig.ENABLE_TOWNSTEAD_COOK,
                TownsteadConfig.ENABLE_STABLE_NAMING_REGISTERS, TownsteadConfig.ENABLE_CHORUS_FRUIT_TELEPORT,
                TownsteadConfig.ENABLE_EMPTY_CONTAINER_DROPOFF);
        for (Supplier<Boolean> value : off) {
            String k = SettingIndex.keyOf(value);
            if (k != null) out.put(k, new JsonPrimitive(false));
        }
        String aging = SettingIndex.keyOf(TownsteadConfig.DISABLE_VILLAGER_AGING);
        if (aging != null) out.put(aging, new JsonPrimitive(true));
        return out;
    }

    /** The key of a system's switch, as the Switchboard names it. */
    public static String key(String system) {
        return "systems." + system;
    }
}
