package com.aetherianartificer.townstead.work.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Small, reusable vocabulary for reading foreign recipe objects.
 *
 * <p>The names and version aliases live in data. Java only supplies stable operations: walk a
 * zero-argument method/field path and convert the resulting public object into an item, fluid,
 * quantity, list, or fluid amount. Every attempted alias is retained in the result so a moved
 * upstream field can be diagnosed without teaching this class which mod owned it.</p>
 */
public final class RecipeProjectionAccess {
    public record Accessor(List<String> aliases, Operation operation, boolean required,
                           @Nullable JsonElement fallback) {
        public Accessor {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
            operation = operation == null ? Operation.RAW : operation;
            fallback = fallback == null ? null : fallback.deepCopy();
        }
    }

    public enum Operation {
        RAW, INTEGER, NUMBER, BOOLEAN, RESOURCE_ID, ITEM_ID, FLUID_ID,
        ITEM_STACK_ID, FLUID_AMOUNT, LIST;

        static @Nullable Operation parse(String value) {
            if (value == null) return RAW;
            try {
                return valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    public record FluidValue(ResourceLocation fluid, int amount) {}

    public record Read(boolean found, @Nullable Object value, @Nullable Object rawValue,
                       @Nullable String selectedAlias,
                       List<String> failures) {
        public Read {
            failures = List.copyOf(failures);
        }
    }

    private RecipeProjectionAccess() {}

    public static Accessor parse(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            throw new IllegalArgumentException("projection accessor must not be null");
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            return new Accessor(List.of(value.getAsString()), Operation.RAW, false, null);
        }
        if (!value.isJsonObject()) throw new IllegalArgumentException("projection accessor must be an object");
        JsonObject object = value.getAsJsonObject();
        for (String key : object.keySet()) {
            if (!List.of("path", "aliases", "operation", "required", "default").contains(key)) {
                throw new IllegalArgumentException("unknown projection accessor field '" + key + "'");
            }
        }
        List<String> aliases = new ArrayList<>();
        if (object.has("path")) addAlias(object.get("path"), aliases);
        if (object.has("aliases")) {
            if (!object.get("aliases").isJsonArray()) throw new IllegalArgumentException("aliases must be an array");
            for (JsonElement alias : object.getAsJsonArray("aliases")) addAlias(alias, aliases);
        }
        if (aliases.isEmpty() && !object.has("default")) {
            throw new IllegalArgumentException("projection accessor needs path, aliases, or default");
        }
        Operation operation = Operation.parse(object.has("operation")
                ? object.get("operation").getAsString() : "raw");
        if (operation == null) throw new IllegalArgumentException("unknown projection operation");
        boolean required = object.has("required") && object.get("required").getAsBoolean();
        return new Accessor(aliases, operation, required,
                object.has("default") ? object.get("default") : null);
    }

    private static void addAlias(JsonElement value, List<String> out) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank()) {
            throw new IllegalArgumentException("projection paths must be non-empty strings");
        }
        out.add(value.getAsString());
    }

    public static Read read(@Nullable Object root, Accessor accessor) {
        List<String> failures = new ArrayList<>();
        for (String alias : accessor.aliases()) {
            Object current = root;
            boolean failed = false;
            for (String segment : alias.split("\\.")) {
                if (segment.isBlank()) {
                    failed = true;
                    failures.add(alias + ": empty path segment");
                    break;
                }
                Step step = step(current, segment);
                if (!step.found()) {
                    failed = true;
                    failures.add(alias + ": " + step.failure());
                    break;
                }
                current = step.value();
            }
            if (failed) continue;
            Object converted = convert(current, accessor.operation());
            if (converted != null) return new Read(true, converted, current, alias, failures);
            failures.add(alias + ": value cannot be converted with "
                    + accessor.operation().name().toLowerCase(Locale.ROOT));
        }
        Object fallback = jsonValue(accessor.fallback());
        return fallback == null
                ? new Read(false, null, null, null, failures)
                : new Read(true, fallback, fallback, "<default>", failures);
    }

    private record Step(boolean found, @Nullable Object value, String failure) {}

