package com.aetherianartificer.townstead.root.condition.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.root.ExpressedGenes;
import com.aetherianartificer.townstead.root.Heredity;
import com.aetherianartificer.townstead.root.gene.Allele;
import com.aetherianartificer.townstead.root.gene.AspectOverlay;
import com.aetherianartificer.townstead.root.gene.Gene;
import com.aetherianartificer.townstead.root.gene.GeneRegistry;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * True while the entity shows any of the named genes ({@code gene}, or a {@code genes} list), or
 * anything at all at a {@code locus}. This is how one trait can require another: a muzzle gated on
 * the coat locus shows only on someone whose coat shows, whichever lineage gave it. It reads the
 * inherited phenotype (what wins each locus, including a lone copy's penetrance), not the other
 * gene's own condition, so a condition never evaluates itself.
 *
 * <p>JSON: {@code { "type":"pheno:gene", "locus":"my_pack:coat" }} or
 * {@code { "type":"pheno:gene", "genes":["my_pack:cat/coat", "my_pack:dog/coat"] }}</p>
 */
public final class GeneConditionType implements ConditionType {

    public static final String KEY = "pheno:gene";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        Set<ResourceLocation> genes = new HashSet<>();
        if (json.has("genes") && json.get("genes").isJsonArray()) {
            JsonArray array = json.getAsJsonArray("genes");
            for (int i = 0; i < array.size(); i++) {
                ResourceLocation id = DataPackLang.parseId(array.get(i).getAsString());
                if (id != null) genes.add(id);
            }
        }
        if (json.has("gene")) {
            ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "gene", ""));
            if (id != null) genes.add(id);
        }
        ResourceLocation locus = json.has("locus") ? DataPackLang.parseId(GsonHelper.getAsString(json, "locus", "")) : null;
        if (genes.isEmpty() && locus == null) return null;
        return ctx -> ctx.entity().level().isClientSide()
                ? ClientGenes.matches(ctx.entity(), genes, locus)
                : matchesServer(ctx.entity(), genes, locus);
    }

    private static boolean matchesServer(LivingEntity entity, Set<ResourceLocation> genes, @Nullable ResourceLocation locus) {
        List<Allele> shown = Heredity.expressedAlleles(ExpressedGenes.genotypeOf(entity));
        var heritage = com.aetherianartificer.townstead.root.gene.GeneExpression.heritageOf(entity);
        if (heritage != null) shown = Heredity.withGranted(shown, Heredity.heritageAlleles(heritage));
        for (Allele allele : AspectOverlay.apply(entity, shown)) {
            if (matches(allele.geneId(), genes, locus)) return true;
        }
        return false;
    }

    private static boolean matches(ResourceLocation geneId, Set<ResourceLocation> genes, @Nullable ResourceLocation locus) {
        if (genes.contains(geneId)) return true;
        ResourceLocation canonical = GeneRegistry.canonicalId(geneId);
        if (canonical != null) {
            for (ResourceLocation wanted : genes) {
                if (canonical.equals(GeneRegistry.canonicalId(wanted))) return true;
            }
        }
        if (locus == null) return false;
        Gene gene = GeneRegistry.byId(geneId);
        return gene != null && locus.equals(Heredity.locusOf(gene));
    }

    /** Client-only store access, isolated so dedicated servers never load it. */
    private static final class ClientGenes {
        static boolean matches(LivingEntity entity, Set<ResourceLocation> genes, @Nullable ResourceLocation locus) {
            String wantedLocus = locus == null ? null : locus.toString();
            for (String id : com.aetherianartificer.townstead.client.root.RootClientStore.expressedGenes(entity)) {
                ResourceLocation geneId = ResourceLocation.tryParse(id);
                if (geneId != null && genes.contains(geneId)) return true;
                if (wantedLocus == null) continue;
                var entry = com.aetherianartificer.townstead.client.root.RootCatalogClient.gene(id);
                if (entry == null) continue;
                String entryLocus = entry.locus().isEmpty() ? id : entry.locus();
                if (wantedLocus.equals(entryLocus)) return true;
            }
            return false;
        }
    }
}
