package com.aetherianartificer.townstead.social;

import net.minecraft.nbt.CompoundTag;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.*;

/**
 * Durable, directional relationship contributions. Values are derived, so their causes can be
 * explained, allowed to decay independently, or corrected without rewriting unrelated history.
 */
public final class RelationshipLedger {
    public static final int MAX_TARGETS_PER_PERSON = 128;
    public static final int MAX_CONTRIBUTIONS_PER_PAIR = 96;

    public record Contribution(String operationId, String quality, float amount, long day,
                               int halfLifeDays, String source) {
        public Contribution {
            Objects.requireNonNull(operationId); Objects.requireNonNull(quality); Objects.requireNonNull(source);
            if (operationId.isBlank() || quality.isBlank()) throw new IllegalArgumentException("operation and quality ids are required");
            if (!Float.isFinite(amount)) throw new IllegalArgumentException("amount must be finite");
            if (halfLifeDays < 0) throw new IllegalArgumentException("halfLifeDays must be >= 0");
        }
        String identity() { return operationId + "\u0000" + quality; }
        double valueAt(long today) {
            if (halfLifeDays == 0 || today <= day) return amount;
            return amount * Math.pow(0.5, (today - day) / (double) halfLifeDays);
        }
    }

    public record View(UUID from, UUID toward, long today, Map<String, Double> qualities,
                       List<Contribution> contributions) {
        public double quality(String id) { return qualities.getOrDefault(id, (double) RelationshipQualities.byId(id).neutral()); }
    }

    private record Pair(UUID from, UUID toward) {}
    private final Map<Pair, LinkedHashMap<String, Contribution>> byPair = new LinkedHashMap<>();

    /** Returns false for an already-applied operation/quality pair. */
    public boolean apply(UUID from, UUID toward, Contribution contribution) {
        if (from == null || toward == null || from.equals(toward) || contribution.amount() == 0) return false;
        Pair pair = new Pair(from, toward);
        LinkedHashMap<String, Contribution> values = byPair.computeIfAbsent(pair, ignored -> new LinkedHashMap<>());
        if (values.containsKey(contribution.identity())) return false;
        values.put(contribution.identity(), contribution);
        trimContributions(values, contribution.day());
        trimTargets(from);
        return true;
    }

    public double value(UUID from, UUID toward, String quality, long today) {
        RelationshipQuality definition = RelationshipQualities.byId(quality);
        LinkedHashMap<String, Contribution> values = byPair.get(new Pair(from, toward));
        if (values == null) return definition.neutral();
        double sum = definition.neutral();
        for (Contribution contribution : values.values()) if (quality.equals(contribution.quality()))
            sum += contribution.valueAt(today);
        return definition.clamp(sum);
    }

    public View view(UUID from, UUID toward, long today) {
        LinkedHashMap<String, Contribution> values = byPair.get(new Pair(from, toward));
        if (values == null) return new View(from, toward, today, Map.of(), List.of());
        Set<String> ids = new LinkedHashSet<>();
        values.values().forEach(value -> ids.add(value.quality()));
        Map<String, Double> totals = new LinkedHashMap<>();
        ids.forEach(id -> totals.put(id, value(from, toward, id, today)));
        return new View(from, toward, today, Map.copyOf(totals), List.copyOf(values.values()));
    }

    public int contributionCount(UUID from, UUID toward) {
        Map<String, Contribution> values = byPair.get(new Pair(from, toward));
        return values == null ? 0 : values.size();
    }
    public boolean hasQuality(UUID from, UUID toward, String quality) {
        Map<String, Contribution> values = byPair.get(new Pair(from, toward));
        return values != null && values.values().stream().anyMatch(c -> quality.equals(c.quality()));
    }

    public int contributionCount() { return byPair.values().stream().mapToInt(Map::size).sum(); }

    public void prune(long today) {
        byPair.values().forEach(values -> values.entrySet().removeIf(entry -> {
            Contribution c = entry.getValue();
            return c.halfLifeDays() > 0 && Math.abs(c.valueAt(today)) < RelationshipQualities.byId(c.quality()).pruneBelow();
        }));
        byPair.values().removeIf(Map::isEmpty);
    }

    public CompoundTag save() {
        CompoundTag root = new CompoundTag(); root.putInt("version", 1);
        int[] index = {0};
        byPair.forEach((pair, values) -> {
            JsonObject p = new JsonObject(); p.addProperty("from", pair.from().toString()); p.addProperty("toward", pair.toward().toString());
            JsonArray entries = new JsonArray();
            values.values().forEach(c -> {
                JsonObject e = new JsonObject(); e.addProperty("operation", c.operationId()); e.addProperty("quality", c.quality());
                e.addProperty("amount", c.amount()); e.addProperty("day", c.day()); e.addProperty("halfLife", c.halfLifeDays());
                if (!c.source().isBlank()) e.addProperty("source", c.source()); entries.add(e);
            });
            p.add("entries", entries); root.putString("pair_" + index[0]++, p.toString());
        });
        root.putInt("count", index[0]); return root;
    }

    public static RelationshipLedger load(CompoundTag root) {
        RelationshipLedger ledger = new RelationshipLedger();
        if (!root.contains("version") || root.getInt("version") != 1) return ledger;
        int count = Math.max(0, Math.min(16384, root.getInt("count")));
        for (int i = 0; i < count; i++) {
            try {
                JsonObject p = JsonParser.parseString(root.getString("pair_" + i)).getAsJsonObject();
                UUID from = UUID.fromString(p.get("from").getAsString()), toward = UUID.fromString(p.get("toward").getAsString());
                JsonArray entries = p.getAsJsonArray("entries");
                for (int j = 0; j < entries.size(); j++) {
                    JsonObject e = entries.get(j).getAsJsonObject();
                    ledger.apply(from, toward, new Contribution(e.get("operation").getAsString(), e.get("quality").getAsString(),
                            e.get("amount").getAsFloat(), e.get("day").getAsLong(), Math.max(0, e.get("halfLife").getAsInt()),
                            e.has("source") ? e.get("source").getAsString() : ""));
                }
            }
                catch (RuntimeException ignored) { }
        }
        return ledger;
    }

    private static void trimContributions(LinkedHashMap<String, Contribution> values, long today) {
        while (values.size() > MAX_CONTRIBUTIONS_PER_PAIR) {
            String weakest = values.entrySet().stream().min(Comparator
                    .comparingDouble((Map.Entry<String, Contribution> e) -> Math.abs(e.getValue().valueAt(today)))
                    .thenComparingLong(e -> e.getValue().day())).orElseThrow().getKey();
            values.remove(weakest);
        }
    }
    private void trimTargets(UUID from) {
        while (byPair.keySet().stream().filter(pair -> pair.from().equals(from)).count() > MAX_TARGETS_PER_PERSON) {
            Pair weakest = byPair.entrySet().stream().filter(e -> e.getKey().from().equals(from))
                    .min(Comparator.comparingLong(e -> e.getValue().values().stream().mapToLong(Contribution::day).max().orElse(Long.MIN_VALUE)))
                    .orElseThrow().getKey();
            byPair.remove(weakest);
        }
    }
}
