package com.aetherianartificer.townstead.social;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Deterministic JSON scenario using the production relationship transition and read model. */
public record RelationshipScenario(String id, Map<String, UUID> people, List<Change> changes, List<Check> checks) {
    public static final String SCHEMA = "townstead:relationship_scenario/v1";
    public record Change(String operation, String from, String toward, String quality, float amount,
                         long day, int halfLifeDays, String source, boolean expectApplied) {}
    public record Check(String from, String toward, String quality, long day, double minimum, double maximum) {}
    public record Result(RelationshipLedger ledger, List<String> trace, List<String> failures) {
        public boolean passed() { return failures.isEmpty(); }
    }

    public Result run() {
        RelationshipLedger ledger = new RelationshipLedger();
        List<String> trace = new ArrayList<>(), failures = new ArrayList<>();
        for (Change change : changes) {
            boolean applied = ledger.apply(person(change.from), person(change.toward),
                    new RelationshipLedger.Contribution(change.operation, change.quality, change.amount,
                            change.day, change.halfLifeDays, change.source));
            trace.add(change.operation + " " + change.from + " -> " + change.toward + " "
                    + change.quality + " " + signed(change.amount) + " applied=" + applied);
            if (applied != change.expectApplied) failures.add(change.operation + ": expected applied=" + change.expectApplied);
        }
        for (Check check : checks) {
            double value = ledger.value(person(check.from), person(check.toward), check.quality, check.day);
            trace.add("check day=" + check.day + " " + check.from + " -> " + check.toward + " " + check.quality + "=" + value);
            if (!Double.isFinite(value) || value < check.minimum || value > check.maximum)
                failures.add(check.from + " -> " + check.toward + " " + check.quality + " expected "
                        + check.minimum + ".." + check.maximum + ", got " + value);
        }
        return new Result(ledger, List.copyOf(trace), List.copyOf(failures));
    }

    public static RelationshipScenario parse(JsonObject json) {
        only(json, "schema", "id", "people", "changes", "checks");
        String schema = GsonHelper.getAsString(json, "schema", "");
        if (!SCHEMA.equals(schema)) throw bad("schema", "must be " + SCHEMA);
        String id = GsonHelper.getAsString(json, "id");
        Map<String, UUID> people = new LinkedHashMap<>();
        JsonElement peopleJson = json.get("people");
        if (peopleJson == null) throw bad("people", "is required");
        if (peopleJson.isJsonArray()) for (JsonElement element : peopleJson.getAsJsonArray()) {
            String name = element.getAsString(); people.put(name, stableId(id, name));
        } else for (var entry : peopleJson.getAsJsonObject().entrySet())
            people.put(entry.getKey(), UUID.fromString(entry.getValue().getAsString()));
        if (people.size() < 2) throw bad("people", "requires at least two people");

        List<Change> changes = new ArrayList<>();
        JsonArray changesJson = GsonHelper.getAsJsonArray(json, "changes");
        for (int i = 0; i < changesJson.size(); i++) {
            JsonObject value = changesJson.get(i).getAsJsonObject();
            only(value, "operation", "from", "toward", "quality", "amount", "day", "half_life_days", "source", "expect_applied");
            String from = person(value, "from", people), toward = person(value, "toward", people);
            String quality = quality(value);
            float amount = GsonHelper.getAsFloat(value, "amount");
            if (!Float.isFinite(amount) || amount == 0) throw bad("changes[" + i + "].amount", "must be finite and nonzero");
            int halfLife = GsonHelper.getAsInt(value, "half_life_days", RelationshipQualities.byId(quality).defaultHalfLifeDays());
            if (halfLife < 0) throw bad("changes[" + i + "].half_life_days", "must be >= 0");
            changes.add(new Change(GsonHelper.getAsString(value, "operation"), from, toward, quality, amount,
                    GsonHelper.getAsLong(value, "day", 0), halfLife, GsonHelper.getAsString(value, "source", id),
                    GsonHelper.getAsBoolean(value, "expect_applied", true)));
        }

        List<Check> checks = new ArrayList<>();
        JsonArray checksJson = GsonHelper.getAsJsonArray(json, "checks");
        for (int i = 0; i < checksJson.size(); i++) {
            JsonObject value = checksJson.get(i).getAsJsonObject();
            only(value, "from", "toward", "quality", "day", "min", "max");
            String from = person(value, "from", people), toward = person(value, "toward", people), quality = quality(value);
            double min = GsonHelper.getAsDouble(value, "min", Double.NEGATIVE_INFINITY);
            double max = GsonHelper.getAsDouble(value, "max", Double.POSITIVE_INFINITY);
            if (min > max) throw bad("checks[" + i + "]", "min must be <= max");
            checks.add(new Check(from, toward, quality, GsonHelper.getAsLong(value, "day", 0), min, max));
        }
        if (checks.isEmpty()) throw bad("checks", "requires at least one assertion");
        return new RelationshipScenario(id, Map.copyOf(people), List.copyOf(changes), List.copyOf(checks));
    }

    private UUID person(String name) {
        UUID value = people.get(name); if (value == null) throw bad("person", "unknown person " + name); return value;
    }
    private static String person(JsonObject json, String field, Map<String, UUID> people) {
        String value = GsonHelper.getAsString(json, field);
        if (!people.containsKey(value)) throw bad(field, "unknown person " + value); return value;
    }
    private static String quality(JsonObject json) {
        String value = GsonHelper.getAsString(json, "quality");
        if (ResourceLocation.tryParse(value) == null) throw bad("quality", "expected resource id"); return value;
    }
    private static UUID stableId(String scenario, String person) {
        return UUID.nameUUIDFromBytes((scenario + "/" + person).getBytes(StandardCharsets.UTF_8));
    }
    private static String signed(float value) { return value > 0 ? "+" + value : Float.toString(value); }
    private static void only(JsonObject json, String... fields) {
        Set<String> allowed = Set.of(fields);
        for (String field : json.keySet()) if (!allowed.contains(field)) throw bad(field, "unknown field");
    }
    private static IllegalArgumentException bad(String field, String message) {
        return new IllegalArgumentException(field + ": " + message);
    }
}
