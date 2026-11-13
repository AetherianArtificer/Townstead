package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.root.attachment.BodyParts;
import com.aetherianartificer.townstead.compat.mess.MessBridge;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/** The actions bodies answer to: marking, washing, and mending. */
public final class TraceActionTypes {

    private TraceActionTypes() {}

    /** Adds to a Trace: {@code { "type":"pheno:add_trace", "trace":"examplepack:ink", "amount":0.2 }}. */
    public static final class Add implements ActionType {
        @Override public String key() { return "pheno:add_trace"; }

        @Override
        public Action parse(JsonObject json) {
            String trace = GsonHelper.getAsString(json, "trace", "");
            float amount = GsonHelper.getAsFloat(json, "amount", 0.2f);
            if (trace.isEmpty()) return null;
            return ctx -> MessBridge.addTrace(ctx.entity(), trace, amount);
        }
    }

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

    /**
     * Washes Traces off. {@code strength} is 1 rain, 2 water, 3 a bath, or 4 soap or a cure, and
     * {@code trace} can narrow it to one: {@code { "type":"pheno:wash", "strength":4, "amount":1 }}.
     */
    public static final class Wash implements ActionType {
        @Override public String key() { return "pheno:wash"; }

        @Override
        public Action parse(JsonObject json) {
            int strength = GsonHelper.getAsInt(json, "strength", MessBridge.WASH_SOAP);
            float amount = GsonHelper.getAsFloat(json, "amount", 1f);
            String trace = GsonHelper.getAsString(json, "trace", "");
            return ctx -> MessBridge.wash(ctx.entity(), strength, amount, trace);
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
