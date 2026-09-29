package com.aetherianartificer.townstead.work.martial;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The settings of one martial Job: patrol a route, hold a post, sweep past the border, or drill.
 * Every kind shares {@code engage} (what to fight and how far to chase) and {@code arms} (what to
 * carry for the shift and how to fight with it).
 *
 * <pre>{@code
 * { "schema": "townstead:job/v3", "task": "townstead_work:patrol", "type": "townstead:patrol",
 *   "route": { "along": "village_edge", "inset": 4, "linger": 80 },
 *   "engage": { "hostile": true, "sight": 16, "chase": 48 },
 *   "arms": { "draw": "#townstead:hunter_arms", "style": "auto" },
 *   "xp": { "post": 1, "kill": 8 } }
 * }</pre>
 */
public record MartialJob(Kind kind, Route route, Engage engage, Arms arms, Sweep sweep, @Nullable Place place,
                         Drill drill, Xp xp) {

    public enum Kind { PATROL, HOLD_POST, SWEEP, DRILL;
        static @Nullable Kind of(ResourceLocation type) {
            if (!"townstead".equals(type.getNamespace())) return null;
            return switch (type.getPath()) {
                case "patrol" -> PATROL;
                case "hold_post" -> HOLD_POST;
                case "sweep" -> SWEEP;
                case "drill" -> DRILL;
                default -> null;
            };
        }
    }

    public enum Style { AUTO, MELEE, RANGED }

    /** {@code inset} keeps posts inside the village box; {@code linger} is how long to stay at each. */
    public record Route(int inset, int linger) {}

    /** Fight what the villager's group regards as hostile, and/or these groups, seen within {@code sight}. */
    public record Engage(boolean hostile, Set<String> groups, double sight, double chase) {}

    /** What to take from the villager's own inventory for the shift. */
    public record Arms(@Nullable ResourceLocation item, boolean tag, Style style) {
        public boolean matches(ItemStack stack) {
            if (item == null || stack.isEmpty()) return false;
            return tag ? stack.is(TagKey.create(Registries.ITEM, item))
                    : item.equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }
    }

    /** How far past the village box a sweep looks, and how often. */
    public record Sweep(double reach, int interval) {}

    /** Where a post or a drill takes place: the order's altar, or a block the village's buildings hold. */
    public record Place(boolean orderAltar, @Nullable ResourceLocation block, boolean tag) {
        public boolean matches(BlockState state) {
            if (block == null) return false;
            return tag ? state.is(TagKey.create(Registries.BLOCK, block))
                    : block.equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        }
    }

    /** A drill session lasts {@code session} ticks at the place; {@code after} runs on the villager when it ends. */
    public record Drill(int session, @Nullable Action after) {}

    public record Xp(int post, int kill, int drill) {}

    public static @Nullable MartialJob parse(ResourceLocation type, JsonObject json) {
        Kind kind = Kind.of(type);
        if (kind == null) return null;
        JsonObject route = GsonHelper.getAsJsonObject(json, "route", new JsonObject());
        JsonObject engage = GsonHelper.getAsJsonObject(json, "engage", new JsonObject());
        Set<String> groups = new LinkedHashSet<>();
        for (JsonElement group : GsonHelper.getAsJsonArray(engage, "groups", new com.google.gson.JsonArray())) groups.add(group.getAsString());
        JsonObject arms = GsonHelper.getAsJsonObject(json, "arms", new JsonObject());
        String draw = GsonHelper.getAsString(arms, "draw", "");
        boolean drawTag = draw.startsWith("#");
        ResourceLocation drawId = draw.isEmpty() ? null : DataPackLang.parseId(drawTag ? draw.substring(1) : draw);
        Style style;
        try {
            style = Style.valueOf(GsonHelper.getAsString(arms, "style", "auto").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
        Place place = null;
        if (json.has("place")) {
            JsonObject raw = GsonHelper.getAsJsonObject(json, "place");
            if (GsonHelper.getAsBoolean(raw, "order_altar", false)) {
                place = new Place(true, null, false);
            } else if (raw.has("block")) {
                String block = GsonHelper.getAsString(raw, "block");
                boolean tag = block.startsWith("#");
                ResourceLocation id = DataPackLang.parseId(tag ? block.substring(1) : block);
                if (id == null) return null;
                place = new Place(false, id, tag);
            }
        }
        if ((kind == Kind.HOLD_POST || kind == Kind.DRILL) && place == null) return null;
        JsonObject drill = GsonHelper.getAsJsonObject(json, "drill", new JsonObject());
        Action after = drill.has("after") ? Actions.parse(drill.get("after")) : null;
        JsonObject xp = GsonHelper.getAsJsonObject(json, "xp", new JsonObject());
        return new MartialJob(kind,
                new Route(GsonHelper.getAsInt(route, "inset", 4), GsonHelper.getAsInt(route, "linger", 80)),
                new Engage(GsonHelper.getAsBoolean(engage, "hostile", true), Set.copyOf(groups),
                        GsonHelper.getAsDouble(engage, "sight", 16), GsonHelper.getAsDouble(engage, "chase", 24)),
                new Arms(drawId, drawTag, style),
                new Sweep(GsonHelper.getAsDouble(json, "reach", 48), GsonHelper.getAsInt(json, "interval", 200)),
                place,
                new Drill(GsonHelper.getAsInt(drill, "session", 1200), after),
                new Xp(GsonHelper.getAsInt(xp, "post", 1), GsonHelper.getAsInt(xp, "kill", 8), GsonHelper.getAsInt(xp, "drill", 2)));
    }

    /** Whether {@code item} counts as a ranged weapon for {@code style}. */
    public static boolean ranged(Style style, Item item) {
        return switch (style) {
            case RANGED -> true;
            case MELEE -> false;
            case AUTO -> item instanceof net.minecraft.world.item.ProjectileWeaponItem;
        };
    }
}
