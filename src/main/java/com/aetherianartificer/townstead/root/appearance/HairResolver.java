package com.aetherianartificer.townstead.root.appearance;

import com.aetherianartificer.townstead.root.Ancestry;
import com.aetherianartificer.townstead.root.AncestryRegistry;
import com.aetherianartificer.townstead.root.Heritage;
import com.aetherianartificer.townstead.root.HeritageProfile;
import com.aetherianartificer.townstead.root.HeritageRegistry;
import com.aetherianartificer.townstead.root.Lineage;
import com.aetherianartificer.townstead.root.LineageRegistry;
import com.aetherianartificer.townstead.root.Root;
import com.aetherianartificer.townstead.root.RootRegistry;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Resolves MCA hair most-specific-first: heritage, lineage, ancestry, species, then MCA default. */
public final class HairResolver {

    private HairResolver() {}

    public static boolean enabled(@Nullable ResourceLocation rootId, @Nullable Heritage heritage) {
        return resolve(rootId, heritage).enabled();
    }

    /** Resolve each property independently, allowing a child node to override availability but inherit colors. */
    public static HairSettings resolve(@Nullable ResourceLocation rootId, @Nullable Heritage heritage) {
        HeritageProfile profile = HeritageRegistry.bestMatch(heritage);
        Root root = RootRegistry.resolveOrDefault(rootId);
        ResourceLocation lineageId = root == null ? null : root.lineage();
        ResourceLocation ancestryId = root == null ? null : root.ancestry();
        if (ancestryId == null && lineageId != null) {
            Lineage lineage = LineageRegistry.byId(lineageId);
            if (lineage != null) ancestryId = lineage.ancestry();
        }
        ResourceLocation speciesId = RootRegistry.effectiveSpecies(rootId);
        if (speciesId == null && ancestryId != null) {
            Ancestry ancestry = AncestryRegistry.byId(ancestryId);
            if (ancestry != null) speciesId = ancestry.species();
        }

        List<HairPolicy> policies = new ArrayList<>(4);
        policies.add(profile == null ? HairPolicy.INHERIT : HairPolicyRegistry.heritage(profile.id()));
        policies.add(HairPolicyRegistry.lineage(lineageId));
        policies.add(HairPolicyRegistry.ancestry(ancestryId));
        policies.add(HairPolicyRegistry.species(speciesId));

        Boolean enabled = null;
        List<HairColorRange> ranges = List.of();
        List<HairColorChoice> colors = List.of();
        List<HairGradient> gradients = List.of();
        for (HairPolicy policy : policies) {
            if (enabled == null) {
                // Declaring colors necessarily opts this node back into MCA hair unless it says false.
                enabled = policy.enabled() != null ? policy.enabled()
                        : policy.colorRanges().isEmpty() && policy.colors().isEmpty() && policy.gradients().isEmpty()
                                ? null : Boolean.TRUE;
            }
            if (ranges.isEmpty() && !policy.colorRanges().isEmpty()) ranges = policy.colorRanges();
            if (colors.isEmpty() && !policy.colors().isEmpty()) colors = policy.colors();
            if (gradients.isEmpty() && !policy.gradients().isEmpty()) gradients = policy.gradients();
        }
        return new HairSettings(enabled == null || enabled, ranges, colors, gradients);
    }
}
