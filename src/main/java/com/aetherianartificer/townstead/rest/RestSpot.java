package com.aetherianartificer.townstead.rest;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Something to sleep in: a bed, a coffin, a bedroll. {@code home} makes it a home MCA counts and
 * villagers claim; {@code who} says who may use it, and {@code only} binds those people to spots
 * like it alone (a vampire sleeps in nothing but a coffin). {@code time}, {@code sets_spawn} and
 * {@code skips_night} shape how players sleep in it; {@code sleep} how a sleeper lies.
 */
public record RestSpot(ResourceLocation id, List<BlockMatch> blocks, boolean home, @Nullable Condition who,
                       boolean only, int prefer, Time time, boolean setsSpawn, boolean skipsNight,
                       String pose, Map<String, String> hiddenWhen,
                       @Nullable Action onSleep, @Nullable Action onWake) {
    public static final String SCHEMA = "townstead:rest_spot/v1";

    public enum Time { NIGHT, DAY, ANY }

    /** A block id or {@code #tag}, with block-state values it must have. */
    public record BlockMatch(ResourceLocation id, boolean tag, Map<String, String> state) {
        public boolean matches(BlockState blockState) {
            if (tag ? !blockState.is(TagKey.create(Registries.BLOCK, id))
                    : !id.equals(BuiltInRegistries.BLOCK.getKey(blockState.getBlock()))) return false;
            for (Map.Entry<String, String> wanted : state.entrySet()) {
                if (!wanted.getValue().equals(value(blockState, wanted.getKey()))) return false;
            }
            return true;
        }

        public boolean matchesBlock(Block block) {
            for (BlockState candidate : block.getStateDefinition().getPossibleStates()) {
                if (matches(candidate)) return true;
            }
            return false;
        }
    }

    public boolean matches(BlockState state) {
        for (BlockMatch block : blocks) if (block.matches(state)) return true;
        return false;
    }

    public boolean matchesBlock(Block block) {
        for (BlockMatch match : blocks) if (match.matchesBlock(block)) return true;
        return false;
    }

    /** Whether {@code entity} may use this spot. */
    public boolean admits(LivingEntity entity) {
        return who == null || who.test(new ConditionContext(entity));
    }

    /** Whether a sleeper here is hidden, as by a closed coffin lid. */
    public boolean hides(BlockState state) {
        return hides(hiddenWhen, state);
    }

    public static boolean hides(Map<String, String> hiddenWhen, BlockState state) {
        if (hiddenWhen.isEmpty()) return false;
        for (Map.Entry<String, String> wanted : hiddenWhen.entrySet()) {
            if (!wanted.getValue().equals(value(state, wanted.getKey()))) return false;
        }
        return true;
    }

    static @Nullable String value(BlockState state, String name) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(name)) return serialized(property, state);
        }
        return null;
    }

    private static <T extends Comparable<T>> String serialized(Property<T> property, BlockState state) {
        return property.getName(state.getValue(property));
    }

    static RestSpot parse(ResourceLocation id, JsonObject json) {
        TownsteadSchema.validateRequired(json, SCHEMA);
        List<BlockMatch> blocks = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "blocks")) {
            String raw;
            Map<String, String> state = new LinkedHashMap<>();
            if (element.isJsonObject()) {
                JsonObject entry = element.getAsJsonObject();
                raw = GsonHelper.getAsString(entry, "id");
                if (entry.has("state")) {
                    for (Map.Entry<String, JsonElement> value : GsonHelper.getAsJsonObject(entry, "state").entrySet()) {
                        state.put(value.getKey(), value.getValue().getAsString());
                    }
                }
            } else {
                raw = element.getAsString();
            }
            boolean tag = raw.startsWith("#");
            ResourceLocation blockId = DataPackLang.parseId(tag ? raw.substring(1) : raw);
            if (blockId == null) throw new IllegalArgumentException("'" + raw + "' is not a block id or tag");
            blocks.add(new BlockMatch(blockId, tag, Map.copyOf(state)));
        }
        if (blocks.isEmpty()) throw new IllegalArgumentException("'blocks' is empty");
        Condition who = json.has("who") ? Conditions.parse(json.get("who")) : null;
        if (json.has("who") && who == null) throw new IllegalArgumentException("'who' is not a valid condition");
        Time time;
        try {
            time = Time.valueOf(GsonHelper.getAsString(json, "time", "night").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("'time' must be night, day or any");
        }
        JsonObject sleep = GsonHelper.getAsJsonObject(json, "sleep", new JsonObject());
        Map<String, String> hidden = new LinkedHashMap<>();
        if (sleep.has("hidden_when")) {
            for (Map.Entry<String, JsonElement> value : GsonHelper.getAsJsonObject(sleep, "hidden_when").entrySet()) {
                hidden.put(value.getKey(), value.getValue().getAsString());
            }
        }
        return new RestSpot(id, List.copyOf(blocks), GsonHelper.getAsBoolean(json, "home", true), who,
                GsonHelper.getAsBoolean(json, "only", false), GsonHelper.getAsInt(json, "prefer", 0), time,
                GsonHelper.getAsBoolean(json, "sets_spawn", true), GsonHelper.getAsBoolean(json, "skips_night", true),
                GsonHelper.getAsString(sleep, "pose", "lying"), Map.copyOf(hidden),
                json.has("on_sleep") ? Actions.parse(json.get("on_sleep")) : null,
                json.has("on_wake") ? Actions.parse(json.get("on_wake")) : null);
    }
}
