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
    /** The same schemas keyed by domain too, since one type id can name nodes in several domains. */
    private static final Map<String, NodeSchema> BY_DOMAIN = new LinkedHashMap<>();

    private NodeSchemas() {}

    public static void register(NodeSchema schema) {
        SCHEMAS.put(schema.typeKey(), schema);
        BY_DOMAIN.put(schema.domain() + "|" + schema.typeKey(), schema);
    }

    /** The schema for {@code typeKey} in {@code domain}; null when that domain declares none. */
    @Nullable
    public static NodeSchema get(String typeKey, com.aetherianartificer.townstead.pheno.lang.validate.NodeDomain domain) {
        return BY_DOMAIN.get(domain + "|" + typeKey);
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
