package com.aetherianartificer.townstead.pheno.lang.schema;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of {@link NodeSchema}s keyed by type key. Populated once at setup. Schemas are
 * additive: a type without a registered schema still parses and has its type resolved by the
 * validator, it just does not get field-level normalization, field checks, or generated docs
 * until a schema is added.
 */
public final class NodeSchemas {

    private static final Map<String, NodeSchema> SCHEMAS = new LinkedHashMap<>();

    private NodeSchemas() {}

    public static void register(NodeSchema schema) {
        SCHEMAS.put(schema.typeKey(), schema);
    }

    @Nullable
    public static NodeSchema get(String typeKey) {
        return SCHEMAS.get(typeKey);
    }

    public static Collection<NodeSchema> all() {
        return SCHEMAS.values();
    }

    public static int size() {
        return SCHEMAS.size();
    }
}
