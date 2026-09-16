package com.aetherianartificer.townstead.work.order;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * The tag questions orders ask, kept in one place.
 *
 * <p>Categories are plain item tags under an {@code orders/} path — any datapack that ships
 * {@code data/<ns>/tags/item/orders/cooked_meats.json} has added an orderable category, with no
 * Java and no schema. Membership goes through a swappable resolver so the order arithmetic stays
 * unit-testable: the tests never load a registry, and the game never notices the seam.</p>
 */
public final class OrderTags {

    /** The path prefix that marks an item tag as an orderable category. */
    public static final String CATEGORY_PREFIX = "orders/";

    /**
     * Meat from people. Hidden from every catalogue and refused as a butcher's input unless the
     * cannibalism config says otherwise; which items count is the tag's business, not code's.
     */
    public static final ResourceLocation CANNIBAL_MEATS = rl("townstead:cannibal_meats");

    private static @Nullable BiPredicate<ResourceLocation, ResourceLocation> resolver;

    /**
     * Categories an engine derives rather than a pack authors: the clothing engine answers "warm
     * clothing" from every entry's thermal value, so a new mod's coat joins the category with no
     * tag written. A derived category and a tag of the same id union.
     */
    private static final Map<ResourceLocation, Supplier<List<ResourceLocation>>> DERIVED = new ConcurrentHashMap<>();

    private OrderTags() {}

    /** Registers a derived category. The supplier is asked on every question, so it must be cheap. */
    public static void registerDerived(ResourceLocation tagId, Supplier<List<ResourceLocation>> members) {
        if (tagId == null || members == null) return;
        DERIVED.put(tagId, members);
    }

    static void clearDerived() {
        DERIVED.clear();
    }

    /** Whether this item id is a member of this item tag. */
    public static boolean contains(@Nullable ResourceLocation tagId, @Nullable ResourceLocation itemId) {
        if (tagId == null || itemId == null) return false;
        Supplier<List<ResourceLocation>> derived = DERIVED.get(tagId);
        if (derived != null && derived.get().contains(itemId)) return true;
        BiPredicate<ResourceLocation, ResourceLocation> active = resolver;
        return active != null ? active.test(tagId, itemId) : Registry.contains(tagId, itemId);
    }

    /** The tag's member item ids, in tag order, derived members first. */
    public static List<ResourceLocation> members(@Nullable ResourceLocation tagId) {
        if (tagId == null) return List.of();
        Supplier<List<ResourceLocation>> derived = DERIVED.get(tagId);
        if (derived == null) return resolver != null ? List.of() : Registry.members(tagId);
        LinkedHashSet<ResourceLocation> out = new LinkedHashSet<>(derived.get());
        if (resolver == null) out.addAll(Registry.members(tagId));
        return new ArrayList<>(out);
    }

    /** Every item tag declared as an orderable category, plus every derived one. */
    public static List<ResourceLocation> categories() {
        LinkedHashSet<ResourceLocation> out = new LinkedHashSet<>();
        if (resolver == null) out.addAll(Registry.categories());
        out.addAll(DERIVED.keySet());
        return new ArrayList<>(out);
    }

    /** Whether an output may be offered or produced at all under the cannibalism settings. */
    public static boolean permitted(@Nullable ResourceLocation output) {
        if (output == null) return true;
        // The tag is asked first: almost nothing is sapient flesh, this sits inside per-stack
        // filters on work-selection ticks, and the config only matters once something matched.
        if (!contains(CANNIBAL_MEATS, output)) return true;
        return com.aetherianartificer.townstead.TownsteadConfig.CANNIBALISM_PRODUCE.get();
    }

    /** Test seam: replaces registry lookups. Null restores the real registry. */
    static void resolveWith(@Nullable BiPredicate<ResourceLocation, ResourceLocation> membership) {
        resolver = membership;
    }

    private static ResourceLocation rl(String raw) {
        //? if >=1.21 {
        return ResourceLocation.parse(raw);
        //?} else {
        /*return new ResourceLocation(raw);
        *///?}
    }

    /** Nested so tests that install a resolver never load registry classes. */
    private static final class Registry {

        static boolean contains(ResourceLocation tagId, ResourceLocation itemId) {
            if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(itemId)) return false;
            // Asked per stack on hot filters: the holder answers without building an ItemStack.
            return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemId)
                    .builtInRegistryHolder().is(key(tagId));
        }

        static List<ResourceLocation> members(ResourceLocation tagId) {
            List<ResourceLocation> out = new java.util.ArrayList<>();
            for (var holder : net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getTagOrEmpty(key(tagId))) {
                out.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(holder.value()));
            }
            return out;
        }

        static List<ResourceLocation> categories() {
            List<ResourceLocation> out = new java.util.ArrayList<>();
            net.minecraft.core.registries.BuiltInRegistries.ITEM.getTagNames()
                    .filter(tag -> tag.location().getPath().startsWith(CATEGORY_PREFIX))
                    .forEach(tag -> out.add(tag.location()));
            return out;
        }

        private static net.minecraft.tags.TagKey<net.minecraft.world.item.Item> key(ResourceLocation tagId) {
            return net.minecraft.tags.TagKey.create(
                    net.minecraft.core.registries.Registries.ITEM, tagId);
        }
    }
}
