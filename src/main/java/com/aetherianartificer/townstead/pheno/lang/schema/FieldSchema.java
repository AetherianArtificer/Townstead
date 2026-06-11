package com.aetherianartificer.townstead.pheno.lang.schema;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One field of a {@link NodeSchema}: its canonical name and type, whether it is required,
 * whether it accepts a scalar that should normalize to a single-element list ({@code list}),
 * author-facing {@code aliases} that normalize to the canonical name, an optional
 * {@code deprecated} replacement note, and a doc line for generated reference.
 */
public record FieldSchema(
        String name,
        PhenoType type,
        boolean required,
        boolean list,
        List<String> aliases,
        @Nullable String deprecated,
        @Nullable String doc) {

    public static FieldSchema of(String name, PhenoType type) {
        return new FieldSchema(name, type, false, false, List.of(), null, null);
    }

    public static FieldSchema required(String name, PhenoType type) {
        return new FieldSchema(name, type, true, false, List.of(), null, null);
    }

    public FieldSchema asList() {
        return new FieldSchema(name, type, required, true, aliases, deprecated, doc);
    }

    public FieldSchema aliases(String... alias) {
        return new FieldSchema(name, type, required, list, List.of(alias), deprecated, doc);
    }

    public FieldSchema doc(String text) {
        return new FieldSchema(name, type, required, list, aliases, deprecated, text);
    }

    public FieldSchema deprecated(String replacement) {
        return new FieldSchema(name, type, required, list, aliases, replacement, doc);
    }
}
