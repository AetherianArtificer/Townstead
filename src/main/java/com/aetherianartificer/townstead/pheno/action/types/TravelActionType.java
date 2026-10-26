package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.journey.Companions;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;

/**
 * {@code pheno:travel_with}: the villager side of the action travels with the player until
 * {@code pheno:travel_end}. They keep up, follow through portals, and keep their home. With
 * {@code leave_pets}, their pets stay behind. Ending away from home, they walk back on their own.
 * <pre>
 * { "type": "pheno:travel_with", "leave_pets": true }
 * { "type": "pheno:travel_end" }
 * </pre>
 */
public final class TravelActionType implements ActionType {
    public static final String WITH = "pheno:travel_with";
    public static final String END = "pheno:travel_end";
    private final boolean start;

    public TravelActionType(boolean start) {
        this.start = start;
    }

    @Override
    public String key() {
        return start ? WITH : END;
    }

    @Override
    public Action parse(JsonObject json) {
        boolean leavePets = GsonHelper.getAsBoolean(json, "leave_pets", false);
        return ctx -> {
            VillagerEntityMCA villager = ctx.entity() instanceof VillagerEntityMCA v ? v
                    : ctx.other() instanceof VillagerEntityMCA v ? v : null;
            ServerPlayer player = ctx.other() instanceof ServerPlayer p ? p : ctx.entity() instanceof ServerPlayer p ? p : null;
            if (villager == null) {
                ctx.fail();
                return;
            }
            if (!start) {
                Companions.end(villager);
                return;
            }
            if (player == null) {
                ctx.fail();
                return;
            }
            Companions.start(villager, player, leavePets);
        };
    }
}
