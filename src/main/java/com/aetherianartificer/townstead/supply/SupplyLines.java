package com.aetherianartificer.townstead.supply;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Supply the planner can count but cannot name by item id.
 *
 * <p>Most of what a villager needs is an item id: six carrots, one bowl. Some of it isn't. Impure
 * water is any container a thirst mod scores as unclean, across many ids. Furnace fuel is anything
 * that burns. Caupona measures soup in fluid, and village wells will measure water by the source
 * block rather than by a stack. Each of those used to want its own branch wedged into the storage
 * snapshot, and impure water got one.</p>
 *
 * <p>A line gives that supply a synthetic {@link ResourceLocation} the rest of the engine can treat
 * as an ordinary ingredient id, backed by a predicate instead of a registry entry. Recipes then
 * request {@code townstead:furnace_fuel} the same way they request {@code minecraft:carrot}, and
 * every existing path (planning, scoring, snapshotting, slot search) works unchanged.</p>
 *
 * <p>A line whose backing mod is absent reports {@link Line#active()} false and is skipped, so the
 * common case costs one list read. With no lines registered at all, every call is a fast return.</p>
 */
public final class SupplyLines {

    /** One kind of supply identified by what a stack *is* rather than by which item it is. */
    public interface Line {

        /** The synthetic id recipes and supply maps use for this line. */
        ResourceLocation id();

        /**
         * False when the mod or bridge backing this line is missing. Inactive lines are never
         * consulted, so a line for an absent mod costs nothing beyond the check itself.
         */
        boolean active();

        /** Whether this stack counts toward the line. Contribution is the stack's own count. */
        boolean matches(ItemStack stack, ServerLevel level);

        /**
         * Relative preference when several concrete stacks satisfy this line. Higher values are
         * consumed first. Zero preserves the normal nearest-slot behavior.
         */
        default int preference(ItemStack stack, ServerLevel level) {
            return 0;
        }
    }

    private static final Map<ResourceLocation, Line> LINES = new ConcurrentHashMap<>();
    private static volatile List<Line> ORDERED = List.of();

    private SupplyLines() {}

    public static void register(Line line) {
        if (line == null || line.id() == null) return;
        LINES.put(line.id(), line);
        ORDERED = List.copyOf(LINES.values());
    }

    /** Every registered line, active or not. Empty in a pack with no bridges installed. */
    public static List<Line> all() {
        return ORDERED;
    }

    /** True when no line is registered, so callers can skip their slot walks entirely. */
    public static boolean isEmpty() {
        return ORDERED.isEmpty();
    }

    @Nullable
    public static Line byId(@Nullable ResourceLocation id) {
        return id == null ? null : LINES.get(id);
    }

    /** True if the id names a supply line rather than an item. */
    public static boolean isLineId(@Nullable ResourceLocation id) {
        return id != null && LINES.containsKey(id);
    }

    /**
     * The active lines among {@code trackedIds}, so a caller walks stored slots only when
     * something it is actually planning for needs the walk.
     */
    public static List<Line> activeLinesAmong(Iterable<ResourceLocation> trackedIds) {
        if (ORDERED.isEmpty()) return List.of();
        List<Line> matched = new ArrayList<>(1);
        for (ResourceLocation id : trackedIds) {
            Line line = LINES.get(id);
            if (line != null && line.active()) matched.add(line);
        }
        return matched;
    }

    /** A stack predicate for one line, for the slot-search paths that pull the supply in. */
    public static Predicate<ItemStack> matcher(ServerLevel level, ResourceLocation id) {
        Line line = LINES.get(id);
        if (line == null || !line.active()) return stack -> false;
        return stack -> !stack.isEmpty() && line.matches(stack, level);
    }

    public static int preference(ServerLevel level, ResourceLocation id, ItemStack stack) {
        Line line = LINES.get(id);
        if (line == null || !line.active() || stack.isEmpty() || !line.matches(stack, level)) {
            return Integer.MIN_VALUE;
        }
        return line.preference(stack, level);
    }
}
