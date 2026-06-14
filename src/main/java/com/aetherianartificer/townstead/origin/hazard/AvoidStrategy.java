package com.aetherianartificer.townstead.origin.hazard;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * The spatial half of an environmental hazard: how an entity that is harmed by a positional
 * condition keeps out of danger. The harming gene's own condition stays the "am I in danger now"
 * trigger; the strategy adds the spatial knowledge the condition cannot give: how to route around
 * the danger ({@link #installProactive}) and where "safe" is ({@link #safeAt}).
 *
 * <p>Derived once at gene-parse time from the action + condition JSON (so the "damage source" is
 * computed and cached on the parsed gene, shared by every entity of that origin), never per tick.
 * v1 ships {@link #SUNLIGHT} only; water / cold / etc. slot in as further constants.</p>
 */
public enum AvoidStrategy {
    /** Direct sunlight (the skeleton case): avoid pathing through sunlit tiles, flee to shade. */
    SUNLIGHT;

    /** Action types whose presence makes a positional condition worth fleeing (vs a benign one). */
    private static boolean isHarmful(String actionType) {
        return switch (actionType) {
            case "pheno:ignite", "pheno:damage", "pheno:freeze", "pheno:exhaust" -> true;
            default -> false;
        };
    }

    /**
     * The avoidance a harmful {@code action_over_time} gene implies, or null when it is not a
     * spatial hazard worth fleeing. Requires BOTH a harmful action (so a benign sun effect like
     * photosynthesis is not fled) and a recognised positional condition.
     */
    @Nullable
    public static AvoidStrategy derive(@Nullable JsonElement action, @Nullable JsonElement condition) {
        if (!anyAction(action, AvoidStrategy::isHarmful)) return null;
        if (exposesTo(condition, "sun")) return SUNLIGHT;
        return null;
    }

    /** Turn the always-on proactive avoidance for this strategy on (or off) for a pathfinding mob. */
    public void installProactive(PathfinderMob mob, boolean active) {
        switch (this) {
            case SUNLIGHT -> {
                if (mob.getNavigation() instanceof GroundPathNavigation nav) nav.setAvoidSun(active);
            }
        }
    }

    /** True when the position is safe from this hazard, used to pick a flee target. */
    public boolean safeAt(Level level, BlockPos pos) {
        return switch (this) {
            case SUNLIGHT -> !level.canSeeSky(pos);
        };
    }

    // --- JSON derivation (action may be a single object or an array of them) ---

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

    private static boolean exposesTo(@Nullable JsonElement condition, String token) {
        if (condition == null || !condition.isJsonObject()) return false;
        JsonObject obj = condition.getAsJsonObject();
        if (!"pheno:environment".equals(GsonHelper.getAsString(obj, "type", ""))) return false;
        JsonElement exposure = obj.get("exposure");
        if (exposure == null) return false;
        if (exposure.isJsonArray()) {
            for (JsonElement e : exposure.getAsJsonArray()) {
                if (e.isJsonPrimitive() && token.equals(e.getAsString())) return true;
            }
            return false;
        }
        return exposure.isJsonPrimitive() && token.equals(exposure.getAsString());
    }
}
