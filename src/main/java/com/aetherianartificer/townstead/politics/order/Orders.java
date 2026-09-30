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

    /** Hands the player who helped found an order what its founding gives and teaches. */
    public static void giveFounding(net.minecraft.server.level.ServerPlayer player, FactionKind kind) {
        for (ResourceLocation gift : kind.founding().gifts()) {
            BuiltInRegistries.ITEM.getOptional(gift).ifPresent(item -> {
                net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item);
                if (!player.getInventory().add(stack)) player.drop(stack, false);
            });
        }
        if (!kind.founding().teaches().isEmpty()) {
            //? if >=1.21 {
            player.awardRecipesByKey(kind.founding().teaches());
            //?} else {
            /*player.awardRecipesByKey(kind.founding().teaches().toArray(new ResourceLocation[0]));
            *///?}
        }
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
     * profession practices it, and so does someone training to join one; anyone else loses it.
     */
    public static void syncProfession(VillagerEntityMCA villager) {
        if ((villager.tickCount + villager.getId()) % SYNC_INTERVAL != 0 || !(villager.level() instanceof ServerLevel level)) return;
        PoliticalSavedData data = PoliticalSavedData.get(level.getServer());
        FactionKind.Members sworn = null;
        for (ResourceLocation id : FactionMembership.of(villager)) {
            Faction faction = data.faction(id);
            FactionKind kind = faction == null ? null : PoliticalDefinitions.snapshot().kind(faction.kind());
            if (kind != null && kind.members().profession() != null) {
                sworn = kind.members();
                break;
            }
        }
        if (sworn == null) sworn = trainingFor(level, data, villager);
        ResourceLocation current = BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getProfession());
        if (sworn != null) {
            if (sworn.profession().equals(current)) return;
            VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.getOptional(sworn.profession()).orElse(null);
            if (profession == null) return;
            villager.setProfession(profession);
            if (sworn.shift() != null) takeShift(level, villager, sworn.shift());
        } else if (memberProfessions().contains(current)) {
            villager.setProfession(VillagerProfession.NONE);
            leaveShift(villager);
        }
    }

    /** A new member works the order's shift, unless someone already set theirs by hand. */
    static void takeShift(ServerLevel level, VillagerEntityMCA villager, ResourceLocation template) {
        var schedule = com.aetherianartificer.townstead.villager.TownsteadVillagers.get(villager).schedule();
        if (schedule.hasNonDefaultCustomShifts()) return;
        com.aetherianartificer.townstead.shift.template.ShiftTemplateRegistry.resolve(level.getServer(), template).ifPresent(shift -> {
            schedule.setShifts(shift.copyShifts());
            schedule.setTemplateId(template.toString());
            com.aetherianartificer.townstead.shift.ShiftScheduleApplier.apply(villager);
        });
    }

    /** Someone leaving the order goes back to the ordinary day, if they were still on the order's shift. */
    static void leaveShift(VillagerEntityMCA villager) {
        var schedule = com.aetherianartificer.townstead.villager.TownsteadVillagers.get(villager).schedule();
        ResourceLocation template = ResourceLocation.tryParse(schedule.templateId());
        if (template == null) return;
        for (FactionKind kind : PoliticalDefinitions.snapshot().kinds()) {
            if (!template.equals(kind.members().shift())) continue;
            schedule.setShifts(com.aetherianartificer.townstead.shift.ShiftData.getVanillaDefault());
            schedule.setTemplateId("");
            com.aetherianartificer.townstead.shift.ShiftScheduleApplier.apply(villager);
            return;
        }
    }

    /** The members block of the order {@code villager} is training for, if any. */
    static @Nullable FactionKind.Members trainingFor(ServerLevel level, PoliticalSavedData data, VillagerEntityMCA villager) {
        ResourceLocation order = OrderRecruits.get(level.getServer()).orderOf(villager.getUUID());
        Faction faction = order == null ? null : data.faction(order);
        FactionKind kind = faction == null ? null : PoliticalDefinitions.snapshot().kind(faction.kind());
        return kind == null || kind.members().profession() == null ? null : kind.members();
    }

    /** Puts {@code villager} in the order's profession and shift now, rather than at the next sync. */
    static void takeUp(ServerLevel level, VillagerEntityMCA villager, FactionKind.Members members) {
        if (members.profession() == null) return;
        VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.getOptional(members.profession()).orElse(null);
        if (profession == null) return;
        if (villager.getProfession() != profession) villager.setProfession(profession);
        if (members.shift() != null) takeShift(level, villager, members.shift());
    }

    private static Set<ResourceLocation> memberProfessions() {
        Set<ResourceLocation> out = new HashSet<>();
        for (FactionKind kind : PoliticalDefinitions.snapshot().kinds()) {
            if (kind.members().profession() != null) out.add(kind.members().profession());
        }
        return out;
    }
}
