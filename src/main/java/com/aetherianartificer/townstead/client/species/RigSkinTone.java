package com.aetherianartificer.townstead.client.species;

import com.aetherianartificer.townstead.client.origin.OriginCatalogClient;
import com.aetherianartificer.townstead.client.origin.OriginClientStore;
import com.aetherianartificer.townstead.client.skin.SkinBlend;
import com.aetherianartificer.townstead.client.skin.SkinTintRegistry;
import com.aetherianartificer.townstead.origin.GeneCatalogEntry;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.client.resources.ColorPalette;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import com.aetherianartificer.townstead.origin.OriginCatalogEntry;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Per-entity skin tone for an alternate rig, reusing the exact villager skin pipeline so nothing
 * forks: MCA's per-villager skin value (melanin×hemoglobin, the thing its skin selector controls) is
 * the individualization source, and the origin's authored {@code skin_tone} gene is the palette
 * shift, combined through {@link SkinBlend} just like the villager skin-layer mixin, but applied to
 * the rig texture instead of the suppressed MCA skin layer.
 *
 * <p>Returns an ARGB multiply colour for {@code SpeciesRigLayer}'s {@code renderToBuffer}. With no
 * authored {@code skin_tone} it is {@code 0xFFFFFFFF} (untinted, the rig's own texture), so a
 * species opts into tone individualization simply by carrying a {@code skin_tone} gene.</p>
 */
public final class RigSkinTone {

    private RigSkinTone() {}

    /** The ARGB multiply colour to render this entity's rig with (0xFFFFFFFF = untinted). */
    public static int forEntity(LivingEntity entity) {
        // Palette path: if the origin has a skin_tone gene with a tinted-variant palette, the variant
        // is the HUE and MCA's skin selector shades it within a bounded range (never black/white), so
        // a dark skin gives a dark tone and each shifts live as the selector moves.
        int hue = paletteHue(entity);
        if (hue >= 0) {
            int mcaSkin = mcaSkinColor(entity) & 0xFFFFFF;
            return 0xFF000000 | SkinBlend.shadeByLuma(hue, mcaSkin);
        }
        // Biome path (single-tint skin_tone): MCA's per-villager climate skin shifted by the palette.
        OptionalInt tint = SkinTintRegistry.resolve(entity);
        if (tint.isEmpty()) return 0xFFFFFFFF;
        int mcaSkin = mcaSkinColor(entity) & 0xFFFFFF;
        return 0xFF000000 | SkinBlend.blend(mcaSkin, tint.getAsInt());
    }

    /** This entity's authored skin-tone palette hue ({@code 0xRRGGBB}), or {@code -1} if its origin has none. */
    public static int paletteHue(LivingEntity entity) {
        GeneCatalogEntry palette = paletteGene(entity);
        return palette == null ? -1 : paletteTint(entity, palette);
    }

    /** The origin's {@code skin_tone} gene that carries a tinted-variant palette, or null if none. */
    public static GeneCatalogEntry paletteGene(LivingEntity entity) {
        String originId = OriginClientStore.resolve(entity);
        if (originId.isEmpty()) return null;
        OriginCatalogEntry origin = OriginCatalogClient.origin(originId);
        if (origin == null) return null;
        for (OriginCatalogEntry.Inherited inherited : origin.inheritedGenes()) {
            GeneCatalogEntry gene = OriginCatalogClient.gene(inherited.geneId());
            if (gene == null) continue;
            for (GeneCatalogEntry.Variant variant : gene.variants()) {
                if (variant.tint() >= 0) return gene;
            }
        }
        return null;
    }

    /**
     * This entity's tone from the palette: its synced genetic roll when present (re-rolls on origin
     * selection, inherited), else a stable weighted pick by UUID so an entity that hasn't synced its
     * allele yet (a pre-existing villager, the editor preview) is still varied rather than blank.
     */
    private static int paletteTint(LivingEntity entity, GeneCatalogEntry palette) {
        String rolled = OriginClientStore.resolveCarriedVariant(entity, palette.id());
        if (rolled != null && !rolled.isEmpty()) {
            for (GeneCatalogEntry.Variant variant : palette.variants()) {
                if (variant.id().equals(rolled) && variant.tint() >= 0) return variant.tint();
            }
        }
        List<Integer> pool = new ArrayList<>();
        for (GeneCatalogEntry.Variant variant : palette.variants()) {
            if (variant.tint() < 0) continue;
            for (int i = 0; i < Math.max(1, variant.weight()); i++) pool.add(variant.tint());
        }
        if (pool.isEmpty()) return -1;
        return pool.get(Math.floorMod(entity.getUUID().hashCode(), pool.size()));
    }

    /**
     * MCA's per-villager skin colour, the same melanin×hemoglobin value its SkinLayer renders.
     * {@code ColorPalette.getColor} returns a packed int on NeoForge and an r/g/b float[] on Forge.
     */
    private static int mcaSkinColor(LivingEntity entity) {
        VillagerLike<?> villager = CommonVillagerModel.getVillager(entity);
        Genetics genetics = villager.getGenetics();
        float albinism = villager.getTraits().hasTrait(Traits.ALBINISM) ? 0.1f : 1.0f;
        float melanin = genetics.getGene(Genetics.MELANIN) * albinism;
        float hemoglobin = genetics.getGene(Genetics.HEMOGLOBIN) * albinism;
        float infection = villager.getInfectionProgress();
        //? if neoforge {
        return ColorPalette.SKIN.getColor(melanin, hemoglobin, infection) & 0xFFFFFF;
        //?} else {
        /*float[] c = ColorPalette.SKIN.getColor(melanin, hemoglobin, infection);
        return (Math.round(c[0] * 255f) << 16) | (Math.round(c[1] * 255f) << 8) | Math.round(c[2] * 255f);
        *///?}
    }
}
