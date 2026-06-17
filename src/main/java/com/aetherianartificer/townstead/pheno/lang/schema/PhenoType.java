package com.aetherianartificer.townstead.pheno.lang.schema;

import com.aetherianartificer.townstead.pheno.lang.validate.NodeDomain;
import org.jetbrains.annotations.Nullable;

/**
 * The value type of a schema field. Scalars carry data ({@link #DURATION} accepts {@code "3s"}
 * or a tick count, {@link #PERCENT} accepts {@code "50%"} or a fraction, and so on); the child
 * kinds ({@link #ACTION}, {@link #CONDITION}, ...) mean the field holds a nested node of that
 * domain, which the normalizer and validator descend into.
 */
public enum PhenoType {
    BOOL,
    INT,
    FLOAT,
    STRING,
    ID,
    TAG_OR_ID,
    DURATION,
    PERCENT,
    DISTANCE,
    ANGLE,
    COLOR,
    OBJECT,
    ACTION,
    CONDITION,
    BIENTITY_CONDITION,
    BLOCK_ACTION,
    ITEM_ACTION,
    ANY;

    public boolean isChild() {
        return childDomain() != null;
    }

    /** A unit type whose author-friendly forms ("3s", "50%") normalize to a canonical number. */
    public boolean isUnit() {
        return this == DURATION || this == PERCENT || this == DISTANCE || this == ANGLE;
    }

    @Nullable
    public NodeDomain childDomain() {
        return switch (this) {
            case ACTION -> NodeDomain.ACTION;
            case CONDITION -> NodeDomain.CONDITION;
            case BIENTITY_CONDITION -> NodeDomain.BIENTITY_CONDITION;
            case BLOCK_ACTION -> NodeDomain.BLOCK_ACTION;
            case ITEM_ACTION -> NodeDomain.ITEM_ACTION;
            default -> null;
        };
    }
}
