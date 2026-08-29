package com.aetherianartificer.townstead.pheno.reservation;

import com.aetherianartificer.townstead.pheno.selector.Selector;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.aetherianartificer.townstead.pheno.selector.Selectors;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Parsed, host-independent description of which entities a reservation may acquire. */
public record ReservationSpec(JsonElement onJson, Selector automatic, Selector eligible,
                              @Nullable Condition filter) {

    public static @Nullable ReservationSpec parse(JsonObject json) {
        if (json == null || !json.has("on")) return null;
        JsonElement on = json.get("on").deepCopy();
        Selector automatic = Selectors.parse(on);
        if (automatic == null) return null;
        JsonElement all = on.deepCopy();
        if (all.isJsonObject()) {
            all.getAsJsonObject().remove("order");
            all.getAsJsonObject().remove("limit");
        }
        Selector eligible = Selectors.parse(all);
        Condition filter = on.isJsonObject() && on.getAsJsonObject().has("where")
                ? Conditions.parse(on.getAsJsonObject().get("where")) : null;
        if (on.isJsonObject() && on.getAsJsonObject().has("where") && filter == null) return null;
        return eligible == null ? null : new ReservationSpec(on, automatic, eligible, filter);
    }

    public List<LivingEntity> candidates(Level level, BlockPos pos, @Nullable Integer villageId) {
        return candidates(level, pos, villageId, null);
    }

    /**
     * Resolves the reservation in its host execution frame.  A station supplies its worker as the
     * origin, which lets ordinary Pheno such as {@code [village query, "origin"]} express a
     * preferred external participant with a self-operated fallback.  Browsing candidates supplies
     * no origin, so the worker is never presented as a manually assignable animal.
     */
    public List<LivingEntity> candidates(Level level, BlockPos pos, @Nullable Integer villageId,
                                         @Nullable LivingEntity origin) {
        return eligible.select(context(level, pos, villageId, origin));
    }

    public boolean accepts(Level level, BlockPos pos, @Nullable Integer villageId,
                           LivingEntity entity) {
        return accepts(level, pos, villageId, entity, null);
    }

    public boolean accepts(Level level, BlockPos pos, @Nullable Integer villageId,
                           LivingEntity entity, @Nullable LivingEntity origin) {
        return candidates(level, pos, villageId, origin).stream()
                .anyMatch(candidate -> candidate.getUUID().equals(entity.getUUID()));
    }

    /** Whether this authored selection has the surrounding actor as a fallback candidate. */
    public boolean referencesOrigin() {
        return referencesRole(onJson, "origin") || referencesRole(onJson, "actor")
                || referencesRole(onJson, "bearer");
    }

    public boolean matchesFilter(LivingEntity entity) {
        return entity != null && entity.isAlive()
                && (filter == null || filter.test(new ConditionContext(entity)));
    }

    /** Reapplies the selector's authored order/limit after a host removes unavailable targets. */
    public List<LivingEntity> prioritize(Level level, BlockPos pos,
                                         List<? extends LivingEntity> available) {
        List<LivingEntity> out = new ArrayList<>(available);
        JsonObject json = onJson.isJsonObject() ? onJson.getAsJsonObject() : null;
        String order = json == null || !json.has("order") ? "any"
                : json.get("order").getAsString().toLowerCase(Locale.ROOT);
        switch (order) {
            case "nearest", "closest" -> out.sort(Comparator.comparingDouble(
                    entity -> entity.distanceToSqr(pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d)));
            case "farthest", "furthest" -> out.sort(Comparator.comparingDouble(
                    (LivingEntity entity) -> entity.distanceToSqr(
                            pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d)).reversed());
            case "random" -> {
                for (int i = out.size() - 1; i > 0; i--) {
                    int j = level.getRandom().nextInt(i + 1);
                    LivingEntity value = out.get(i);
                    out.set(i, out.get(j));
                    out.set(j, value);
                }
            }
            default -> { }
        }
        int limit = json == null || !json.has("limit") ? 0 : Math.max(0, json.get("limit").getAsInt());
        return limit > 0 && out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    private static SelectorContext context(Level level, BlockPos pos, @Nullable Integer villageId,
                                           @Nullable LivingEntity origin) {
        SelectorContext context = SelectorContext.ofBlock(level, pos, origin);
        return villageId == null ? context : context.withVillage(villageId);
    }

    private static boolean referencesRole(JsonElement element, String role) {
        if (element == null) return false;
        if (element.isJsonPrimitive()) {
            return element.getAsJsonPrimitive().isString()
                    && role.equalsIgnoreCase(element.getAsString());
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                if (referencesRole(child, role)) return true;
            }
        }
        return false;
    }
}
