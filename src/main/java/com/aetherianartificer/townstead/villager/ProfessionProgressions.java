package com.aetherianartificer.townstead.villager;

import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.profession.def.ProgressionTrack;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Resolves the {@link ProgressionSpec} for a profession id, preferring a data-driven
 * {@link ProfessionDef} when one is registered for that id and otherwise falling back to the
 * built-in {@link ProfessionXpType}. This is how {@code ProfessionXpType} is "migrated onto"
 * the data definitions: the enum stays as the shipped default, and a datapack profession whose
 * id matches (e.g. {@code minecraft:farmer}) overrides its progression without any code change.
 */
public final class ProfessionProgressions {

    /** Used only for an unrecognised profession with no def and no built-in. */
    private static final ProgressionSpec DEFAULT = new ProgressionSpec(new int[]{0}, 0, 0);

    private ProfessionProgressions() {}

    public static ProgressionSpec spec(String professionId) {
        ProfessionDef def = findDef(professionId);
        if (def != null) return fromTrack(def.progression());
        ProfessionXpType builtin = builtin(professionId);
        return builtin != null ? builtin.spec() : DEFAULT;
    }

    /** The spec for a built-in, still preferring a same-id datapack override. */
    public static ProgressionSpec spec(ProfessionXpType type) {
        ProfessionDef def = findDef(type.id());
        return def != null ? fromTrack(def.progression()) : type.spec();
    }

    private static ProfessionDef findDef(String professionId) {
        if (professionId == null || professionId.isBlank()) return null;
        for (var entry : ProfessionDefs.all().entrySet()) {
            ResourceLocation id = entry.getKey();
            if (id.toString().equals(professionId) || id.getPath().equals(professionId)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static ProfessionXpType builtin(String professionId) {
        for (ProfessionXpType type : ProfessionXpType.values()) {
            if (type.id().equals(professionId)) return type;
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
