package com.aetherianartificer.townstead.client.catalog;

import com.aetherianartificer.townstead.Townstead;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Resolves a human-readable name for a building requirement (block id, item id,
 * or block/item tag). Translatable resolution is amortized via a shared static
 * cache so the catalog detail panel doesn't pay {@code Component.translatable}
 * overhead per requirement on every selection change.
 *
 * Safe to call from any thread: registries and the {@code Language} manager
 * are read-only once the game is initialized.
 */
public final class RequirementNameResolver {
    private static final ConcurrentHashMap<ResourceLocation, String> CACHE = new ConcurrentHashMap<>();
    private static final AtomicBoolean ASYNC_WARM_RUNNING = new AtomicBoolean(false);
    /**
     * Set true once a full BuildingTypes warm has finished. The catalog-open
     * fallback in {@code BlueprintScreenMixin} calls
     * {@link #prewarmAllFromBuildingTypes()} on every open; once warmed, that
     * call short-circuits before iterating BuildingTypes (which is otherwise
     * a few thousand HashSet adds at 400-mod scale).
     */
    private static final AtomicBoolean WARMED = new AtomicBoolean(false);

    private RequirementNameResolver() {}

    public static String displayName(ResourceLocation id) {
        String cached = CACHE.get(id);
        if (cached != null) return cached;
        String result = resolve(id);
        CACHE.put(id, result);
        return result;
    }

    /**
     * Concrete icon for a requirement. Direct block/item ids stay fixed; a tag cycles through
     * the installed members it actually accepts. Shared by the catalog and the pinned checklist
     * so those two views never explain the same requirement differently.
     */
    public static ItemStack displayStack(ResourceLocation id, long ticker, int salt) {
        if (BuiltInRegistries.BLOCK.containsKey(id)) {
            Item item = BuiltInRegistries.BLOCK.get(id).asItem();
            return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        }
        if (BuiltInRegistries.ITEM.containsKey(id)) {
            Item item = BuiltInRegistries.ITEM.get(id);
            return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        }

        java.util.List<Item> candidates = new java.util.ArrayList<>();
        TagKey<Block> blockTag = TagKey.create(Registries.BLOCK, id);
        for (Block block : BuiltInRegistries.BLOCK) {
            Item item = block.asItem();
            if (block.defaultBlockState().is(blockTag) && item != null && item != Items.AIR) {
                candidates.add(item);
            }
        }
        if (candidates.isEmpty()) {
            TagKey<Item> itemTag = TagKey.create(Registries.ITEM, id);
            for (Item item : BuiltInRegistries.ITEM) {
                if (item != Items.AIR && item.builtInRegistryHolder().is(itemTag)) candidates.add(item);
            }
        }
        if (candidates.isEmpty()) return ItemStack.EMPTY;
        int index = (int) Math.floorMod((ticker / 20L) + salt, candidates.size());
        return new ItemStack(candidates.get(index));
    }

    /** The concrete member's name for a tag, or the ordinary resolved name for a direct id. */
    public static String displayName(ResourceLocation id, ItemStack shown) {
        if (!shown.isEmpty()
                && !BuiltInRegistries.BLOCK.containsKey(id)
                && !BuiltInRegistries.ITEM.containsKey(id)) {
            return shown.getHoverName().getString();
        }
        return displayName(id);
    }

    /**
     * Pre-resolve every requirement of every building type on a worker
     * thread. The caller must snapshot the requirement-id set on the main
     * thread first — {@code BuildingTypes.getInstance()} is the data-pack
     * registry and not safe to iterate off-thread; we accept a flat list of
     * IDs and only translate them on the worker, which is safe because the
     * Language manager + registries are read-only post-init.
     */
    public static void prewarmAsync(java.util.Collection<ResourceLocation> requirementIds) {
        if (requirementIds == null || requirementIds.isEmpty()) return;
        if (!ASYNC_WARM_RUNNING.compareAndSet(false, true)) return;
        // Snapshot defensively — the caller may pass a live Map.keySet().
        java.util.List<ResourceLocation> snapshot = new java.util.ArrayList<>(requirementIds);
        ForkJoinPool.commonPool().execute(() -> {
            try {
                for (ResourceLocation id : snapshot) {
                    if (!CACHE.containsKey(id)) CACHE.put(id, resolve(id));
                }
                WARMED.set(true);
            } catch (Throwable t) {
                Townstead.LOGGER.warn("RequirementNameResolver prewarm failed", t);
            } finally {
                ASYNC_WARM_RUNNING.set(false);
            }
        });
    }

    /**
     * Convenience entry point: gathers every building type's requirement ids
     * on the calling (main) thread and dispatches the async warm. The gather
     * step must run on the main thread because {@code BuildingTypes} is the
     * data-pack registry and not safe to iterate off-thread.
     *
     * <p>Short-circuits once a full warm has completed, so the catalog-open
     * fallback path doesn't pay to iterate {@code BuildingTypes} every time.
     * Also short-circuits if a warm is currently in flight — the in-flight
     * one already covers the same ids.
     */
    public static void prewarmAllFromBuildingTypes() {
        if (WARMED.get() || ASYNC_WARM_RUNNING.get()) return;
        java.util.LinkedHashSet<ResourceLocation> ids = new java.util.LinkedHashSet<>();
        try {
            for (BuildingType bt : BuildingTypes.getInstance()) {
                ids.addAll(bt.getGroups().keySet());
            }
        } catch (Throwable t) {
            Townstead.LOGGER.warn("RequirementNameResolver: failed to collect requirement ids", t);
            return;
        }
        prewarmAsync(ids);
    }

    private static String resolve(ResourceLocation id) {
        if (BuiltInRegistries.BLOCK.containsKey(id)) {
            Block block = BuiltInRegistries.BLOCK.get(id);
            return Component.translatable(block.getDescriptionId()).getString();
        }
        if (BuiltInRegistries.ITEM.containsKey(id)) {
            Item item = BuiltInRegistries.ITEM.get(id);
            return Component.translatable(item.getDescriptionId()).getString();
        }
        String tagPath = id.toString().replace(':', '.').replace('/', '.');
        String slashKey = "tag.block." + tagPath;
        String dottedKey = "tag.item." + tagPath;
        String slash = Component.translatable(slashKey).getString();
        if (!slash.equals(slashKey)) return slash;
        String dotted = Component.translatable(dottedKey).getString();
        if (!dotted.equals(dottedKey)) return dotted;
        String fallback = id.getPath().replace('_', ' ');
        if (fallback.endsWith("s") && fallback.length() > 3) {
            fallback = fallback.substring(0, fallback.length() - 1);
        }
        String[] words = fallback.split(" ");
        StringBuilder out = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(w.substring(0, 1).toUpperCase(Locale.ROOT)).append(w.substring(1));
        }
        return out.toString();
    }
}
