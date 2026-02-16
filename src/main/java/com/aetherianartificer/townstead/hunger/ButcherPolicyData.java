package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.hunger.profile.ButcherProfileRegistry;
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

public final class ButcherPolicyData extends SavedData {
    private static final String DATA_NAME = "townstead_butcher_policy";
    private static final String DEFAULT_PROFILE_ID = ButcherProfileRegistry.DEFAULT_PROFILE_ID;

    private String defaultProfileId = DEFAULT_PROFILE_ID;
    private int defaultTier = 5;
    private final List<ButcherAreaPolicy> areas = new ArrayList<>();

    public static ButcherPolicyData get(ServerLevel level) {
        DimensionDataStorage storage = level.getServer().overworld().getDataStorage();
        return storage.computeIfAbsent(
                new Factory<>(ButcherPolicyData::new, ButcherPolicyData::load, DataFixTypes.LEVEL),
                DATA_NAME
        );
    }

    public ResolvedButcherPolicy resolveForAnchor(BlockPos anchor) {
        ButcherAreaPolicy best = null;
        int bestDist = Integer.MAX_VALUE;
        for (ButcherAreaPolicy area : areas) {
            if (!area.contains(anchor)) continue;
            int dist = area.distanceToCenterSq(anchor);
            if (dist < bestDist) {
                bestDist = dist;
                best = area;
            }
        }
        if (best != null) {
            return new ResolvedButcherPolicy(
                    normalizeProfileId(best.profileId()),
                    normalizeTier(best.tier()),
                    "area"
            );
        }
        String profile = normalizeProfileId(defaultProfileId);
        int tier = normalizeTier(defaultTier);
        if (areas.isEmpty() && DEFAULT_PROFILE_ID.equals(profile) && tier <= 1) {
            tier = 5;
        }
        return new ResolvedButcherPolicy(profile, tier, "default");
    }

    public String getDefaultProfileId() {
        return normalizeProfileId(defaultProfileId);
    }

    public int getDefaultTier() {
        return normalizeTier(defaultTier);
    }

    public List<ButcherAreaPolicy> getAreas() {
        return List.copyOf(areas);
    }

    public void setDefaultPolicy(String profileId, int tier) {
        String normalizedProfile = normalizeProfileId(profileId);
        int normalizedTier = normalizeTier(tier);
        if (normalizedProfile.equals(defaultProfileId) && normalizedTier == defaultTier) return;
        this.defaultProfileId = normalizedProfile;
        this.defaultTier = normalizedTier;
        setDirty();
    }

    public static ButcherPolicyData load(CompoundTag tag, HolderLookup.Provider registries) {
        ButcherPolicyData data = new ButcherPolicyData();
        data.defaultProfileId = normalizeProfileId(tag.getString("defaultProfileId"));
        data.defaultTier = tag.contains("defaultTier") ? normalizeTier(tag.getInt("defaultTier")) : 5;

        ListTag list = tag.getList("areas", Tag.TAG_COMPOUND);
        for (Tag t : list) {
            if (!(t instanceof CompoundTag areaTag)) continue;
            data.areas.add(ButcherAreaPolicy.fromTag(areaTag));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("defaultProfileId", normalizeProfileId(defaultProfileId));
        tag.putInt("defaultTier", normalizeTier(defaultTier));

        ListTag list = new ListTag();
        for (ButcherAreaPolicy area : areas) {
            list.add(area.toTag());
        }
        tag.put("areas", list);
        return tag;
    }

    private static String normalizeProfileId(String profileId) {
        if (profileId == null || profileId.isBlank()) return DEFAULT_PROFILE_ID;
        return profileId.trim();
    }

    private static int normalizeTier(int tier) {
        return Math.max(1, Math.min(tier, 5));
    }

    public record ResolvedButcherPolicy(String profileId, int tier, String source) {}

    public record ButcherAreaPolicy(BlockPos center, int radius, String profileId, int tier) {
        public static ButcherAreaPolicy fromTag(CompoundTag tag) {
            BlockPos center = BlockPos.of(tag.getLong("center"));
            int radius = Math.max(1, tag.getInt("radius"));
            String profileId = tag.getString("profileId");
            int tier = tag.getInt("tier");
            return new ButcherAreaPolicy(center, radius, profileId, tier);
        }

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("center", center.asLong());
            tag.putInt("radius", Math.max(1, radius));
            tag.putString("profileId", normalizeProfileId(profileId));
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
