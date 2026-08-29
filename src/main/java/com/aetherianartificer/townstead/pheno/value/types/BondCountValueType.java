package com.aetherianartificer.townstead.pheno.value.types;

import com.aetherianartificer.townstead.pheno.condition.PhenoSubject;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.ValueType;
import com.aetherianartificer.townstead.social.Bonds;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;

/**
 * {@code pheno:bond_count} — how many ties of a kind someone holds, as a number
 * usable anywhere a number is. {@code active} defaults to true, so it counts
 * ties that still hold rather than every one ever formed.
 *
 * <p>Subject-capable: a fabricated life counts its own ties as it accumulates.</p>
 */
public final class BondCountValueType implements ValueType {

    public static final String KEY = "pheno:bond_count";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Value parse(JsonObject json) {
        String kind = GsonHelper.getAsString(json, "kind", "");
        if (kind.isEmpty()) return null;
        boolean activeOnly = GsonHelper.getAsBoolean(json, "active", true);
        return Value.subjectAware(ctx -> {
            PhenoSubject subject = ctx.subject();
            if (subject != null) return subject.bonds().count(kind, activeOnly);
            LivingEntity self = ctx.self();
            return self == null ? 0 : Bonds.of(self).count(kind, activeOnly);
        });
    }
}
