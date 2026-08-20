package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Comparison;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.pheno.condition.PhenoSubject;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.Values;
import com.aetherianartificer.townstead.social.Bonds;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;

/**
 * {@code pheno:bond_count} — how many ties of a kind someone holds, compared
 * against {@code compare_to} using {@code comparison}. Spelled like its
 * collection siblings ({@code pheno:collection_count}, {@code collection_size}),
 * because it answers the same shape of question, but bonds are their own
 * concept rather than a collection store.
 *
 * <pre>
 *   { "type": "pheno:bond_count", "kind": "townstead:marriage",
 *     "comparison": "&lt;",
 *     "compare_to": { "type": "pheno:bond_max", "kind": "townstead:marriage" } }
 * </pre>
 *
 * {@code compare_to} is a value, so it may be a literal or another number: a
 * kind's own {@code max_active} is what keeps arity in one file, and a pack that
 * makes marriage poly changes nothing else. {@code active} defaults to true, so
 * ties that have ended stop counting; {@code active: false} counts every one
 * ever formed, which is how "has been married before" differs from "is married".
 *
 * <p>Subject-capable: a fabricated life counts its own ties as it accumulates.</p>
 */
public final class BondCountConditionType implements ConditionType {

    public static final String KEY = "pheno:bond_count";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        String kind = GsonHelper.getAsString(json, "kind", "");
        if (kind.isEmpty()) return null;
        boolean activeOnly = GsonHelper.getAsBoolean(json, "active", true);
        Comparison comparison = Comparison.parse(GsonHelper.getAsString(json, "comparison", ">="));
        Value compareTo = json.has("compare_to")
                ? Values.parse(json.get("compare_to")) : Values.constant(1);
        if (compareTo == null) return null;
        boolean supportsSubject = compareTo.supportsSubject();
        return new Condition() {
            @Override
            public boolean test(ConditionContext ctx) {
                return comparison.compare(count(ctx, kind, activeOnly),
                        compareTo.get(SelectorContext.of(ctx)));
            }

            @Override
            public boolean supportsSubject() {
                return supportsSubject;
            }
        };
    }

    private static int count(ConditionContext ctx, String kind, boolean activeOnly) {
        PhenoSubject subject = ctx.subject();
        if (subject != null) return subject.bonds().count(kind, activeOnly);
        LivingEntity entity = ctx.entity();
        return entity == null ? 0 : Bonds.of(entity).count(kind, activeOnly);
    }
}
