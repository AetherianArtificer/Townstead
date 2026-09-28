package com.aetherianartificer.townstead.politics.order;

import com.aetherianartificer.townstead.culture.FactionNaming;
import com.aetherianartificer.townstead.politics.definition.FactionKind;
import com.aetherianartificer.townstead.politics.definition.PoliticalDefinitions;
import com.aetherianartificer.townstead.politics.relations.FactionMembership;
import com.aetherianartificer.townstead.politics.relations.FactionRelations;
import com.aetherianartificer.townstead.politics.state.BondInstance;
import com.aetherianartificer.townstead.politics.state.Faction;
import com.aetherianartificer.townstead.politics.state.FactionBonds;
import com.aetherianartificer.townstead.politics.state.Party;
import com.aetherianartificer.townstead.politics.state.PoliticalIds;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.politics.state.PoliticalVillageBootstrap;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Landless orders based at a town, like a hunters' lodge: founded with a leader, joined by an
 * oath, and bound to their town by an accord, so they spare its people. Membership is the
 * profession its kind names: members practice it, and nobody else does.
 */
public final class Orders {
    public static final ResourceLocation HUNTER_ORDER = ResourceLocation.tryParse("townstead:hunter_order");
    private static final ResourceLocation PROVENANCE = ResourceLocation.tryParse("townstead:lodge_founding");
    private static final int SYNC_INTERVAL = 200;

    private Orders() {}

    /** Founds an order of {@code kind} at {@code town}, led by {@code leader} when given. Null when it cannot. */
    public static @Nullable Faction found(ServerLevel level, Village town, ResourceLocation kind, @Nullable UUID leader) {
        FactionKind definition = PoliticalDefinitions.snapshot().kind(kind);
        if (definition == null) return null;
        PoliticalSavedData data = PoliticalSavedData.get(level.getServer());
        SettlementRef settlement = new SettlementRef(level.dimension().location(), town.getId());
        PoliticalVillageBootstrap.ensure(level, town);
        Faction townFaction = data.faction(settlement);
        long now = level.getGameTime();
        Faction order = new Faction(PoliticalIds.faction("townstead"), kind, "Order", level.getRandom().nextInt(0xFFFFFF), null,
                now, PROVENANCE, Faction.Status.ACTIVE, List.of(), settlement);
        data.putFaction(order);
        FactionNaming.initialize(data, order.id(), FactionNaming.generate(null, kind, "Order"));
        order = data.faction(order.id());
        if (leader != null && swear(level, order, leader)) {
            for (FactionKind.Office office : definition.offices()) {
                if (office.founder()) FactionBonds.form(data, office.bond(), FactionBonds.sides(office.bond(), order.id(), leader), PROVENANCE, now);
            }
        }
        if (townFaction != null && townFaction.active()) {
            FactionBonds.form(data, FactionRelations.ACCORD, List.of(new BondInstance.Side("ally", Party.faction(order.id())),
                    new BondInstance.Side("ally", Party.faction(townFaction.id()))), PROVENANCE, now);
        }
        FactionRelations.invalidate();
        FactionMembership.invalidate();
        return order;
    }

    /** {@code person} takes the order's oath. */
    public static boolean swear(ServerLevel level, Faction order, UUID person) {
        FactionKind definition = PoliticalDefinitions.snapshot().kind(order.kind());
        if (definition == null) return false;
        PoliticalSavedData data = PoliticalSavedData.get(level.getServer());
        ResourceLocation oath = definition.membership().bond();
        boolean formed = FactionBonds.form(data, oath, FactionBonds.sides(oath, order.id(), person), PROVENANCE,
                level.getGameTime()).formed();
        if (formed) FactionMembership.invalidate();
        return formed;
    }

    /** The active order of {@code kind} based at this settlement, or null. */
    public static @Nullable Faction at(PoliticalSavedData data, SettlementRef settlement, ResourceLocation kind) {
        for (Faction faction : data.factions()) {
            if (faction.active() && faction.kind().equals(kind) && settlement.equals(faction.home())) return faction;
        }
        return null;
    }

    /**
     * Keeps a villager's profession in step with membership: a member of an order whose kind names a
     * profession practices it; anyone who is not loses it.
     */
    public static void syncProfession(VillagerEntityMCA villager) {
        if ((villager.tickCount + villager.getId()) % SYNC_INTERVAL != 0 || !(villager.level() instanceof ServerLevel level)) return;
        PoliticalSavedData data = PoliticalSavedData.get(level.getServer());
        ResourceLocation sworn = null;
        for (ResourceLocation id : FactionMembership.of(villager)) {
            Faction faction = data.faction(id);
            FactionKind kind = faction == null ? null : PoliticalDefinitions.snapshot().kind(faction.kind());
            if (kind != null && kind.members().profession() != null) {
                sworn = kind.members().profession();
                break;
            }
        }
        ResourceLocation current = BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getProfession());
        if (sworn != null) {
            if (sworn.equals(current)) return;
            VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.getOptional(sworn).orElse(null);
            if (profession != null) villager.setProfession(profession);
        } else if (memberProfessions().contains(current)) {
            villager.setProfession(VillagerProfession.NONE);
        }
    }

    private static Set<ResourceLocation> memberProfessions() {
        Set<ResourceLocation> out = new HashSet<>();
        for (FactionKind kind : PoliticalDefinitions.snapshot().kinds()) {
            if (kind.members().profession() != null) out.add(kind.members().profession());
        }
        return out;
    }
}
