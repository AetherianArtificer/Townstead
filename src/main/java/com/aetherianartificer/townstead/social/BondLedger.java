package com.aetherianartificer.townstead.social;

import com.google.gson.*;
import net.minecraft.nbt.CompoundTag;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Durable lifecycle records for recognized person-to-person bonds. */
public final class BondLedger {
    public record Entry(UUID id, String operationId, String kind, UUID first, String firstName,
                        UUID second, String secondName, long startDay, long endDay,
                        String formedBy, String endedBy) {
        public boolean active() { return endDay < 0; }
        public boolean involves(UUID person) { return first.equals(person) || second.equals(person); }
        public Bond forPerson(UUID person) {
            if (first.equals(person)) return new Bond(kind, second, secondName, startDay, endDay);
            if (second.equals(person)) return new Bond(kind, first, firstName, startDay, endDay);
            throw new IllegalArgumentException("person is not part of bond");
        }
    }
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private final Map<String, UUID> operations = new HashMap<>();

    public boolean form(String operationId, String kind, UUID first, String firstName, UUID second,
                        String secondName, long day, String formedBy) {
        if (operationId == null || operationId.isBlank() || kind == null || kind.isBlank()
                || first == null || second == null || first.equals(second)) return false;
        if (operations.containsKey(operationId)) return false;
        BondKind definition = BondKinds.byId(kind);
        if (definition.uniquePerPair() && entries.values().stream().anyMatch(entry -> entry.active()
                && entry.kind().equals(kind) && samePair(entry, first, second))) return false;
        if (!definition.unlimited() && active(first, kind) >= definition.maxActive()) return false;
        if (definition.symmetric() && !definition.unlimited() && active(second, kind) >= definition.maxActive()) return false;
        UUID id = UUID.nameUUIDFromBytes(("townstead:bond/" + operationId).getBytes(StandardCharsets.UTF_8));
        Entry entry = new Entry(id, operationId, kind, first, bounded(firstName), second, bounded(secondName),
                day, -1, bounded(formedBy), "");
        entries.put(id, entry); operations.put(operationId, id); return true;
    }

    public boolean end(UUID id, long day, String endedBy) {
        Entry entry = entries.get(id);
        if (entry == null || !entry.active() || day < entry.startDay()) return false;
        entries.put(id, new Entry(entry.id(), entry.operationId(), entry.kind(), entry.first(), entry.firstName(),
                entry.second(), entry.secondName(), entry.startDay(), day, entry.formedBy(), bounded(endedBy)));
        return true;
    }

    public List<Bond> bonds(UUID person) {
        return entries.values().stream().filter(entry -> entry.involves(person)).map(entry -> entry.forPerson(person)).toList();
    }
    public List<Entry> entries(UUID person) { return entries.values().stream().filter(entry -> entry.involves(person)).toList(); }
    public int size() { return entries.size(); }
    private long active(UUID person, String kind) {
        return entries.values().stream().filter(entry -> entry.active() && entry.kind().equals(kind) && entry.involves(person)).count();
    }

    public CompoundTag save() {
        CompoundTag root = new CompoundTag(); root.putInt("version", 1); root.putInt("count", entries.size());
        int i = 0;
        for (Entry entry : entries.values()) {
            JsonObject json = new JsonObject(); json.addProperty("id", entry.id().toString());
            json.addProperty("operation", entry.operationId()); json.addProperty("kind", entry.kind());
            json.addProperty("first", entry.first().toString()); json.addProperty("firstName", entry.firstName());
            json.addProperty("second", entry.second().toString()); json.addProperty("secondName", entry.secondName());
            json.addProperty("startDay", entry.startDay()); json.addProperty("endDay", entry.endDay());
            json.addProperty("formedBy", entry.formedBy()); json.addProperty("endedBy", entry.endedBy());
            root.putString("bond_" + i++, json.toString());
        }
        return root;
    }

    public static BondLedger load(CompoundTag root) {
        BondLedger ledger = new BondLedger();
        if (!root.contains("version") || root.getInt("version") != 1) return ledger;
        int count = Math.max(0, Math.min(32768, root.getInt("count")));
        for (int i = 0; i < count; i++) try {
            JsonObject json = JsonParser.parseString(root.getString("bond_" + i)).getAsJsonObject();
            Entry entry = new Entry(UUID.fromString(json.get("id").getAsString()), json.get("operation").getAsString(),
                    json.get("kind").getAsString(), UUID.fromString(json.get("first").getAsString()), json.get("firstName").getAsString(),
                    UUID.fromString(json.get("second").getAsString()), json.get("secondName").getAsString(),
                    json.get("startDay").getAsLong(), json.get("endDay").getAsLong(),
                    json.get("formedBy").getAsString(), json.get("endedBy").getAsString());
            if (!entry.first().equals(entry.second()) && !ledger.entries.containsKey(entry.id())
                    && !ledger.operations.containsKey(entry.operationId())) {
                ledger.entries.put(entry.id(), entry); ledger.operations.put(entry.operationId(), entry.id());
            }
        } catch (RuntimeException ignored) { }
        return ledger;
    }

    private static boolean samePair(Entry entry, UUID a, UUID b) {
        return entry.first().equals(a) && entry.second().equals(b) || entry.first().equals(b) && entry.second().equals(a);
    }
    private static String bounded(String value) {
        if (value == null) return ""; return value.substring(0, Math.min(256, value.length()));
    }
}
