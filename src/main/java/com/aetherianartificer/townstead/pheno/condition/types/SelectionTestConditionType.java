package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.pheno.selector.Selector;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.aetherianartificer.townstead.pheno.selector.Selectors;
import com.google.gson.JsonObject;

/**
 * Tests whether an {@code on} selection has any members ({@code any}) or none ({@code none}). The
 * selection's own {@code where} does the filtering, so {@code any} of {@code nearby where zombie}
 * is "is a zombie near." ({@code all} needs a universe plus a predicate and waits for that.)
 */
public final class SelectionTestConditionType implements ConditionType {

    public enum Mode { ANY, NONE }

    private final String key;
    private final Mode mode;

    public SelectionTestConditionType(String key, Mode mode) {
        this.key = key;
        this.mode = mode;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public Condition parse(JsonObject json) {
        Selector selector = Selectors.parse(json.get("on"));
        if (selector == null) return null;
        return ctx -> {
            boolean empty = selector.select(SelectorContext.of(ctx)).isEmpty();
            return mode == Mode.ANY ? !empty : empty;
        };
    }
}
