package com.aetherianartificer.townstead.contract;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.pheno.marker.PlayerMarkers;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.Values;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * One objective of a contract, by {@code type}:
 * <ul>
 * <li>{@code townstead:kill_entity}: {@code entity}, {@code tag} or {@code group} (a disposition
 * group), one or a list, and {@code count}. Kills after the contract was taken.</li>
 * <li>{@code townstead:obtain_item}: {@code item} or {@code tag}, {@code count}. Carried now.</li>
 * <li>{@code townstead:item_delivery}: as obtain_item, and taken on hand-in.</li>
 * <li>{@code townstead:find_marked}: {@code key} of a place marked for the player
 * ({@code pheno:mark_structure}), {@code radius}. Reaching it stays done.</li>
 * <li>{@code townstead:cure_vampirism}: {@code count} cures started after taking it.</li>
 * <li>{@code townstead:condition}: any Pheno {@code condition} on the player. Stays done.</li>
 * <li>{@code townstead:gain}: any Pheno {@code value} on the player, risen by {@code count}.</li>
 * </ul>
 * Every objective can carry a {@code text} value; without one it gets a plain default.
 */
public abstract class ContractObjective {
    public static final long UNKNOWN = -1L;
    static final Set<String> TYPES = Set.of("townstead:kill_entity", "townstead:obtain_item", "townstead:item_delivery",
            "townstead:find_marked", "townstead:cure_vampirism", "townstead:condition", "townstead:gain");

    final ContractText text;
    final long count;

    ContractObjective(ContractText text, long count) {
        this.text = text;
        this.count = Math.max(1, count);
    }

    /** The current reading, before any baseline; {@link #UNKNOWN} when it cannot be read. */
    abstract long read(ServerPlayer player, @Nullable LivingEntity giver);

    /** Whether progress counts from when the contract was taken. */
    boolean gain() { return false; }

    /** Whether progress, once reached, stays reached. */
    boolean latch() { return true; }

    @Nullable String marker() { return null; }

    /** Hand-in step. False when it cannot be done now (the goods are gone). */
    boolean turnIn(ServerPlayer player) { return true; }

    abstract ContractText defaultText();

    public long count() { return count; }

    String label(Map<String, String> values, String locale, ServerPlayer player) {
        String out = (text.isEmpty() ? defaultText() : text).resolve(values, locale);
        String key = marker();
        if (key == null) return out;
        var pos = PlayerMarkers.get(player, key);
        return out.replace("{x}", pos == null ? "?" : Integer.toString(pos.pos().getX()))
                .replace("{z}", pos == null ? "?" : Integer.toString(pos.pos().getZ()));
    }

    // ---- parsing ----

    /** Checks the shape of a template objective before any value is in. Returns an error, or null. */
    static @Nullable String check(JsonObject json) {
        String type = GsonHelper.getAsString(json, "type", "");
        if (!TYPES.contains(type)) return "unknown objective type '" + type + "'";
        return switch (type) {
            case "townstead:kill_entity" -> json.has("entity") || json.has("tag") || json.has("group") ? null
                    : "kill_entity needs \"entity\", \"tag\" or \"group\"";
            case "townstead:obtain_item", "townstead:item_delivery" -> json.has("item") || json.has("tag") ? null
                    : type.substring(10) + " needs \"item\" or \"tag\"";
            case "townstead:find_marked" -> json.has("key") ? null : "find_marked needs \"key\"";
            case "townstead:condition" -> json.has("condition") ? null : "condition needs \"condition\"";
            case "townstead:gain" -> json.has("value") ? null : "gain needs \"value\"";
            default -> null;
        };
    }

    /** Builds an objective from JSON with its template values already in. */
    static ContractObjective parse(JsonObject json) {
        String type = GsonHelper.getAsString(json, "type");
        ContractText text = json.has("text") ? ContractText.parse(json.get("text")) : ContractText.EMPTY;
        long count = GsonHelper.getAsLong(json, "count", 1);
        return switch (type) {
            case "townstead:kill_entity" -> new Kill(text, count, ids(json.get("entity")), ids(json.get("tag")), strings(json.get("group")));
            case "townstead:obtain_item" -> new Carry(text, count, items(json), itemName(json), false);
            case "townstead:item_delivery" -> new Carry(text, count, items(json), itemName(json), true);
            case "townstead:find_marked" -> new Marked(text, GsonHelper.getAsString(json, "key"), GsonHelper.getAsInt(json, "radius", 48));
            case "townstead:cure_vampirism" -> new Cure(text, count);
            case "townstead:condition" -> new Holds(text, json.get("condition"));
            case "townstead:gain" -> new Gain(text, count, json.get("value"));
            default -> throw new IllegalArgumentException("unknown objective type '" + type + "'");
        };
    }

    private static List<ResourceLocation> ids(@Nullable JsonElement raw) {
        List<ResourceLocation> out = new ArrayList<>();
        for (String s : strings(raw)) {
            ResourceLocation id = DataPackLang.parseId(s.startsWith("#") ? s.substring(1) : s);
            if (id != null) out.add(id);
        }
        return out;
    }

