package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.chronicle.emit.ChronicleTaps;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.social.RelationshipLedger;
import com.aetherianartificer.townstead.social.RelationshipService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Writes a repeat-safe, directional relationship and memory result for a two-person Pheno action. */
public final class SocialOutcomeActionType implements ActionType {
    public static final String KEY = "pheno:social_outcome";

    public record Change(String quality, float amount, int halfLifeDays) {}

    @Override public String key() { return KEY; }

    @Override
    public Action parse(JsonObject json) {
        only(json, "type", "source", "actor_relationship", "other_relationship",
                "actor_memory", "other_memory");
        ResourceLocation source = ResourceLocation.tryParse(GsonHelper.getAsString(json, "source", ""));
        if (source == null) throw bad("source", "expected resource id");
        List<Change> actorChanges = changes(json, "actor_relationship");
        List<Change> otherChanges = changes(json, "other_relationship");
        String actorMemory = memory(json, "actor_memory");
        String otherMemory = memory(json, "other_memory");
        if (actorChanges.isEmpty() && otherChanges.isEmpty()
                && actorMemory.isBlank() && otherMemory.isBlank()) {
            throw bad("social_outcome", "must write at least one relationship quality or memory");
        }

        return context -> {
            if (!(context.level() instanceof ServerLevel level) || context.other() == null) {
                context.fail();
                return;
            }
            var actor = context.entity();
            var other = context.other();
            long day = TownsteadCalendar.worldDay(level.getServer());
            String operation = "social_outcome:" + source + ":" + level.getGameTime() + ":"
                    + actor.getUUID() + ":" + other.getUUID();
            var data = RelationshipService.data(level.getServer());
            boolean changed = apply(data, actor.getUUID(), other.getUUID(), operation + ":actor", actorChanges, day,
                    source.toString());
            changed |= apply(data, other.getUUID(), actor.getUUID(), operation + ":other", otherChanges, day,
                    source.toString());
            if (!actorMemory.isBlank()) changed |= data.addEpisodicMemory(actor.getUUID(),
                    operation + ":actor:memory", actorMemory, other.getUUID(), day, source.toString(), Map.of());
            if (!otherMemory.isBlank()) changed |= data.addEpisodicMemory(other.getUUID(),
                    operation + ":other:memory", otherMemory, actor.getUUID(), day, source.toString(), Map.of());
            if (changed) ChronicleTaps.socialOutcome(actor, other, source, actorMemory, otherMemory);
        };
    }

    private static boolean apply(com.aetherianartificer.townstead.chronicle.store.ChronicleSavedData data,
                                 java.util.UUID from, java.util.UUID toward, String operation,
                                 List<Change> changes, long day, String source) {
        boolean changed = false;
        for (Change change : changes) {
            changed |= data.applyRelationship(from, toward, new RelationshipLedger.Contribution(
                    operation + ":" + change.quality(), change.quality(), change.amount(), day,
                    change.halfLifeDays() < 0
                            ? com.aetherianartificer.townstead.social.RelationshipQualities.byId(
                            change.quality()).defaultHalfLifeDays()
                            : change.halfLifeDays(), source));
        }
        return changed;
    }

    private static List<Change> changes(JsonObject json, String field) {
        if (!json.has(field)) return List.of();
        JsonArray array = GsonHelper.getAsJsonArray(json, field);
        if (array.size() > 16) throw bad(field, "must contain at most 16 changes");
        ArrayList<Change> values = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (int i = 0; i < array.size(); i++) {
            JsonObject value = array.get(i).getAsJsonObject();
            only(value, "quality", "amount", "half_life_days");
            String quality = GsonHelper.getAsString(value, "quality", "").trim();
            if (ResourceLocation.tryParse(quality) == null) throw bad(field + "[" + i + "].quality", "expected resource id");
            if (!seen.add(quality)) throw bad(field, "contains duplicate quality " + quality);
            float amount = GsonHelper.getAsFloat(value, "amount", 0);
            if (!Float.isFinite(amount) || amount == 0 || amount < -100 || amount > 100)
                throw bad(field + "[" + i + "].amount", "must be nonzero and between -100 and 100");
            boolean overridesHalfLife = value.has("half_life_days");
            int halfLife = overridesHalfLife ? GsonHelper.getAsInt(value, "half_life_days") : -1;
            if (overridesHalfLife && halfLife < 0)
                throw bad(field + "[" + i + "].half_life_days", "must be >= 0");
            values.add(new Change(quality, amount, halfLife));
        }
        return List.copyOf(values);
    }

    private static String memory(JsonObject json, String field) {
        if (!json.has(field)) return "";
        String value = GsonHelper.getAsString(json, field, "").trim();
        if (ResourceLocation.tryParse(value) == null) throw bad(field, "expected resource id");
        return value;
    }

    private static void only(JsonObject json, String... fields) {
        Set<String> allowed = Set.of(fields);
        for (String field : json.keySet()) if (!allowed.contains(field)) throw bad(field, "unknown field");
    }

    private static IllegalArgumentException bad(String field, String message) {
        return new IllegalArgumentException(field + ": " + message);
    }
}
