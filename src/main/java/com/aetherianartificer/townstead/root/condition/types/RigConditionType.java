package com.aetherianartificer.townstead.root.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.root.rig.ServerRig;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * True when the entity's body is the given {@code rig} id, or, with {@code "rig":"none"}, when it
 * renders on MCA's own humanoid body with no Townstead rig. Lets authored looks that assume a
 * humanoid head skip creatures whose Root authors their own.
 */
public final class RigConditionType implements ConditionType {

    public static final String KEY = "pheno:rig";
    private static final String NONE = "none";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        String target = GsonHelper.getAsString(json, "rig", "").trim();
        if (target.isEmpty()) return null;
        return ctx -> {
            String rig = ServerRig.rigIdFor(ctx.entity());
            // Species that keep MCA's body name it as mca:villager rather than leaving it empty.
            boolean none = rig == null || rig.isEmpty() || rig.equals(com.aetherianartificer.townstead.root.Rig.VILLAGER.base());
            return NONE.equals(target) ? none : rig != null && rig.equals(target);
        };
    }
}