    private static Step step(@Nullable Object owner, String name) {
        if (owner == null) return new Step(false, null, "parent value is null");
        if (owner instanceof List<?> list && integer(name) != null) {
            int index = integer(name);
            return index >= 0 && index < list.size()
                    ? new Step(true, list.get(index), "")
                    : new Step(false, null, "list index out of bounds");
        }
        for (String methodName : methodAliases(name)) {
            try {
                Method method = owner.getClass().getMethod(methodName);
                if (method.getParameterCount() != 0) continue;
                method.setAccessible(true);
                return new Step(true, method.invoke(owner), "");
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable failure) {
                return new Step(false, null, "method " + methodName + " failed: "
                        + failure.getClass().getSimpleName());
            }
        }
        for (Class<?> type = owner.getClass(); type != null && type != Object.class;
             type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return new Step(true, field.get(owner), "");
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable failure) {
                return new Step(false, null, "field " + name + " failed: "
                        + failure.getClass().getSimpleName());
            }
        }
        return new Step(false, null, "no zero-argument method or field named " + name);
    }

    private static List<String> methodAliases(String name) {
        if (name.startsWith("get") || name.startsWith("is")) return List.of(name);
        String title = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        return List.of(name, "get" + title, "is" + title);
    }

    private static @Nullable Integer integer(String value) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static @Nullable Object convert(@Nullable Object value, Operation operation) {
        if (value == null) return null;
        return switch (operation) {
            case RAW -> value;
            case INTEGER -> value instanceof Number number ? number.intValue() : null;
            case NUMBER -> value instanceof Number number ? number.doubleValue() : null;
            case BOOLEAN -> value instanceof Boolean bool ? bool : null;
            case RESOURCE_ID -> resourceId(value);
            case ITEM_ID -> itemId(value);
            case FLUID_ID -> fluidId(value);
            case ITEM_STACK_ID -> value instanceof ItemStack stack && !stack.isEmpty()
                    ? BuiltInRegistries.ITEM.getKey(stack.getItem()) : null;
            case FLUID_AMOUNT -> fluidAmount(value);
            case LIST -> List.copyOf(elements(value));
        };
    }

    private static @Nullable ResourceLocation resourceId(Object value) {
        if (value instanceof ResourceLocation id) return id;
        return value instanceof String raw ? ResourceLocation.tryParse(raw) : null;
    }

    private static @Nullable ResourceLocation itemId(Object value) {
        if (value instanceof Item item) return BuiltInRegistries.ITEM.getKey(item);
        if (value instanceof ItemStack stack && !stack.isEmpty()) {
            return BuiltInRegistries.ITEM.getKey(stack.getItem());
        }
        return resourceId(value);
    }

    private static @Nullable ResourceLocation fluidId(Object value) {
        if (value instanceof Fluid fluid) return BuiltInRegistries.FLUID.getKey(fluid);
        FluidValue amount = fluidAmount(value);
        return amount == null ? resourceId(value) : amount.fluid();
    }

    private static @Nullable FluidValue fluidAmount(Object value) {
        if (value instanceof FluidValue amount) return amount;
        try {
            Object fluid = value.getClass().getMethod("getFluid").invoke(value);
            Object amount = value.getClass().getMethod("getAmount").invoke(value);
            if (fluid instanceof Fluid actual && amount instanceof Number number) {
                ResourceLocation id = BuiltInRegistries.FLUID.getKey(actual);
                return id == null ? null : new FluidValue(id, number.intValue());
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** Collections, streams and arrays are normalized so compat logic never repeats this glue. */
    public static List<Object> elements(@Nullable Object value) {
        List<Object> out = new ArrayList<>();
        if (value == null) return out;
        if (value instanceof Stream<?> stream) stream.forEach(out::add);
        else if (value instanceof Collection<?> collection) out.addAll(collection);
        else if (value instanceof Iterable<?> iterable) iterable.forEach(out::add);
        else if (value.getClass().isArray()) {
            for (int index = 0; index < Array.getLength(value); index++) out.add(Array.get(value, index));
        } else out.add(value);
        return out;
    }

    private static @Nullable Object jsonValue(@Nullable JsonElement value) {
        if (value == null || value.isJsonNull()) return null;
        if (value.isJsonPrimitive()) {
            if (value.getAsJsonPrimitive().isBoolean()) return value.getAsBoolean();
            if (value.getAsJsonPrimitive().isNumber()) return value.getAsDouble();
            return value.getAsString();
        }
        if (value.isJsonArray()) {
            List<Object> result = new ArrayList<>();
            for (JsonElement element : (JsonArray) value) result.add(jsonValue(element));
            return List.copyOf(result);
        }
        return value.deepCopy();
    }
}
