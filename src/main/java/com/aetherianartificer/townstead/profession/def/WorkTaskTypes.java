package com.aetherianartificer.townstead.profession.def;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

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

    // Cook family (Farmer's Delight producer machinery).
    public static final ResourceLocation COOK = type("cook");
    public static final ResourceLocation CHOP = type("chop");
    public static final ResourceLocation BREW = type("brew");

    // Zone engines.
    public static final ResourceLocation HARVEST = type("harvest");
    public static final ResourceLocation FISH = type("fish");

    // Butchery suite.
    public static final ResourceLocation SLAUGHTER = type("slaughter");
    public static final ResourceLocation BUTCHER = type("butcher");
    public static final ResourceLocation DISMANTLE = type("dismantle");
    public static final ResourceLocation GRIND = type("grind");
    public static final ResourceLocation SMOKE = type("smoke");
    public static final ResourceLocation CURE = type("cure");
    public static final ResourceLocation CLEAN = type("clean");
    public static final ResourceLocation HAMMER = type("hammer");
    public static final ResourceLocation DELIVER = type("deliver");

    // Tend / leatherwork / storage.
    public static final ResourceLocation SHEAR = type("shear");
    public static final ResourceLocation TAN = type("tan");
    public static final ResourceLocation STORE = type("store");

    /** Every butchery-suite type, for gates that serve the whole shop. Do not mutate. */
    public static final ResourceLocation[] BUTCHERY_SUITE = {
            SLAUGHTER, BUTCHER, DISMANTLE, GRIND, SMOKE, CURE, CLEAN, HAMMER, DELIVER};

    private WorkTaskTypes() {}

    private static ResourceLocation type(String path) {
        ResourceLocation id = ResourceLocation.tryParse(NAMESPACE + ":" + path);
        KNOWN.add(id);
        return id;
    }

    public static void register(ResourceLocation id) {
        if (id != null) KNOWN.add(id);
    }

    public static boolean knows(@Nullable ResourceLocation id) {
        return id != null && KNOWN.contains(id);
    }
}
