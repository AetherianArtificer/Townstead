package com.aetherianartificer.townstead.food;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** The optional transaction envelope embedded by {@code townstead:consumable/v2}. */
public record ConsumptionPolicy(Mode mode, Accounting accounting, Set<Consumer> consumers,
                                int servings, Remainder remainder,
                                EffectAdmission effectAdmission) {
    private static final Set<String> FIELDS = Set.of(
            "mode", "accounting", "consumers", "servings", "remainder", "effect_admission");
    private static final Set<String> REMAINDER_FIELDS = Set.of("mode", "item", "destination");
    private static final Set<String> ADMISSION_FIELDS = Set.of("default", "allow", "deny");

    public enum Mode { OBSERVE_NATIVE, REPLACE_WITH_PHENO }
    public enum Accounting { CONSUME_ONE, NATIVE_RESULT }
    public enum Consumer { PLAYER, VILLAGER, OTHER_LIVING }
    public enum RemainderMode { NATIVE, ITEM, NONE }
    public enum RemainderDestination { SOURCE, STORAGE, HOLDER, DROP }
    public enum Decision { ALLOW, DENY }
    public enum EffectClass {
        STATUS, ATTRIBUTE, TELEPORT, COMBAT, WORLD_MUTATION, LOOT_ECONOMY,
        EXPERIENCE, PROJECTILE, CLEANSING, PLAYER_ONLY, AURA
    }

    public record Remainder(RemainderMode mode, @Nullable ResourceLocation item,
                            RemainderDestination destination) {}

    public record EffectAdmission(Decision fallback, Set<EffectClass> allow,
                                  Set<EffectClass> deny) {
        public EffectAdmission {
            allow = Set.copyOf(allow);
            deny = Set.copyOf(deny);
        }

        public Decision decision(EffectClass effectClass) {
            if (deny.contains(effectClass)) return Decision.DENY;
            if (allow.contains(effectClass)) return Decision.ALLOW;
            return fallback;
        }

        public boolean unrestricted() {
            for (EffectClass effectClass : EffectClass.values()) {
                if (decision(effectClass) != Decision.ALLOW) return false;
            }
            return true;
        }
    }

    public ConsumptionPolicy {
        consumers = Set.copyOf(consumers);
        if (servings < 1) throw new IllegalArgumentException("servings must be at least one");
    }

    public boolean permits(Consumer consumer) { return consumers.contains(consumer); }

    public static ConsumptionPolicy parse(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("'transaction' must be an object");
        }
        JsonObject json = element.getAsJsonObject();
        rejectUnknown(json, FIELDS, "transaction");
        Mode mode = parseEnum(json, "mode", Mode.class, Mode.OBSERVE_NATIVE);
        Accounting accounting = parseEnum(
                json, "accounting", Accounting.class, Accounting.CONSUME_ONE);
        Set<Consumer> consumers = json.has("consumers")
                ? parseEnumSet(json.get("consumers"), Consumer.class, "consumers")
                : Set.copyOf(EnumSet.allOf(Consumer.class));
        int servings = 1;
        if (json.has("servings")) {
            JsonElement value = json.get("servings");
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                    || value.getAsDouble() != value.getAsInt()) {
                throw new IllegalArgumentException("'servings' must be an integer");
            }
            servings = value.getAsInt();
        }
        if (servings < 1) throw new IllegalArgumentException("'servings' must be at least 1");
        return new ConsumptionPolicy(mode, accounting, consumers, servings,
                parseRemainder(json.get("remainder")),
                parseAdmission(json.get("effect_admission")));
    }

    private static Remainder parseRemainder(@Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return new Remainder(RemainderMode.NATIVE, null, RemainderDestination.SOURCE);
        }
        if (!element.isJsonObject()) throw new IllegalArgumentException("'remainder' must be an object");
        JsonObject json = element.getAsJsonObject();
        rejectUnknown(json, REMAINDER_FIELDS, "remainder");
        RemainderMode mode = parseEnum(json, "mode", RemainderMode.class, RemainderMode.NATIVE);
        ResourceLocation item = json.has("item")
                ? ResourceLocation.tryParse(json.get("item").getAsString()) : null;
        if (json.has("item") && item == null) throw new IllegalArgumentException("invalid remainder item");
        if (mode == RemainderMode.ITEM && item == null) {
            throw new IllegalArgumentException("remainder mode 'item' requires 'item'");
        }
        if (mode != RemainderMode.ITEM && item != null) {
            throw new IllegalArgumentException("remainder 'item' is only valid with mode 'item'");
        }
        return new Remainder(mode, item, parseEnum(json, "destination",
                RemainderDestination.class, RemainderDestination.SOURCE));
    }

    private static EffectAdmission parseAdmission(@Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return new EffectAdmission(Decision.ALLOW, Set.of(), Set.of());
        }
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("'effect_admission' must be an object");
        }
        JsonObject json = element.getAsJsonObject();
        rejectUnknown(json, ADMISSION_FIELDS, "effect admission");
        Decision fallback = parseEnum(json, "default", Decision.class, Decision.ALLOW);
        Set<EffectClass> allow = json.has("allow")
                ? parseEnumSet(json.get("allow"), EffectClass.class, "allow") : Set.of();
        Set<EffectClass> deny = json.has("deny")
                ? parseEnumSet(json.get("deny"), EffectClass.class, "deny") : Set.of();
        EnumSet<EffectClass> overlap = EnumSet.noneOf(EffectClass.class);
        overlap.addAll(allow);
        overlap.retainAll(deny);
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException(
                    "effect classes cannot be both allowed and denied: " + overlap);
        }
        return new EffectAdmission(fallback, allow, deny);
    }

    private static <E extends Enum<E>> Set<E> parseEnumSet(JsonElement element, Class<E> type,
                                                            String field) {
        if (!element.isJsonArray() || element.getAsJsonArray().isEmpty()) {
            throw new IllegalArgumentException("'" + field + "' must be a non-empty array");
        }
        EnumSet<E> out = EnumSet.noneOf(type);
        for (JsonElement value : element.getAsJsonArray()) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("'" + field + "' entries must be strings");
            }
            out.add(enumValue(value.getAsString(), type, field));
        }
        return Set.copyOf(out);
    }

    private static <E extends Enum<E>> E parseEnum(JsonObject json, String field, Class<E> type,
                                                    E fallback) {
        if (!json.has(field)) return fallback;
        if (!json.get(field).isJsonPrimitive() || !json.getAsJsonPrimitive(field).isString()) {
            throw new IllegalArgumentException("'" + field + "' must be a string");
        }
        return enumValue(json.get(field).getAsString(), type, field);
    }

    private static <E extends Enum<E>> E enumValue(String raw, Class<E> type, String field) {
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unknown '" + field + "' value '" + raw + "'");
        }
    }

    private static void rejectUnknown(JsonObject json, Set<String> allowed, String context) {
        for (String field : json.keySet()) {
            if (!allowed.contains(field)) {
                throw new IllegalArgumentException("unknown " + context + " field '" + field + "'");
            }
        }
    }
}
