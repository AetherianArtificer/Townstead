package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.root.attachment.BodyParts;
import com.aetherianartificer.townstead.compat.mess.MessBridge;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/** The actions bodies answer to: spraying a liquid, and mending. */
public final class TraceActionTypes {

    private TraceActionTypes() {}

    /**
     * Throws a liquid from the body, the way the actor is facing, or from where something landed:
     * {@code { "type":"pheno:spray_liquid", "liquid":"examplepack:ink", "amount":4 }}.
     */
    public static final class SprayLiquid implements ActionType {
        @Override public String key() { return "pheno:spray_liquid"; }

        @Override
        public Action parse(JsonObject json) {
            String liquid = GsonHelper.getAsString(json, "liquid", "");
            float amount = GsonHelper.getAsFloat(json, "amount", 4f);
            if (liquid.isEmpty()) return null;
            return ctx -> {
                var at = ctx.at();
                if (at != null) {
                    // A splash where it hit: up and outward, and then it falls back down.
                    MessBridge.spray(ctx.entity(), liquid, at.add(0.0, 0.2, 0.0),
                            new net.minecraft.world.phys.Vec3(0.0, 1.0, 0.0), amount);
                } else {
                    MessBridge.spray(ctx.entity(), liquid,
                            ctx.entity().position().add(0.0, ctx.entity().getBbHeight() * 0.6, 0.0),
                            ctx.entity().getLookAngle(), amount);
                }
            };
        }
    }

    /** Mends a part now, or every part: {@code { "type":"pheno:restore_part", "attachment":"examplepack:wing" }}. */
    public static final class RestorePart implements ActionType {
        @Override public String key() { return "pheno:restore_part"; }

        @Override
        public Action parse(JsonObject json) {
            String attachment = GsonHelper.getAsString(json, "attachment", "");
            return ctx -> BodyParts.restore(ctx.entity(), attachment);
        }
    }
}
