package com.aetherianartificer.townstead.origin.gene;

import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Pluggable gene-type contract — the Java backbone of the gene system, modeled
 * on the reaction system's {@code TriggerType} (and Apoli's power factories).
 * A gene's JSON names a type via its {@code "type"} field; that type parses the
 * config and (in future phases) owns the gene's effect/render behavior.
 *
 * <p>Register implementations once at startup via {@link GeneTypes#register}.</p>
 */
public interface GeneType {

    /** Wire key matched against a gene JSON's {@code "type"} (e.g. {@code townstead_origins:scaled_part}). */
    String key();

    /** Parse this type's config from the gene JSON; return {@code null} if invalid. */
    GeneInstance parse(JsonObject json);

    /**
     * Same as {@link #parse(JsonObject)} but with the data-pack lang index, for
     * types that carry localized labels in their config (e.g. life-cycle stages).
     * Defaults to the lang-free overload; override when labels need resolving.
     */
    default GeneInstance parse(JsonObject json, Map<String, String> lang) {
        return parse(json);
    }

    /**
     * Parse one variant of a multi-variant gene. {@code variantId} is the variant's key in
     * the gene's {@code variants} map; types that draw on a shared catalog (e.g. chronotype)
     * use it to resolve a weight-only reference. Defaults to {@link #parse(JsonObject, Map)}.
     */
    default GeneInstance parseVariant(String variantId, JsonObject json, Map<String, String> lang) {
        return parse(json, lang);
    }

    /**
     * A type-supplied fallback label for a variant id (e.g. a shared catalog label), used
     * when the variant entry has no {@code label} of its own. {@code null} = no fallback.
     */
    @Nullable
    default Component variantLabel(String variantId) {
        return null;
    }

    /**
     * A type-derived locus for a gene that declares none, so genes of this type
     * that occupy the same conceptual slot collapse and inherit as alleles of one
     * another (e.g. all body-metric genes targeting MCA's {@code size} share a
     * locus). {@code null} = the gene's own id is its locus. The instance passed is
     * the gene's first variant's parsed config.
     */
    @Nullable
    default net.minecraft.resources.ResourceLocation defaultLocus(GeneInstance instance) {
        return null;
    }

    // Future behavior hooks (effect application, model rendering) will be added
    // here as the gene catalogue grows; this iteration is framework + display only.
}
