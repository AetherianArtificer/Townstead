package com.aetherianartificer.townstead.client.catalog;

import com.aetherianartificer.townstead.Townstead;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
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

    private RequirementNameResolver() {}

    public static String displayName(ResourceLocation id) {
        String cached = CACHE.get(id);
        if (cached != null) return cached;
        String result = resolve(id);
        CACHE.put(id, result);
        return result;
    }

    public static int cacheSize() {
        return CACHE.size();
    }

    public static void clear() {
        CACHE.clear();
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
        if (requirementIds == null || requirementIds.isEmpty()) {
            Townstead.LOGGER.info("[TS-Diag/ReqName] prewarmAsync skip reason=empty");
            return;
        }
        if (!ASYNC_WARM_RUNNING.compareAndSet(false, true)) {
            Townstead.LOGGER.info("[TS-Diag/ReqName] prewarmAsync skip reason=alreadyRunning ids={}",
                    requirementIds.size());
            return;
        }
        // Snapshot defensively — the caller may pass a live Map.keySet().
        java.util.List<ResourceLocation> snapshot = new java.util.ArrayList<>(requirementIds);
        Townstead.LOGGER.info("[TS-Diag/ReqName] prewarmAsync dispatch ids={} cacheSize={}",
                snapshot.size(), CACHE.size());
        ForkJoinPool.commonPool().execute(() -> {
            try {
                long t0 = System.nanoTime();
                int hitsBefore = CACHE.size();
                int resolved = 0;
                for (ResourceLocation id : snapshot) {
                    if (!CACHE.containsKey(id)) {
                        CACHE.put(id, resolve(id));
                        resolved++;
                    }
                }
                long elapsed = System.nanoTime() - t0;
                Townstead.LOGGER.info("[TS-Diag/ReqName] prewarmAsync done seen={} resolved={} cacheSize={} elapsedUs={} thread={}",
                        snapshot.size(), resolved, CACHE.size(),
                        elapsed / 1_000L, Thread.currentThread().getName());
            } catch (Throwable t) {
                Townstead.LOGGER.warn("[TS-Diag/ReqName] prewarmAsync failed", t);
            } finally {
                ASYNC_WARM_RUNNING.set(false);
            }
        });
    }

    /**
     * Convenience entry point: gathers every building type's requirement ids
     * on the calling (main) thread and dispatches the async warm.
     */
    public static void prewarmAllFromBuildingTypes() {
        Townstead.LOGGER.info("[TS-Diag/ReqName] prewarmAllFromBuildingTypes entered thread={}",
                Thread.currentThread().getName());
        java.util.LinkedHashSet<ResourceLocation> ids = new java.util.LinkedHashSet<>();
        try {
            for (BuildingType bt : BuildingTypes.getInstance()) {
                ids.addAll(bt.getGroups().keySet());
            }
        } catch (Throwable t) {
            Townstead.LOGGER.warn("[TS-Diag/ReqName] failed to collect requirement ids on calling thread", t);
            return;
        }
        Townstead.LOGGER.info("[TS-Diag/ReqName] prewarmAllFromBuildingTypes collected ids={}", ids.size());
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
