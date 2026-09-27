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
 * An optional Pheno {@code condition} is also tested on the entity in field {@code entity}.
 */
final class EventMatcher {
    private record FieldTest(Method accessor, JsonElement wanted) {}

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
                if (accessor == null) throw new IllegalArgumentException(unknown(entry.getKey(), event, fields));
                tests.add(new FieldTest(accessor, entry.getValue()));
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

    boolean matches(Object event, GoalContext ctx) {
        for (FieldTest test : tests) {
            if (!matches(read(test.accessor(), event), test.wanted(), ctx)) return false;
        }
        if (condition != null && entity != null) {
            Object value = unwrap(read(entity, event));
            if (!(value instanceof LivingEntity living)) return false;
            return condition.test(new ConditionContext(living, ctx.player()));
        }
        return true;
    }

    private static boolean matches(@Nullable Object actual, JsonElement wanted, GoalContext ctx) {
        actual = unwrap(actual);
        if (actual == null) return false;
        if (wanted.isJsonArray()) {
            for (JsonElement option : wanted.getAsJsonArray()) if (matches(actual, option, ctx)) return true;
            return false;
        }
        if (wanted.isJsonObject()) {
            if (!(actual instanceof Number number)) return false;
            JsonObject range = wanted.getAsJsonObject();
            double n = number.doubleValue();
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
