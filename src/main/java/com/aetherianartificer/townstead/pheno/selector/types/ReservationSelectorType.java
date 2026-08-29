package com.aetherianartificer.townstead.pheno.selector.types;

import com.aetherianartificer.townstead.pheno.selector.Selector;
import com.aetherianartificer.townstead.pheno.selector.SelectorType;
import com.google.gson.JsonObject;

/** Selects the living entities reserved by this action execution. */
public final class ReservationSelectorType implements SelectorType {
    public static final String KEY = "pheno:reservation";

    @Override public String key() { return KEY; }

    @Override
    public Selector parse(JsonObject json) {
        return ctx -> ctx.reservations() == null || ctx.self() == null
                ? java.util.List.of() : ctx.reservations().targets(ctx.self());
    }
}
