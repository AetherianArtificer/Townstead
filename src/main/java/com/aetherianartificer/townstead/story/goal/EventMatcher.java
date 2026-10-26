package com.aetherianartificer.townstead.story.goal;

import com.aetherianartificer.townstead.api.v1.model.VillageId;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tests a posted event against a goal's {@code where}: each named field of the event record must
 * match. A value can be text or a number, {@code "teller"} or {@code "player"} (the person, or
 * their village for a village field), a list meaning any of, or {@code {"min": .., "max": ..}}.
 * A range can also name another field: {@code {"above": "tierBefore"}} or {@code {"below": ..}}.
 * On an event with a person but no village field, {@code village} is that person's village: their
 * home, else where they stand. An optional Pheno {@code condition} is also tested on the entity in
 * field {@code entity}.
 */
final class EventMatcher {
    /** {@code villageOf}: the accessor reads a person and the test is on their village. */
    private record FieldTest(Method accessor, JsonElement wanted, boolean villageOf) {}

    private final List<FieldTest> tests;
    private final @Nullable Method entity;
    private final @Nullable Condition condition;

    private EventMatcher(List<FieldTest> tests, @Nullable Method entity, @Nullable Condition condition) {
        this.tests = tests;
        this.entity = entity;
        this.condition = condition;
    }

    /** Builds a matcher, or throws with a message naming the fields the event has. */
    static EventMatcher parse(Class<?> event, @Nullable JsonObject where, @Nullable String entityField,
                              @Nullable Condition condition) {
        Map<String, Method> fields = new LinkedHashMap<>();
        for (RecordComponent component : event.getRecordComponents()) fields.put(component.getName(), component.getAccessor());
        List<FieldTest> tests = new ArrayList<>();
        if (where != null) {
            for (Map.Entry<String, JsonElement> entry : where.entrySet()) {
                Method accessor = fields.get(entry.getKey());
                boolean villageOf = false;
                if (accessor == null && entry.getKey().equals("village")) {
                    accessor = personField(event);
                    villageOf = accessor != null;
                }
                if (accessor == null) throw new IllegalArgumentException(unknown(entry.getKey(), event, fields));
                if (entry.getValue().isJsonObject()) {
                    for (String bound : List.of("above", "below")) {
                        JsonElement other = entry.getValue().getAsJsonObject().get(bound);
                        if (other != null && !fields.containsKey(other.getAsString())) {
                            throw new IllegalArgumentException(unknown(other.getAsString(), event, fields));
                        }
                    }
                }
                tests.add(new FieldTest(accessor, entry.getValue(), villageOf));
            }
        }
        Method entity = null;
        if (condition != null) {
            if (entityField == null) throw new IllegalArgumentException("a goal with an event condition needs \"entity\": the field to test it on");
            entity = fields.get(entityField);
            if (entity == null) throw new IllegalArgumentException(unknown(entityField, event, fields));
        }
        return new EventMatcher(List.copyOf(tests), entity, condition);
    }

    /** The event's first person field, such as {@code worker}, or null when it has none. */
    private static @Nullable Method personField(Class<?> event) {
        for (RecordComponent component : event.getRecordComponents()) {
            if (LivingEntity.class.isAssignableFrom(component.getType())) return component.getAccessor();
        }
        return null;
    }

    private static @Nullable VillageId villageOf(@Nullable Object person) {
        if (!(person instanceof LivingEntity living) || !(living.level() instanceof net.minecraft.server.level.ServerLevel level)) return null;
        int id = com.aetherianartificer.townstead.chronicle.emit.ChronicleEmitter.resolveVillageId(living);
        return id == com.aetherianartificer.townstead.chronicle.model.ChronicleEvent.VILLAGE_NONE
                ? null : new VillageId(level.dimension().location(), id);
    }

    boolean matches(Object event, GoalContext ctx) {
        for (FieldTest test : tests) {
            Object actual = read(test.accessor(), event);
            if (test.villageOf()) actual = villageOf(unwrap(actual));
            if (!matches(actual, test.wanted(), ctx, event)) return false;
        }
        if (condition != null && entity != null) {
            Object value = unwrap(read(entity, event));
            if (!(value instanceof LivingEntity living)) return false;
            return condition.test(new ConditionContext(living, ctx.player()));
        }
        return true;
    }

    private static boolean matches(@Nullable Object actual, JsonElement wanted, GoalContext ctx, Object event) {
        actual = unwrap(actual);
        if (actual == null) return false;
        if (wanted.isJsonArray()) {
            for (JsonElement option : wanted.getAsJsonArray()) if (matches(actual, option, ctx, event)) return true;
            return false;
        }
        if (wanted.isJsonObject()) {
            if (!(actual instanceof Number number)) return false;
            JsonObject range = wanted.getAsJsonObject();
            double n = number.doubleValue();
            Double above = range.has("above") ? numberField(event, range.get("above").getAsString()) : null;
            Double below = range.has("below") ? numberField(event, range.get("below").getAsString()) : null;
            if (range.has("above") && (above == null || n <= above)) return false;
            if (range.has("below") && (below == null || n >= below)) return false;
            return (!range.has("min") || n >= range.get("min").getAsDouble())
                    && (!range.has("max") || n <= range.get("max").getAsDouble());
        }
        if (!wanted.isJsonPrimitive()) return false;
        if (wanted.getAsJsonPrimitive().isNumber()) {
            return actual instanceof Number number && number.doubleValue() == wanted.getAsDouble();
        }
        if (wanted.getAsJsonPrimitive().isBoolean()) return actual.equals(wanted.getAsBoolean());
        String text = wanted.getAsString();
        if (text.equals("teller") || text.equals("player")) {
            boolean teller = text.equals("teller");
            UUID id = teller ? ctx.speakerId() : ctx.playerId();
            if (actual instanceof Entity e) return e.getUUID().equals(id);
            if (actual instanceof UUID uuid) return uuid.equals(id);
            if (actual instanceof VillageId village) return ctx.villageId(teller).map(village::equals).orElse(false);
            return false;
        }
        if (actual instanceof Enum<?> e) return e.name().equalsIgnoreCase(text);
        if (actual instanceof Entity e) return e.getUUID().toString().equalsIgnoreCase(text);
        return actual.toString().equalsIgnoreCase(text);
    }

    private static @Nullable Double numberField(Object event, String field) {
        for (RecordComponent component : event.getClass().getRecordComponents()) {
            if (!component.getName().equals(field)) continue;
            return unwrap(read(component.getAccessor(), event)) instanceof Number number ? number.doubleValue() : null;
        }
        return null;
    }

    private static @Nullable Object read(Method accessor, Object event) {
        try {
            return accessor.invoke(event);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static @Nullable Object unwrap(@Nullable Object value) {
        return value instanceof Optional<?> optional ? optional.orElse(null) : value;
    }

    private static String unknown(String field, Class<?> event, Map<String, Method> fields) {
        return GoalEvents.id(event) + " has no field '" + field + "' (it has: " + String.join(", ", fields.keySet()) + ")";
    }
}
