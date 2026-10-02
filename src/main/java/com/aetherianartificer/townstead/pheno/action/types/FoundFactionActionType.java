package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.politics.definition.FactionKind;
import com.aetherianartificer.townstead.politics.definition.PoliticalDefinitions;
import com.aetherianartificer.townstead.politics.order.Orders;
import com.aetherianartificer.townstead.politics.state.Faction;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;

/**
 * Founds a faction of {@code kind} at the village of the villager who leads it. The villager side
 * of the action leads; when the other side is a player, they are told and receive what the
 * founding gives. For now only kinds that hold no land (orders) can be founded this way.
 * <pre>
 * { "type": "pheno:found_faction", "kind": "townstead:hunter_order" }
 * </pre>
 */
public final class FoundFactionActionType implements ActionType {
    public static final String KEY = "pheno:found_faction";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        ResourceLocation kindId = DataPackLang.parseId(GsonHelper.getAsString(json, "kind", ""));
        if (kindId == null) return null;
        return ctx -> {
            LivingEntity entity = ctx.entity(), other = ctx.other();
            VillagerEntityMCA leader = entity instanceof VillagerEntityMCA v ? v : other instanceof VillagerEntityMCA v ? v : null;
            ServerPlayer player = entity instanceof ServerPlayer p ? p : other instanceof ServerPlayer p ? p : null;
            FactionKind kind = PoliticalDefinitions.snapshot().kind(kindId);
            if (leader == null || kind == null || kind.holdsLand() || !(leader.level() instanceof ServerLevel level)) {
                ctx.fail();
                return;
            }
            Village village = VillageManager.get(level).findNearestVillage(leader.blockPosition(), Village.MERGE_MARGIN).orElse(null);
            Faction faction = village == null ? null : Orders.found(level, village, kindId, leader.getUUID());
            if (faction == null) {
                ctx.fail();
                return;
            }
            if (player != null) {
                Orders.giveFounding(player, kind);
                player.displayClientMessage(Component.translatable("command.townstead.order.founded", faction.name(),
                        village.getName(), leader.getName()), false);
            }
        };
    }
}
