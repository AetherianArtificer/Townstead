package com.aetherianartificer.townstead.origin.gene;

import com.google.gson.JsonObject;

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

    // Future behavior hooks (effect application, model rendering) will be added
    // here as the gene catalogue grows; this iteration is framework + display only.
}
