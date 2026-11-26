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
     * {@code { "type":"pheno:spray_liquid", "liquid":"examplepack:ink", "amount":4 }}. Where something landed,
     * {@code spread} fans that many splashes out around it ({@code rise} is how steeply); {@code "from":"eyes"} throws
     * it from the mouth rather than the chest; {@code pattern} names how it's thrown ({@code bitofamess:high_pressure}
     * for a jet, {@code bitofamess:arc} for a fan) instead of the liquid's own pick.
     */
    public static final class SprayLiquid implements ActionType {
        @Override public String key() { return "pheno:spray_liquid"; }

        @Override
        public Action parse(JsonObject json) {
            String liquid = GsonHelper.getAsString(json, "liquid", "");
            float amount = GsonHelper.getAsFloat(json, "amount", 4f);
            // How many splashes fan out around a landing, and how steeply they rise.
            int spread = Math.max(1, GsonHelper.getAsInt(json, "spread", 1));
            double rise = GsonHelper.getAsDouble(json, "rise", spread > 1 ? 0.5 : 1.0);
            boolean fromMouth = "eyes".equals(GsonHelper.getAsString(json, "from", "body"));
            // A named way of throwing it (a jet, a fan); empty lets the liquid pick its own.
            String pattern = GsonHelper.getAsString(json, "pattern", "");
            if (liquid.isEmpty()) return null;
            return ctx -> {
                var at = ctx.at();
                if (at != null) {
                    // A splash where it hit: up and outward, and then it falls back down. Spread over a ring of
                    // directions, it runs out across the ground all round.
                    var from = at.add(0.0, 0.2, 0.0);
                    if (spread == 1) {
                        MessBridge.spray(ctx.entity(), liquid, from, new net.minecraft.world.phys.Vec3(0.0, 1.0, 0.0), amount, pattern);
                        return;
                    }
                    double turn = ctx.entity().getRandom().nextDouble() * Math.PI * 2;
                    for (int i = 0; i < spread; i++) {
                        double a = turn + i * Math.PI * 2 / spread;
                        MessBridge.spray(ctx.entity(), liquid, from,
                                new net.minecraft.world.phys.Vec3(Math.cos(a), rise, Math.sin(a)), amount, pattern);
                    }
                } else {
                    var from = fromMouth ? ctx.entity().getEyePosition().add(ctx.entity().getLookAngle().scale(0.3))
                            : ctx.entity().position().add(0.0, ctx.entity().getBbHeight() * 0.6, 0.0);
                    MessBridge.spray(ctx.entity(), liquid, from, ctx.entity().getLookAngle(), amount, pattern);
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
