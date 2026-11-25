package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.pheno.condition.block.BlockCondition;
import com.aetherianartificer.townstead.pheno.condition.block.BlockConditions;
import com.aetherianartificer.townstead.pheno.seek.SeekPlaces;
import com.aetherianartificer.townstead.pheno.seek.Seeks;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Sends the focus, if it is a mob that can walk, to the nearest creature or block that passes a condition, and runs an
 * action when it gets there. With {@code "pick":"each"} it is a round: it visits each one in turn, up to {@code limit},
 * passing over anyone it visited in the last {@code revisit} ticks.
 *
 * <p>JSON: {@code { "type":"pheno:seek", "id":"my_pack:rounds",
 * "find":{ "entity":{ "type":"my_mod:unwell" }, "within":{ "place":"my_mod:village", "otherwise":{ "radius":32 } } },
 * "pick":"each", "limit":"all",
 * "when":["idle","meet"], "speed":0.6, "reach":2, "give_up":600, "revisit":24000, "lasts":2400,
 * "on_arrival":{ ... }, "target":"found", "on_give_up":{ ... } }}</p>
 *
 * <ul>
 *   <li>{@code find}: {@code entity} (a condition tested on each creature) or {@code block} (a block condition), and
 *       {@code within}, the place to search: {@code {"radius":32}} around the seeker, {@code "of":"origin"} around who
 *       started it, {@code "focus"} the action's block, {@code "job_site"} or {@code "home"}; or a place a mod registers,
 *       such as {@code {"place":"townstead:village"}}, with {@code otherwise} to search when there is no such place. A
 *       bare {@code radius} still works. Blocks are searched within 16 of each region's middle.</li>
 *   <li>{@code limit}: how many a round visits, a number or {@code "all"}. A far target is walked to in stages.</li>
 *   <li>{@code when}: the schedule activities it may run in, by id ({@code work}, {@code idle}, {@code meet},
 *       {@code rest}, a modded one by full id), or {@code any}. A mob with no schedule is {@code idle} while it has
 *       nothing to fight. Default {@code any}.</li>
 *   <li>{@code target}: who the arrival action lands on, {@code found} (the default; the seeker is the origin and the
 *       other) or {@code self} (the seeker, with what it found as the other). At a block, the seeker, with the block as
 *       its focus.</li>
 *   <li>{@code give_up}: ticks without getting any closer to a target before passing it over; {@code lasts}: ticks
 *       the whole seek may take. Time waiting for its activity does not count against a target.</li>
 *   <li>{@code config/pheno/seeks.json} can change any seek by its {@code id} (see {@code SeekOverrides}).</li>
 * </ul>
 */
public final class SeekActionType implements ActionType {

    public static final String KEY = "pheno:seek";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        ResourceLocation id = json.has("id") ? DataPackLang.parseId(GsonHelper.getAsString(json, "id", "")) : null;
        if (json.has("id") && id == null) return null;
        if (id == null) id = ResourceLocation.tryParse("pheno:seek/" + Integer.toHexString(json.toString().hashCode()));
        Seeks.Order order = order(json, id);
        if (order == null) return null;
        return ctx -> {
            if (ctx.entity() != null && !ctx.level().isClientSide()) Seeks.start(ctx.entity(), order, ctx.origin(), ctx.focusBlock());
        };
    }

    /** Reads a seek's JSON into its order; null if it is wrong. Overrides from the config are read the same way. */
    public static Seeks.Order order(JsonObject json, ResourceLocation id) {
        if (!json.has("find") || !json.get("find").isJsonObject()) return null;
        JsonObject find = json.getAsJsonObject("find");
        Condition entities = find.has("entity") ? Conditions.parse(find.get("entity")) : null;
        BlockCondition blocks = find.has("block") ? BlockConditions.parse(find.get("block")) : null;
        if ((find.has("entity") && entities == null) || (find.has("block") && blocks == null)) return null;
        if (entities == null && blocks == null) return null;
        JsonObject withinJson;
        if (find.has("within") && find.get("within").isJsonObject()) withinJson = find.getAsJsonObject("within");
        else {
            withinJson = new JsonObject();
            withinJson.addProperty("radius", GsonHelper.getAsInt(find, "radius", 16));
        }
        Seeks.Within within = within(withinJson, 0);
        if (within == null) return null;
        boolean each = "each".equalsIgnoreCase(GsonHelper.getAsString(json, "pick", "nearest"));
        int limit;
        if (json.has("limit") && json.get("limit").isJsonPrimitive() && json.get("limit").getAsJsonPrimitive().isString()) {
            if (!"all".equalsIgnoreCase(json.get("limit").getAsString())) return null;
            limit = Integer.MAX_VALUE;
        } else {
            limit = Math.max(1, GsonHelper.getAsInt(json, "limit", each ? 8 : 1));
        }
        Set<String> when = new LinkedHashSet<>();
        if (json.has("when")) {
            JsonElement raw = json.get("when");
            if (raw.isJsonArray()) for (JsonElement e : raw.getAsJsonArray()) when.add(e.getAsString().toLowerCase(Locale.ROOT));
            else when.add(raw.getAsString().toLowerCase(Locale.ROOT));
        }
        if (when.isEmpty()) when.add("any");
        double speed = Mth.clamp(GsonHelper.getAsDouble(json, "speed", 0.6), 0.1, 2.0);
        double reach = Mth.clamp(GsonHelper.getAsDouble(json, "reach", 2.0), 0.5, 8.0);
        int giveUp = Math.max(20, GsonHelper.getAsInt(json, "give_up", 600));
        int revisit = Math.max(0, GsonHelper.getAsInt(json, "revisit", each ? 24000 : 0));
        int lasts = Mth.clamp(GsonHelper.getAsInt(json, "lasts", 2400), 20, 72000);
        Action onArrival = json.has("on_arrival") ? Actions.parse(json.get("on_arrival")) : null;
        if (json.has("on_arrival") && onArrival == null) return null;
        Action onGiveUp = json.has("on_give_up") ? Actions.parse(json.get("on_give_up")) : null;
        if (json.has("on_give_up") && onGiveUp == null) return null;
        boolean onFound = !"self".equalsIgnoreCase(GsonHelper.getAsString(json, "target", "found"));
        return new Seeks.Order(id, entities, blocks, within, each, limit, Set.copyOf(when), speed, reach,
                giveUp, revisit, lasts, onArrival, onFound, onGiveUp, json.deepCopy());
    }

    /** A seek's {@code within}, the place to search, for others that search the same way (routines). Null if bad. */
    public static Seeks.Within within(JsonObject json) {
        return within(json, 0);
    }

    private static Seeks.Within within(JsonObject json, int depth) {
        ResourceLocation placeId = json.has("place") ? ResourceLocation.tryParse(GsonHelper.getAsString(json, "place", "")) : SeekPlaces.AROUND;
        if (placeId == null) return null;
        SeekPlaces.Place place = SeekPlaces.get(placeId);
        if (place == null) return null;
        Seeks.Within otherwise = null;
        if (json.has("otherwise") && json.get("otherwise").isJsonObject() && depth < 3) {
            otherwise = within(json.getAsJsonObject("otherwise"), depth + 1);
            if (otherwise == null) return null;
        }
        return new Seeks.Within(place, json.deepCopy(), otherwise);
    }
}
