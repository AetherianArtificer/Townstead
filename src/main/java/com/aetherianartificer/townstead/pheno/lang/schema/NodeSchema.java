package com.aetherianartificer.townstead.pheno.lang.schema;

import com.aetherianartificer.townstead.pheno.lang.validate.NodeDomain;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * The single source of truth for one node type: the registry it belongs to ({@link NodeDomain}),
 * its fields, the field that the {@code do} shorthand lowers to ({@code primaryChild}), the
 * context roles it reads, and the side it runs on. Parsing, normalization, validation, JSON
 * Schema, and generated docs all read this one object, so they cannot drift apart.
 */
public record NodeSchema(
        String typeKey,
        NodeDomain domain,
        List<FieldSchema> fields,
        @Nullable String primaryChild,
        Set<Role> acceptedContext,
        Side side,
        @Nullable String doc) {

    /** The canonical field for a given author key (its own name or one of its aliases). */
    @Nullable
    public FieldSchema fieldFor(String authorKey) {
        for (FieldSchema f : fields) {
            if (f.name().equals(authorKey)) return f;
            if (f.aliases().contains(authorKey)) return f;
        }
        return null;
    }

    @Nullable
    public FieldSchema field(String name) {
        for (FieldSchema f : fields) {
            if (f.name().equals(name)) return f;
        }
        return null;
    }

    public static Builder of(String typeKey, NodeDomain domain) {
        return new Builder(typeKey, domain);
    }

    /** Fluent builder so registrations read declaratively. */
    public static final class Builder {
        private final String typeKey;
        private final NodeDomain domain;
        private final java.util.List<FieldSchema> fields = new java.util.ArrayList<>();
        private String primaryChild;
        private java.util.Set<Role> context = java.util.Set.of();
        private Side side = Side.SERVER;
        private String doc;

        private Builder(String typeKey, NodeDomain domain) {
            this.typeKey = typeKey;
            this.domain = domain;
        }

        public Builder field(FieldSchema field) {
            fields.add(field);
            return this;
        }

        public Builder primaryChild(String name) {
            this.primaryChild = name;
            return this;
        }

        public Builder context(Role... roles) {
            this.context = Set.of(roles);
            return this;
        }

        public Builder side(Side side) {
            this.side = side;
            return this;
        }

        public Builder doc(String doc) {
            this.doc = doc;
            return this;
        }

        public NodeSchema build() {
            return new NodeSchema(typeKey, domain, List.copyOf(fields), primaryChild, context, side, doc);
        }
    }
}
