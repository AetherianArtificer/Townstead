package com.aetherianartificer.townstead.ritual;

import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.politics.definition.FactionKind;
import com.aetherianartificer.townstead.politics.definition.PoliticalDefinitions;
import com.aetherianartificer.townstead.politics.order.OrderAltars;
import com.aetherianartificer.townstead.politics.relations.FactionMembership;
import com.aetherianartificer.townstead.politics.state.Faction;
import com.aetherianartificer.townstead.politics.state.FactionBonds;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Starts and runs rituals. A ritual is held by the order at the candidate's village whose kind has
 * the ritual's officiant office, at that order's altar, led by whoever holds the office. Order
 * members nearby gather as witnesses.
 */
public final class RitualService {
    private static final double OFFICIANT_RANGE = 32;
    private static final Map<GlobalPos, RitualSession> SESSIONS = new HashMap<>();

    private RitualService() {}

    /** Starts {@code ritualId} for {@code candidate}. Returns null on success, else a lang key saying why not. */
    public static @Nullable String start(ServerLevel level, ResourceLocation ritualId, LivingEntity candidate) {
        RitualDefinition ritual = Rituals.get(ritualId);
        if (ritual == null) return "ritual.townstead.refused.unknown";
        if (busy(candidate)) return "ritual.townstead.refused.busy";
        Village village = VillageManager.get(level).findNearestVillage(candidate.blockPosition(), Village.MERGE_MARGIN).orElse(null);
        if (village == null) return "ritual.townstead.refused.no_village";
        PoliticalSavedData data = PoliticalSavedData.get(level.getServer());
        Faction order = order(data, new SettlementRef(level.dimension().location(), village.getId()), ritual);
        if (order == null) return "ritual.townstead.refused.no_order";
        if (ritual.outcome().joinOrder() && FactionMembership.of(candidate).contains(order.id())) {
            return "ritual.townstead.refused.already_sworn";
        }
        if (!ritual.candidateCondition().test(new ConditionContext(candidate))) return "ritual.townstead.refused.unfit";
        GlobalPos altar = OrderAltars.get(level.getServer()).altar(order.id());
        if (altar == null || !altar.dimension().equals(level.dimension())
                || !ritual.placeBlock().equals(BuiltInRegistries.BLOCK.getKey(level.getBlockState(altar.pos()).getBlock()))) {
            return "ritual.townstead.refused.no_altar";
        }
        if (SESSIONS.containsKey(altar)) return "ritual.townstead.refused.altar_busy";
        if (candidate.distanceToSqr(altar.pos().getCenter()) > ritual.radius() * ritual.radius()) {
            return "ritual.townstead.refused.too_far";
        }
        LivingEntity officiant = officiant(level, data, order, ritual, altar.pos(), candidate);
        if (officiant == null) return "ritual.townstead.refused.no_officiant";
        List<LivingEntity> witnesses = witnesses(level, order, ritual, altar.pos(), candidate, officiant);
        SESSIONS.put(altar, new RitualSession(ritual, level, altar.pos(), order, officiant, candidate, witnesses));
        if (candidate instanceof net.minecraft.server.level.ServerPlayer player) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("ritual.townstead.called"), false);
        }
        return null;
    }

    public static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        SESSIONS.values().removeIf(session -> {
            session.tick();
            return session.over();
        });
    }

    /** Breaks off every ritual, returning offerings. For server stop. */
    public static void clear() {
        SESSIONS.values().forEach(session -> session.fail("ritual.townstead.broken.absent"));
        SESSIONS.clear();
    }

    public static boolean busy(LivingEntity entity) {
        for (RitualSession session : SESSIONS.values()) if (session.involves(entity)) return true;
        return false;
    }

    private static @Nullable Faction order(PoliticalSavedData data, SettlementRef settlement, RitualDefinition ritual) {
        for (Faction faction : data.factions()) {
            if (!faction.active() || !settlement.equals(faction.home())) continue;
            FactionKind kind = PoliticalDefinitions.snapshot().kind(faction.kind());
            if (kind == null) continue;
            if (ritual.officiantOffice() == null) return faction;
            for (FactionKind.Office office : kind.offices()) {
                if (office.bond().equals(ritual.officiantOffice())) return faction;
            }
        }
        return null;
    }

    private static @Nullable LivingEntity officiant(ServerLevel level, PoliticalSavedData data, Faction order,
                                                    RitualDefinition ritual, BlockPos altar, LivingEntity candidate) {
        if (ritual.officiantOffice() == null) return null;
        for (UUID holder : FactionBonds.holders(data, order.id(), ritual.officiantOffice())) {
            Entity entity = level.getEntity(holder);
            if (entity instanceof LivingEntity living && living != candidate && living.isAlive() && !busy(living)
                    && living.distanceToSqr(altar.getCenter()) <= OFFICIANT_RANGE * OFFICIANT_RANGE) {
                return living;
            }
        }
        return null;
    }

    private static List<LivingEntity> witnesses(ServerLevel level, Faction order, RitualDefinition ritual, BlockPos altar,
                                                LivingEntity candidate, LivingEntity officiant) {
        if (ritual.maxWitnesses() <= 0) return List.of();
        List<LivingEntity> out = new ArrayList<>();
        AABB area = new AABB(altar).inflate(ritual.radius());
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area, e -> e != candidate && e != officiant
                && e.isAlive() && !busy(e) && FactionMembership.of(e).contains(order.id()))) {
            out.add(entity);
        }
        out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(altar.getCenter())));
        return out.size() > ritual.maxWitnesses() ? out.subList(0, ritual.maxWitnesses()) : out;
    }
}
