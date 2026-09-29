package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.politics.definition.FactionKind;
import com.aetherianartificer.townstead.politics.definition.PoliticalDefinitions;
import com.aetherianartificer.townstead.politics.order.Recruiting;
import com.aetherianartificer.townstead.politics.relations.FactionMembership;
import com.aetherianartificer.townstead.politics.state.Faction;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * Puts the villager standing nearest the one asking forward to train for the order that the one
 * being asked (such as a lodge's head) belongs to. When one side is a player, they are the asker. Fails when nobody
 * suitable is near or there is no room; a player is told why.
 * <pre>
 * { "type": "pheno:enlist", "range": 6 }
 * </pre>
 */
public final class EnlistActionType implements ActionType {
    public static final String KEY = "pheno:enlist";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        double range = Math.max(1, GsonHelper.getAsDouble(json, "range", 6));
        return ctx -> {
            // Whichever side is the player is the one asking; in a story the teller is the entity.
            boolean playerAsks = !(ctx.entity() instanceof ServerPlayer) && ctx.other() instanceof ServerPlayer;
            LivingEntity asker = playerAsks ? ctx.other() : ctx.entity();
            LivingEntity asked = playerAsks ? ctx.entity() : ctx.other();
            if (asker == null || asked == null || !(asker.level() instanceof ServerLevel level)) {
                ctx.fail();
                return;
            }
            Faction order = order(level, asked);
            VillagerEntityMCA person = level.getEntitiesOfClass(VillagerEntityMCA.class, asker.getBoundingBox().inflate(range),
                            v -> v != asked && v.isAlive()).stream()
                    .min(Comparator.comparingDouble(asker::distanceToSqr)).orElse(null);
            String refused = order == null ? "recruit.townstead.refused.no_recruiting"
                    : person == null ? "recruit.townstead.refused.nobody"
                    : Recruiting.enlist(level, order, person);
            if (asker instanceof ServerPlayer player) {
                player.displayClientMessage(refused == null
                        ? Component.translatable("recruit.townstead.enlisted", person.getName(), order.name())
                        : Component.translatable(refused), false);
            }
            if (refused != null) ctx.fail();
        };
    }

    private static @Nullable Faction order(ServerLevel level, LivingEntity member) {
        PoliticalSavedData data = PoliticalSavedData.get(level.getServer());
        for (ResourceLocation id : FactionMembership.of(member)) {
            Faction faction = data.faction(id);
            FactionKind kind = faction == null ? null : PoliticalDefinitions.snapshot().kind(faction.kind());
            if (kind != null && kind.recruitment() != null) return faction;
        }
        return null;
    }
}
