package com.aetherianartificer.townstead.root.condition.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.root.ExpressedGenes;
import com.aetherianartificer.townstead.root.Heredity;
import com.aetherianartificer.townstead.root.gene.Allele;
import com.aetherianartificer.townstead.root.gene.AllelePayload;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashSet;
import java.util.Set;

/**
 * True while the entity's expressed allele of a variant {@code gene} carries one of
 * the named variants ({@code variant} string or {@code is} list). This is what lets
 * a style choice drive powers: a revelation gene's rolled form gating which aura
 * fires, a tusk style gating a damage bonus. Genetics-specific (reads the genotype),
 * so it lives beside {@code pheno:toggled} rather than in the shared layer.
 *
 * <p>JSON: {@code { "type":"pheno:variant",
 * "gene":"my_pack:revelation_form", "variant":"searing" }}</p>
 */
public final class VariantConditionType implements ConditionType {

    public static final String KEY = "pheno:variant";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        ResourceLocation geneId = DataPackLang.parseId(GsonHelper.getAsString(json, "gene", ""));
        if (geneId == null) return null;
        Set<String> wanted = new HashSet<>();
        if (json.has("is") && json.get("is").isJsonArray()) {
            JsonArray array = json.getAsJsonArray("is");
            for (int i = 0; i < array.size(); i++) wanted.add(array.get(i).getAsString());
        } else {
            String single = GsonHelper.getAsString(json, "variant", "");
            if (!single.isEmpty()) wanted.add(single);
        }
        if (wanted.isEmpty()) return null;
        // Side-aware like pheno:toggled: the genotype is server truth; clients
        // (attachment gates, pose conditions) read the synced carried-variant store.
        return ctx -> ctx.entity().level().isClientSide()
                ? ClientVariantState.matches(ctx.entity(), geneId, wanted)
                : matchesServer(ctx.entity(), geneId, wanted);
    }

    private static boolean matchesServer(LivingEntity entity, ResourceLocation geneId, Set<String> wanted) {
        for (Allele allele : com.aetherianartificer.townstead.root.gene.AspectOverlay.apply(entity,
                Heredity.expressedAlleles(ExpressedGenes.genotypeOf(entity)))) {
            if (geneId.equals(allele.geneId())) {
                String raw = allele.variantId() == null ? "" : allele.variantId();
                return wanted.contains(AllelePayload.parse(raw).variant());
            }
        }
        return false;
    }

    /** Client-only store access, isolated so dedicated servers never load it. */
    private static final class ClientVariantState {
        static boolean matches(LivingEntity entity, ResourceLocation geneId, Set<String> wanted) {
            String carried = com.aetherianartificer.townstead.client.root.RootClientStore
                    .resolveCarriedVariant(entity, geneId.toString());
            return wanted.contains(AllelePayload.parse(carried).variant());
        }
    }
}
