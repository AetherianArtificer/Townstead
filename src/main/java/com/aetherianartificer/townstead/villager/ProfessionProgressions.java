package com.aetherianartificer.townstead.villager;

import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.profession.def.ProgressionTrack;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Resolves the {@link ProgressionSpec} for a career id from its data-pack
 * {@link ProfessionDef}. The built-in careers (farmer, butcher, shepherd, cook) ship as
 * ordinary defs inside the mod jar, so a pack overrides any progression by replacing the
 * def with the same id; there is no privileged hardcoded path. A career with no def resolves
 * to an inert spec (no tiers, no daily allowance), so a missing definition reads as missing
 * rather than silently inventing numbers.
 */
public final class ProfessionProgressions {

    /** Used only for an unrecognised career with no registered def. */
    private static final ProgressionSpec DEFAULT = new ProgressionSpec(new int[]{0}, 0, 0);

    private ProfessionProgressions() {}

    public static ProgressionSpec spec(ResourceLocation careerId) {
        return careerId == null ? DEFAULT : spec(careerId.toString());
    }

    public static ProgressionSpec spec(String careerId) {
        ProfessionDef def = findDef(careerId);
        return def != null ? fromTrack(def.progression()) : DEFAULT;
    }

    /** Matches a full id or alias exactly, or a bare legacy name by def path. */
    private static ProfessionDef findDef(String careerId) {
        if (careerId == null || careerId.isBlank()) return null;
        ProfessionDef direct = ProfessionDefs.byId(ResourceLocation.tryParse(careerId));
        if (direct != null) return direct;
        for (var entry : ProfessionDefs.all().entrySet()) {
            ResourceLocation id = entry.getKey();
            if (id.toString().equals(careerId) || id.getPath().equals(careerId)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static ProgressionSpec fromTrack(ProgressionTrack track) {
        List<Integer> thresholds = track.tierThresholds();
        int[] arr = thresholds.isEmpty() ? new int[]{0} : new int[thresholds.size()];
        for (int i = 0; i < arr.length && i < thresholds.size(); i++) {
            arr[i] = thresholds.get(i);
        }
        return new ProgressionSpec(arr, track.dailyCap(), track.maxXp());
    }
}
