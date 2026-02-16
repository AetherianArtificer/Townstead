package com.aetherianartificer.townstead.farming;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.ArrayList;
import java.util.List;

public final class FarmingPolicyData extends SavedData {
    private static final String DATA_NAME = "townstead_farming_policy";
    private static final String DEFAULT_PATTERN_ID = "starter_rows";

    private String defaultPatternId = DEFAULT_PATTERN_ID;
    private int defaultTier = 3;
    private final List<FarmingAreaPolicy> areas = new ArrayList<>();

    public static FarmingPolicyData get(ServerLevel level) {
        DimensionDataStorage storage = level.getServer().overworld().getDataStorage();
        return storage.computeIfAbsent(
                new Factory<>(FarmingPolicyData::new, FarmingPolicyData::load, DataFixTypes.LEVEL),
                DATA_NAME
        );
    }

    public ResolvedFarmingPolicy resolveForAnchor(BlockPos anchor) {
        FarmingAreaPolicy best = null;
        int bestDist = Integer.MAX_VALUE;
        for (FarmingAreaPolicy area : areas) {
            if (!area.contains(anchor)) continue;
            int dist = area.distanceToCenterSq(anchor);
            if (dist < bestDist) {
                bestDist = dist;
                best = area;
            }
        }
        if (best != null) {
            return new ResolvedFarmingPolicy(
                    normalizePatternId(best.patternId()),
                    normalizeTier(best.tier()),
                    "area"
            );
        }
        String pattern = normalizePatternId(defaultPatternId);
        int tier = normalizeTier(defaultTier);
        if (areas.isEmpty() && DEFAULT_PATTERN_ID.equals(pattern) && tier <= 1) {
            // Bootstrap to a practical tier until in-game policy UI is wired.
            tier = 3;
        }
        return new ResolvedFarmingPolicy(pattern, tier, "default");
    }

    public String getDefaultPatternId() {
        return normalizePatternId(defaultPatternId);
    }

    public int getDefaultTier() {
        return normalizeTier(defaultTier);
    }

    public void setDefaultPolicy(String patternId, int tier) {
        String normalizedPattern = normalizePatternId(patternId);
        int normalizedTier = normalizeTier(tier);
        if (normalizedPattern.equals(this.defaultPatternId) && normalizedTier == this.defaultTier) return;
        this.defaultPatternId = normalizedPattern;
        this.defaultTier = normalizedTier;
        setDirty();
    }

    public List<FarmingAreaPolicy> getAreas() {
        return List.copyOf(areas);
    }

    public void setAreas(List<FarmingAreaPolicy> newAreas) {
        areas.clear();
        if (newAreas != null) areas.addAll(newAreas);
        setDirty();
    }

    public static FarmingPolicyData load(CompoundTag tag, HolderLookup.Provider registries) {
        FarmingPolicyData data = new FarmingPolicyData();
        data.defaultPatternId = normalizePatternId(tag.getString("defaultPatternId"));
        data.defaultTier = tag.contains("defaultTier") ? normalizeTier(tag.getInt("defaultTier")) : 3;

        ListTag list = tag.getList("areas", Tag.TAG_COMPOUND);
        for (Tag t : list) {
            if (!(t instanceof CompoundTag areaTag)) continue;
            data.areas.add(FarmingAreaPolicy.fromTag(areaTag));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("defaultPatternId", normalizePatternId(defaultPatternId));
        tag.putInt("defaultTier", normalizeTier(defaultTier));

        ListTag list = new ListTag();
        for (FarmingAreaPolicy area : areas) {
            list.add(area.toTag());
        }
        tag.put("areas", list);
        return tag;
    }

    private static String normalizePatternId(String patternId) {
        if (patternId == null || patternId.isBlank()) return DEFAULT_PATTERN_ID;
        return patternId;
    }

    private static int normalizeTier(int tier) {
        return Math.max(1, Math.min(tier, 5));
    }

    public record ResolvedFarmingPolicy(String patternId, int tier, String source) {}

    public record FarmingAreaPolicy(BlockPos center, int radius, String patternId, int tier) {
        public static FarmingAreaPolicy fromTag(CompoundTag tag) {
            BlockPos center = BlockPos.of(tag.getLong("center"));
            int radius = Math.max(1, tag.getInt("radius"));
            String patternId = tag.getString("patternId");
            int tier = tag.getInt("tier");
            return new FarmingAreaPolicy(center, radius, patternId, tier);
        }

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("center", center.asLong());
            tag.putInt("radius", Math.max(1, radius));
            tag.putString("patternId", normalizePatternId(patternId));
            tag.putInt("tier", normalizeTier(tier));
            return tag;
        }

        public boolean contains(BlockPos pos) {
            int dx = Math.abs(pos.getX() - center.getX());
            int dz = Math.abs(pos.getZ() - center.getZ());
            return dx <= radius && dz <= radius;
        }

        public int distanceToCenterSq(BlockPos pos) {
            int dx = pos.getX() - center.getX();
            int dy = pos.getY() - center.getY();
            int dz = pos.getZ() - center.getZ();
            return dx * dx + dy * dy + dz * dz;
        }
    }
}
