package com.aetherianartificer.townstead.pheno.capability;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Identifies what produced a {@link CapabilityContribution}: the source object's id (a gene,
 * a skill, an equipment piece) and its {@link SourceKind}. {@code detail} is an optional short
 * human note (e.g. a condition summary) shown in {@code /pheno explain}.
 */
public record Provenance(ResourceLocation source, SourceKind kind, @Nullable String detail) {

    public static Provenance gene(ResourceLocation id) {
        return new Provenance(id, SourceKind.GENE, null);
    }

    public static Provenance gene(ResourceLocation id, String detail) {
        return new Provenance(id, SourceKind.GENE, detail);
    }

    public String render() {
        return kind.label() + " " + source + (detail == null ? "" : " (" + detail + ")");
    }
}
