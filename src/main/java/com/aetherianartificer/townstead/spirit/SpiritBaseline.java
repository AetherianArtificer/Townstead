package com.aetherianartificer.townstead.spirit;

import com.aetherianartificer.townstead.Townstead;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * What an ordinary village's Community Spirit looks like: the share each axis is expected to hold.
 * A village is known for what it has above this, so the farms every village has pull nobody, and a
 * harbor and market do. Loaded from {@code data/<ns>/spirit_baseline/*.json}; files apply in id order
 * and a later file replaces the axes it names.
 *
 * <pre>{@code
 * { "schema": "townstead:spirit_baseline/v1", "shares": { "pastoral": 0.40, "industrious": 0.25 } }
 * }</pre>
 */
public final class SpiritBaseline {
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/SpiritBaseline");
    private static volatile Map<String, Float> shares = Map.of();

    private SpiritBaseline() {}

    /** The ordinary share of an axis, 0 when the baseline does not name it. */
    public static float of(String axis) {
        return shares.getOrDefault(axis, 0F);
    }

    /** How far above the ordinary this village's share of an axis is; never negative. */
    public static double excess(SpiritTotals totals, String axis) {
        if (totals == null || totals.total() <= 0) return 0;
        return Math.max(0.0, totals.shareOf(axis) - of(axis));
    }

    public static void replace(Map<String, Float> values) {
        shares = Map.copyOf(values);
    }

    public static final class Loader extends SimpleJsonResourceReloadListener {
        public Loader() {
            super(new Gson(), "spirit_baseline");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
            Map<String, Float> merged = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, JsonElement> entry : new TreeMap<>(entries).entrySet()) {
                try {
                    JsonObject json = GsonHelper.getAsJsonObject(GsonHelper.convertToJsonObject(entry.getValue(), "baseline"), "shares");
                    for (Map.Entry<String, JsonElement> axis : json.entrySet()) {
                        if (!SpiritRegistry.contains(axis.getKey())) {
                            LOGGER.warn("Spirit baseline {} names unknown spirit '{}'", entry.getKey(), axis.getKey());
                            continue;
                        }
                        float share = GsonHelper.convertToFloat(axis.getValue(), axis.getKey());
                        if (!Float.isFinite(share) || share < 0 || share > 1) {
                            LOGGER.warn("Spirit baseline {} gives {} a share outside 0..1", entry.getKey(), axis.getKey());
                            continue;
                        }
                        merged.put(axis.getKey(), share);
                    }
                } catch (RuntimeException error) {
                    LOGGER.warn("Could not load spirit baseline {}: {}", entry.getKey(), error.getMessage());
                }
            }
            replace(merged);
            LOGGER.info("Loaded the spirit baseline for {} axis(es)", merged.size());
        }
    }
}
