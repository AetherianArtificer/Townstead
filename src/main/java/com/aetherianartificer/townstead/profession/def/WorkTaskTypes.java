package com.aetherianartificer.townstead.profession.def;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The vocabulary of work task types professions may declare in {@code work_tasks}. Each id names
 * a task engine in Townstead code; any profession may declare any type, so a pack's own careers
 * can compose from the full engine library. The loader rejects declarations of unknown types so
 * typos surface as diagnostics instead of silently idle villagers. Types live in their own
 * {@code townstead_work} namespace (bare ids resolve into it) so they never collide with
 * profession or content ids like {@code townstead:cook}.
 */
public final class WorkTaskTypes {

    /** Default namespace for work task types; bare {@code type} ids resolve into it. */
    public static final String NAMESPACE = "townstead_work";

    private static final Set<ResourceLocation> KNOWN = ConcurrentHashMap.newKeySet();

    /**
     * Types driven by the generic station engine rather than a bespoke task class. The routing
     * lives here in the vocabulary because both failure modes of guessing are bad: a type two
     * engines serve double-drives its stations, and a type no engine serves puts orders on the
     * sheet that wait forever. Declaring the driver where the type is named makes either mistake
     * a one-line diff review.
     */
    private static final Set<ResourceLocation> STATION_DRIVEN = ConcurrentHashMap.newKeySet();
    private static volatile ResourceLocation[] STATION_DRIVEN_SNAPSHOT = new ResourceLocation[0];

    // Cook family (Farmer's Delight producer machinery).
    public static final ResourceLocation COOK = type("cook");
    public static final ResourceLocation CHOP = type("chop");
    public static final ResourceLocation BREW = type("brew");

    // Zone engines.
    public static final ResourceLocation HARVEST = type("harvest");
    public static final ResourceLocation FISH = type("fish");
    public static final ResourceLocation INTERACT = type("interact");

    // Butchery suite.
    public static final ResourceLocation SLAUGHTER = type("slaughter");
    public static final ResourceLocation BUTCHER = type("butcher");
    public static final ResourceLocation DISMANTLE = type("dismantle");
    public static final ResourceLocation GRIND = stationDriven("grind");
    public static final ResourceLocation TAXIDERMY = stationDriven("taxidermy");
    public static final ResourceLocation SMOKE = type("smoke");
    public static final ResourceLocation CURE = type("cure");
    public static final ResourceLocation CLEAN = type("clean");
    public static final ResourceLocation HAMMER = type("hammer");
    public static final ResourceLocation DELIVER = type("deliver");

    // Smith family, served by the generic station engine: a workstation def naming this type is
    // all it takes for the work to run.
    public static final ResourceLocation SMELT = stationDriven("smelt");
    // Working recipes at a surface that holds nothing (crafting table, stonecutter). What a
    // trade may craft is its own declaration's recipes filter, never the family's full breadth.
    public static final ResourceLocation CRAFT = stationDriven("craft");

    // Tend / leatherwork / storage.
    public static final ResourceLocation SHEAR = type("shear");
    public static final ResourceLocation TAN = type("tan");
    public static final ResourceLocation STORE = type("store");

    /** Every butchery-suite type, for gates that serve the whole shop. Do not mutate. */
    public static final ResourceLocation[] BUTCHERY_SUITE = {
            SLAUGHTER, BUTCHER, DISMANTLE, GRIND, TAXIDERMY, SMOKE, CURE, CLEAN, HAMMER, DELIVER};

    private WorkTaskTypes() {}

    private static ResourceLocation type(String path) {
        ResourceLocation id = ResourceLocation.tryParse(NAMESPACE + ":" + path);
        KNOWN.add(id);
        return id;
    }

    private static ResourceLocation stationDriven(String path) {
        ResourceLocation id = type(path);
        STATION_DRIVEN.add(id);
        STATION_DRIVEN_SNAPSHOT = STATION_DRIVEN.toArray(ResourceLocation[]::new);
        return id;
    }

    public static boolean isStationDriven(@Nullable ResourceLocation id) {
        return id != null && STATION_DRIVEN.contains(id);
    }

    /** The station-driven types as an array for allocation-free eligibility scans. Do not mutate. */
    public static ResourceLocation[] stationDrivenTypes() {
        return STATION_DRIVEN_SNAPSHOT;
    }

    public static void register(ResourceLocation id) {
        if (id != null) KNOWN.add(id);
    }

    public static boolean knows(@Nullable ResourceLocation id) {
        return id != null && KNOWN.contains(id);
    }

    /**
     * Chronicle activities owned by code-driven task engines. Data-driven Jobs supersede these
     * with their own resource ids; this table gives older engines the same automatic ownership.
     */
    public static List<String> activities(ResourceLocation id) {
        if (id == null) return List.of();
        if (id.equals(COOK) || id.equals(CHOP)) {
            return List.of("townstead:cooked");
        }
        if (id.equals(BREW)) return List.of("townstead:brewed");
        if (id.equals(HARVEST)) {
            return List.of("townstead:harvested", "townstead:planted", "townstead:tilled",
                    "townstead:groomed", "townstead:irrigated", "townstead:farmed");
        }
        if (id.equals(SHEAR)) return List.of("townstead:tended");
        if (id.equals(CLEAN)) return List.of("townstead:cleaned");
        if (id.equals(SLAUGHTER)) return List.of("townstead:slaughtered");
        if (id.equals(BUTCHER) || id.equals(DISMANTLE) || id.equals(SMOKE) || id.equals(CURE)
                || id.equals(HAMMER) || id.equals(DELIVER)) {
            return List.of("townstead:butchered");
        }
        if (id.equals(GRIND) || id.equals(TAXIDERMY) || id.equals(SMELT) || id.equals(CRAFT)) {
            return List.of("townstead:produced");
        }
        return List.of();
    }
}
