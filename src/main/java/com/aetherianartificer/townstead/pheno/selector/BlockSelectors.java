package com.aetherianartificer.townstead.pheno.selector;

import com.aetherianartificer.townstead.pheno.condition.block.BlockCondition;
import com.aetherianartificer.townstead.pheno.condition.block.BlockConditions;
import com.aetherianartificer.townstead.pheno.selector.spatial.Region;
import com.aetherianartificer.townstead.pheno.selector.spatial.Spatial;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Parses the {@code on} value of a block action into a {@link BlockSelector}: a place is its block,
 * a region is its blocks (optionally a block {@code where} filter and a {@code limit}), an array is
 * the union. The spatial vocabulary is the same one entity selection uses; here it extracts
 * positions. A bounded enumeration cap keeps a large radius from running away.
 */
public final class BlockSelectors {

    private static final int ENUM_CAP = 8192;

    private BlockSelectors() {}

    @Nullable
    public static BlockSelector parse(@Nullable JsonElement element) {
        if (element == null) return null;
        Region region = Spatial.parse(element);
        if (region != null) {
            BlockCondition where = null;
            int limit = 0;
            if (element.isJsonObject()) {
                JsonObject obj = element.getAsJsonObject();
                if (obj.has("where")) {
                    where = BlockConditions.parse(obj.get("where"));
                    if (where == null) return null;
                }
                limit = Math.max(0, GsonHelper.getAsInt(obj, "limit", 0));
            }
            BlockCondition filter = where;
            int lim = limit;
            return ctx -> {
                List<BlockPos> raw = region.positions(ctx, ENUM_CAP);
                if (filter == null && lim <= 0) return raw;
                List<BlockPos> out = new ArrayList<>();
                for (BlockPos pos : raw) {
                    if (filter == null || filter.test(ctx.level(), pos)) out.add(pos);
                    if (lim > 0 && out.size() >= lim) break;
                }
                return out;
            };
        }
        if (element.isJsonArray()) {
            List<BlockSelector> parts = new ArrayList<>();
            for (JsonElement child : element.getAsJsonArray()) {
                BlockSelector part = parse(child);
                if (part == null) return null;
                parts.add(part);
            }
            return ctx -> {
                LinkedHashSet<BlockPos> union = new LinkedHashSet<>();
                for (BlockSelector part : parts) union.addAll(part.select(ctx));
                return new ArrayList<>(union);
            };
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            return BlockSelectorTypes.get(GsonHelper.getAsString(obj, "type", "")).map(t -> t.parse(obj)).orElse(null);
        }
        return null;
    }
}
