package com.aetherianartificer.townstead.clothing;

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

/**
 * Composes body clothing down the root chain the way hair does: species, then ancestry, then
 * lineage, then heritage, each node adding its sets unless it replaces what came before.
 */
public final class BodyClothingResolver {

    private BodyClothingResolver() {}

    public static BodyClothing resolve(@Nullable ResourceLocation rootId, @Nullable Heritage heritage) {
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

        BodyClothing composed = BodyClothing.INHERIT
                .mergedWith(BodyClothingRegistry.species(speciesId))
                .mergedWith(BodyClothingRegistry.ancestry(ancestryId))
                .mergedWith(BodyClothingRegistry.lineage(lineageId));
        if (profile != null) composed = composed.mergedWith(BodyClothingRegistry.heritage(profile.id()));
        return composed;
    }

    /** Every entry that fits this body, from the resolved sets, in chain order. */
    public static List<ClothingEntry> fitted(@Nullable ResourceLocation rootId, @Nullable Heritage heritage) {
        List<ClothingEntry> out = new ArrayList<>();
        for (ResourceLocation setId : resolve(rootId, heritage).sets()) {
            for (ClothingEntry entry : ClothingDefs.members(setId)) {
                if (!out.contains(entry)) out.add(entry);
            }
        }
        return out;
    }
}
