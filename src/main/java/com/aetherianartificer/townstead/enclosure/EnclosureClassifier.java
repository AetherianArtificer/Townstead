package com.aetherianartificer.townstead.enclosure;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Picks the best {@link EnclosureTypeIndex.Spec} for a detected
 * {@link Enclosure}. A spec matches when all perimeter and interior
 * requirements are satisfied. Ties break on priority (higher wins) then
 * on interior-signature specificity (more required signature blocks wins,
 * so a slaughter_pen with its blood_grate outranks a generic pen when both
 * would otherwise match).
 */
public final class EnclosureClassifier {
    private EnclosureClassifier() {}

    public static @Nullable EnclosureTypeIndex.Spec classify(Enclosure enclosure) {
        if (enclosure == null) return null;
        EnclosureTypeIndex.Spec best = null;
        for (EnclosureTypeIndex.Spec spec : EnclosureTypeIndex.all()) {
            if (!matches(spec, enclosure)) continue;
            if (best == null || isBetter(spec, best)) best = spec;
        }
        return best;
    }

    private static boolean matches(EnclosureTypeIndex.Spec spec, Enclosure enc) {
        int interiorSize = enc.interiorSize();
        if (interiorSize < spec.minInterior() || interiorSize > spec.maxInterior()) return false;
        if (enc.fenceCount() < spec.fencesRequired()) return false;
        if (enc.fenceGateCount() < spec.fenceGatesRequired()) return false;
        if (enc.wallCount() < spec.wallsRequired()) return false;
        for (EnclosureTypeIndex.Requirement req : spec.interiorSignatures()) {
            if (countMatching(req, enc.interiorContent()) < req.count()) return false;
        }
        return true;
    }

    private static int countMatching(EnclosureTypeIndex.Requirement req, Map<String, Integer> content) {
        int total = 0;
        for (Map.Entry<String, Integer> e : content.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(e.getKey());
            if (id == null) continue;
            Block block = BuiltInRegistries.BLOCK.get(id);
            BlockState state = block.defaultBlockState();
            if (req.matches(state)) total += e.getValue();
        }
        return total;
    }

    private static boolean isBetter(EnclosureTypeIndex.Spec candidate, EnclosureTypeIndex.Spec current) {
        if (candidate.priority() != current.priority()) {
            return candidate.priority() > current.priority();
        }
        return candidate.interiorSignatures().size() > current.interiorSignatures().size();
    }
}
