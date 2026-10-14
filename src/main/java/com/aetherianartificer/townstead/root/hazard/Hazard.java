package com.aetherianartificer.townstead.root.hazard;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Predicate;

/**
 * A place an entity's own gene says hurts it, and how it keeps clear. The gene's condition already
 * answers "am I in danger here?"; asking it again at another block ({@link ConditionContext#at})
 * answers "would I be in danger there?". So anything positional a condition can say, a mod check
 * included (Vampirism sun and garlic, wolfsbane, temperature), is avoidable with no per-hazard code.
 *
 * <p>Derived once when the gene parses. Settings come from the gene's {@code "avoid"}: {@code true}
 * forces avoidance for a benign-looking action, or an object tunes it:</p>
 * <pre>
 *   "avoid": { "flee_speed": 1.2, "roam_speed": 0.6, "search_radius": 8,
 *              "edge_distance": 3, "roam": true, "reject_destinations": true }
 * </pre>
 * <p>{@code "avoid": false} turns avoidance off for a harmful gene.</p>
 */
public final class Hazard {

    /** Leaf condition types whose answer depends on where the entity stands. */
    private static final Set<String> POSITIONAL = Set.of(
            "pheno:environment", "pheno:biome", "pheno:block", "pheno:block_in_radius", "pheno:brightness",
            "pheno:fluid_height", "pheno:submerged_in", "pheno:ambient_temperature", "pheno:near_marker",
            "pheno:distance_from_coordinates", "pheno:dimension");

    private final Condition condition;
    private final boolean sunlit;
    private final float fleeSpeed;
    private final float roamSpeed;
    private final int searchRadius;
    private final int edgeDistance;
    private final boolean roam;
    private final boolean rejectDestinations;

    private Hazard(Condition condition, boolean sunlit, JsonObject settings) {
        this.condition = condition;
        this.sunlit = sunlit;
        this.fleeSpeed = GsonHelper.getAsFloat(settings, "flee_speed", 1.2f);
        this.roamSpeed = GsonHelper.getAsFloat(settings, "roam_speed", 0.6f);
        this.searchRadius = Math.max(2, Math.min(32, GsonHelper.getAsInt(settings, "search_radius", 8)));
        this.edgeDistance = Math.max(1, Math.min(8, GsonHelper.getAsInt(settings, "edge_distance", 3)));
        this.roam = GsonHelper.getAsBoolean(settings, "roam", true);
        this.rejectDestinations = GsonHelper.getAsBoolean(settings, "reject_destinations", true);
    }

    /**
     * The hazard a periodic gene implies, or null. Needs a harmful action (so a benign sun effect
     * like photosynthesis is not fled) unless {@code avoid} declares it, and a condition that depends
     * on place, since only a place can be walked away from.
     */
    public static @Nullable Hazard derive(@Nullable JsonElement action, @Nullable JsonElement conditionJson,
                                          @Nullable Condition condition, @Nullable JsonElement avoid) {
        if (condition == null || conditionJson == null) return null;
        boolean declared = avoid != null && (avoid.isJsonObject()
                || avoid.isJsonPrimitive() && avoid.getAsJsonPrimitive().isBoolean() && avoid.getAsBoolean());
        boolean refused = avoid != null && avoid.isJsonPrimitive() && avoid.getAsJsonPrimitive().isBoolean()
                && !avoid.getAsBoolean();
        if (refused) return null;
        if (!declared && !anyAction(action, Hazard::isHarmful)) return null;
        if (!mentions(conditionJson, Hazard::positional)) return null;
        JsonObject settings = avoid != null && avoid.isJsonObject() ? avoid.getAsJsonObject() : new JsonObject();
        return new Hazard(condition, mentions(conditionJson, Hazard::sunExposure), settings);
    }

    /** Whether standing at {@code pos} would put this entity in the hazard. */
    public boolean dangerousAt(ConditionContext ctx, BlockPos pos) {
        try {
            return condition.test(ctx.at(pos));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /**
     * Keep the pathfinder off sunlit tiles (vanilla's own avoidance) while the open sky right above
     * the mob would actually hurt it. Night, rain and sun-safe places such as a vampire forest leave
     * its routes free.
     */
    public void installProactive(PathfinderMob mob, ConditionContext ctx) {
        if (!sunlit || !(mob.getNavigation() instanceof GroundPathNavigation nav)) return;
        BlockPos sky = mob.level().getHeightmapPos(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, mob.blockPosition());
        nav.setAvoidSun(dangerousAt(ctx, sky));
    }

    public static void clearProactive(PathfinderMob mob) {
        if (mob.getNavigation() instanceof GroundPathNavigation nav) nav.setAvoidSun(false);
    }

    public float fleeSpeed() { return fleeSpeed; }
    public float roamSpeed() { return roamSpeed; }
    public int searchRadius() { return searchRadius; }
    public int edgeDistance() { return edgeDistance; }
    public boolean roam() { return roam; }
    public boolean rejectDestinations() { return rejectDestinations; }

    private static boolean isHarmful(String actionType) {
        return switch (actionType) {
            case "pheno:ignite", "pheno:damage", "pheno:freeze", "pheno:exhaust" -> true;
            default -> false;
        };
    }

    private static boolean positional(JsonObject leaf) {
        String type = GsonHelper.getAsString(leaf, "type", "");
        if (POSITIONAL.contains(type)) return true;
        // A mod check read through a place-aware provider counts as a place.
        return "pheno:value".equals(type) && leaf.toString().contains("\"pheno:provider\"");
    }

    private static boolean sunExposure(JsonObject leaf) {
        String type = GsonHelper.getAsString(leaf, "type", "");
        if ("pheno:value".equals(type)) return leaf.toString().contains("sun_damage");
        if (!"pheno:environment".equals(type)) return false;
        JsonElement exposure = leaf.get("exposure");
        if (exposure == null) return false;
        if (exposure.isJsonArray()) {
            for (JsonElement e : exposure.getAsJsonArray()) {
                if (e.isJsonPrimitive() && "sun".equals(e.getAsString())) return true;
            }
            return false;
        }
        return exposure.isJsonPrimitive() && "sun".equals(exposure.getAsString());
    }

    private static boolean anyAction(@Nullable JsonElement action, Predicate<String> typeMatches) {
        if (action == null) return false;
        if (action.isJsonArray()) {
            for (JsonElement e : action.getAsJsonArray()) {
                if (anyAction(e, typeMatches)) return true;
            }
            return false;
        }
        return action.isJsonObject()
                && typeMatches.test(GsonHelper.getAsString(action.getAsJsonObject(), "type", ""));
    }

    /** True when a positive (un-negated) part of the condition tree matches {@code leaf}. */
    private static boolean mentions(@Nullable JsonElement condition, Predicate<JsonObject> leaf) {
        if (condition == null || !condition.isJsonObject()) return false;
        JsonObject obj = condition.getAsJsonObject();
        String type = GsonHelper.getAsString(obj, "type", "");
        if (("pheno:and".equals(type) || "pheno:or".equals(type)) && obj.get("conditions") != null
                && obj.get("conditions").isJsonArray()) {
            for (JsonElement child : obj.getAsJsonArray("conditions")) {
                if (mentions(child, leaf)) return true;
            }
            return false;
        }
        if (GsonHelper.getAsBoolean(obj, "inverted", false) || "pheno:not".equals(type)) return false;
        return leaf.test(obj);
    }
}