    private static List<String> strings(@Nullable JsonElement raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isJsonNull()) return out;
        if (raw.isJsonArray()) raw.getAsJsonArray().forEach(e -> out.add(e.getAsString()));
        else out.add(raw.getAsString());
        return out;
    }

    private static Predicate<ItemStack> items(JsonObject json) {
        if (json.has("tag")) {
            ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "tag").replace("#", ""));
            TagKey<Item> tag = TagKey.create(Registries.ITEM, id);
            return stack -> stack.is(tag);
        }
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "item"));
        return stack -> id != null && id.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private static String itemName(JsonObject json) {
        if (json.has("item")) {
            ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "item"));
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                return BuiltInRegistries.ITEM.get(id).getDescription().getString();
            }
        }
        String raw = json.has("tag") ? GsonHelper.getAsString(json, "tag") : GsonHelper.getAsString(json, "item", "");
        return raw.substring(raw.indexOf(':') + 1).replace('_', ' ');
    }

    static int carried(ServerPlayer player, Predicate<ItemStack> matches) {
        int total = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && matches.test(stack)) total += stack.getCount();
        }
        return total;
    }

    // ---- kinds ----

    private static final class Kill extends ContractObjective {
        final List<ResourceLocation> types, tags;
        final List<String> groups;

        Kill(ContractText text, long count, List<ResourceLocation> types, List<ResourceLocation> tags, List<String> groups) {
            super(text, count);
            this.types = types;
            this.tags = tags;
            this.groups = groups;
        }

        long read(ServerPlayer player, @Nullable LivingEntity giver) {
            return KillCounts.count(player.server, player.getUUID(), types, tags, groups);
        }

        boolean gain() { return true; }

        ContractText defaultText() { return ContractText.key("contract.townstead.objective.kill", Long.toString(count)); }
    }

    private static final class Carry extends ContractObjective {
        final Predicate<ItemStack> matches;
        final String name;
        final boolean deliver;

        Carry(ContractText text, long count, Predicate<ItemStack> matches, String name, boolean deliver) {
            super(text, count);
            this.matches = matches;
            this.name = name;
            this.deliver = deliver;
        }

        long read(ServerPlayer player, @Nullable LivingEntity giver) {
            return carried(player, matches);
        }

        boolean latch() { return false; }

        boolean turnIn(ServerPlayer player) {
            if (carried(player, matches) < count) return false;
            if (!deliver) return true;
            long left = count;
            var inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize() && left > 0; i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.isEmpty() || !matches.test(stack)) continue;
                int taken = (int) Math.min(left, stack.getCount());
                stack.shrink(taken);
                left -= taken;
            }
            inventory.setChanged();
            return true;
        }

        ContractText defaultText() {
            return ContractText.key(deliver ? "contract.townstead.objective.deliver" : "contract.townstead.objective.carry",
                    Long.toString(count), name);
        }
    }

    private static final class Marked extends ContractObjective {
        final String key;
        final int radius;

        Marked(ContractText text, String key, int radius) {
            super(text, 1);
            this.key = key;
            this.radius = radius;
        }

        long read(ServerPlayer player, @Nullable LivingEntity giver) {
            var pos = PlayerMarkers.get(player, key);
            if (pos == null || !pos.dimension().equals(player.level().dimension())) return 0;
            return pos.pos().closerToCenterThan(player.position(), radius) ? 1 : 0;
        }

        @Nullable String marker() { return key; }

        ContractText defaultText() { return ContractText.key("contract.townstead.objective.find_marked", "{x}", "{z}"); }
    }

    private static final class Cure extends ContractObjective {
        Cure(ContractText text, long count) {
            super(text, count);
        }

        long read(ServerPlayer player, @Nullable LivingEntity giver) {
            return com.aetherianartificer.townstead.chronicle.Chronicles.count(player.server, player.getUUID(),
                    com.aetherianartificer.townstead.compat.vampirism.VampireVillagers.CURED_BY);
        }

        boolean gain() { return true; }

        ContractText defaultText() { return ContractText.key("contract.townstead.objective.cure", Long.toString(count)); }
    }

    private static final class Holds extends ContractObjective {
        final JsonElement raw;
        @Nullable Condition condition;

        Holds(ContractText text, JsonElement raw) {
            super(text, 1);
            this.raw = raw;
        }

        long read(ServerPlayer player, @Nullable LivingEntity giver) {
            if (condition == null) condition = Conditions.parse(raw);
            if (condition == null) return UNKNOWN;
            return condition.test(new ConditionContext(player, giver)) ? 1 : 0;
        }

        ContractText defaultText() { return ContractText.key("contract.townstead.objective.condition"); }
    }

    private static final class Gain extends ContractObjective {
        final JsonElement raw;
        @Nullable Value value;

        Gain(ContractText text, long count, JsonElement raw) {
            super(text, count);
            this.raw = raw;
        }

        long read(ServerPlayer player, @Nullable LivingEntity giver) {
            if (value == null) value = Values.parse(raw);
            if (value == null) return UNKNOWN;
            double reading = value.get(new SelectorContext(player, giver, player, player.level(), player.position()));
            return Double.isFinite(reading) ? (long) Math.floor(reading) : UNKNOWN;
        }

        boolean gain() { return true; }

        ContractText defaultText() { return ContractText.key("contract.townstead.objective.gain", Long.toString(count)); }
    }
}
