package com.aetherianartificer.townstead.pheno.value.types;

import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.ValueType;
import com.aetherianartificer.townstead.social.BondKind;
import com.aetherianartificer.townstead.social.BondKinds;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * {@code pheno:bond_max} — how many ties of a kind may hold at once, read from
 * the bond kind's own definition. Unlimited kinds report
 * {@link Double#MAX_VALUE}, so "below the limit" is true for them without the
 * author special-casing it.
 *
 * <p>This is what keeps arity in one file: a pack that makes marriage poly edits
 * {@code max_active} and every gate that compares against this follows.</p>
 */
public final class BondMaxValueType implements ValueType {

    public static final String KEY = "pheno:bond_max";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Value parse(JsonObject json) {
        String kind = GsonHelper.getAsString(json, "kind", "");
        if (kind.isEmpty()) return null;
        return Value.subjectAware(ctx -> {
            BondKind def = BondKinds.byId(kind);
            return def.unlimited() ? Double.MAX_VALUE : def.maxActive();
        });
    }
}
