package com.aetherianartificer.townstead.politics.order;

import com.aetherianartificer.townstead.compat.mca.McaBuildings;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.politics.definition.FactionKind;
import com.aetherianartificer.townstead.politics.definition.PoliticalDefinitions;
import com.aetherianartificer.townstead.politics.relations.FactionMembership;
import com.aetherianartificer.townstead.politics.state.Faction;
import com.aetherianartificer.townstead.politics.state.FactionBonds;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.ritual.RitualService;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Takes villagers into orders. Villagers volunteer at dusk or are put forward by a player; each
 * then trains near the order's altar for a number of nights (a drill Job), and at
 * the next dusk after that the order's officiant holds the joining ritual.
 */
public final class Recruiting {
    private static final int INTERVAL = 100;
    private static final int TRAIN_TICKS = 1200;
    static final double TRAIN_RANGE = 16;
    private static final double ANNOUNCE_RANGE = 96;
    private static final Map<UUID, Integer> TONIGHT = new HashMap<>();
    private static final Map<ResourceLocation, Long> VOLUNTEER_NIGHT = new HashMap<>();
    private static long tonightIndex = Long.MIN_VALUE;

    private Recruiting() {}

    /** Puts {@code person} forward for {@code order}. Null on success, else a lang key saying why not. */
    public static @Nullable String enlist(ServerLevel level, Faction order, VillagerEntityMCA person) {
        FactionKind kind = PoliticalDefinitions.snapshot().kind(order.kind());
        if (kind == null || kind.recruitment() == null) return "recruit.townstead.refused.no_recruiting";
        if (person.isBaby()) return "recruit.townstead.refused.too_young";
        if (FactionMembership.of(person).contains(order.id())) return "recruit.townstead.refused.member";
        OrderRecruits recruits = OrderRecruits.get(level.getServer());
        if (recruits.orderOf(person.getUUID()) != null) return "recruit.townstead.refused.already";
        if (room(level, order, kind) <= 0) return "recruit.townstead.refused.no_room";
        recruits.enlist(order.id(), person.getUUID());
        // Training is their job now, so nothing else hires them away.
        Orders.takeUp(level, person, kind.members());
        return null;
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % INTERVAL != 0) return;
        PoliticalSavedData data = PoliticalSavedData.get(server);
        OrderRecruits recruits = OrderRecruits.get(server);
        for (Faction order : data.factions()) {
            if (!order.active() || order.home() == null) continue;
            FactionKind kind = PoliticalDefinitions.snapshot().kind(order.kind());
            if (kind == null || kind.recruitment() == null) continue;
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, order.home().dimension()));
            if (level == null) continue;
            Village village = VillageManager.get(level).getOrEmpty(order.home().villageId()).orElse(null);
            if (village == null) continue;
            tickOrder(level, village, data, recruits, order, kind);
        }
    }

    private static void tickOrder(ServerLevel level, Village village, PoliticalSavedData data, OrderRecruits recruits,
                                  Faction order, FactionKind kind) {
        FactionKind.Recruitment recruitment = kind.recruitment();
        long time = level.getDayTime() % 24000L;
        long day = level.getDayTime() / 24000L;
        long night = time >= 12000 ? day : day - 1;
        if (night != tonightIndex) {
            tonightIndex = night;
            TONIGHT.clear();
        }
        boolean dusk = time >= 12000 && time < 13000;
        boolean dark = time >= 12000 && time < 23000;
        GlobalPos altar = OrderAltars.get(level.getServer()).altar(order.id());
        boolean altarHere = altar != null && altar.dimension().equals(level.dimension());

        List<UUID> ready = new ArrayList<>();
        for (Map.Entry<UUID, OrderRecruits.Recruit> entry : List.copyOf(recruits.of(order.id()).entrySet())) {
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity person)) continue;
            if (!person.isAlive() || FactionMembership.of(person).contains(order.id())) {
                recruits.remove(entry.getKey());
                continue;
            }
            if (entry.getValue().nights() >= recruitment.trainingNights()) {
                ready.add(entry.getKey());
            } else if (dark && altarHere && person.distanceToSqr(altar.pos().getCenter()) <= TRAIN_RANGE * TRAIN_RANGE) {
                int ticks = TONIGHT.merge(entry.getKey(), INTERVAL, Integer::sum);
                if (ticks >= TRAIN_TICKS) {
                    recruits.trained(order.id(), entry.getKey(), night);
                    if (entry.getValue().nights() + 1 >= recruitment.trainingNights() && person instanceof VillagerEntityMCA) {
                        announce(level, village, Component.translatable("recruit.townstead.trained", person.getName(), order.name()));
                    }
                }
            }
        }

        if (!dusk) return;
        for (UUID id : ready) {
            Entity entity = level.getEntity(id);
            if (entity instanceof LivingEntity person && RitualService.start(level, recruitment.ritual(), person) == null) break;
        }
        if (VOLUNTEER_NIGHT.getOrDefault(order.id(), Long.MIN_VALUE) == night) return;
        VOLUNTEER_NIGHT.put(order.id(), night);
        if (recruitment.volunteerChance() <= 0 || level.getRandom().nextDouble() >= recruitment.volunteerChance()) return;
        VillagerEntityMCA volunteer = volunteer(level, village, recruits, order, recruitment);
        if (volunteer != null && enlist(level, order, volunteer) == null) {
            announce(level, village, Component.translatable("recruit.townstead.volunteered", volunteer.getName(), order.name(),
                    recruitment.trainingNights()));
        }
    }

    private static @Nullable VillagerEntityMCA volunteer(ServerLevel level, Village village, OrderRecruits recruits,
                                                         Faction order, FactionKind.Recruitment recruitment) {
        com.aetherianartificer.townstead.pheno.condition.Condition condition = recruitment.volunteerCondition();
        if (condition == null) return null;
        List<VillagerEntityMCA> willing = new ArrayList<>();
        for (VillagerEntityMCA resident : village.getResidents(level)) {
            if (!resident.isAlive() || resident.isBaby() || recruits.orderOf(resident.getUUID()) != null) continue;
            if (sworn(resident)) continue;
            if (!condition.test(new ConditionContext(resident))) continue;
            willing.add(resident);
        }
        return willing.isEmpty() ? null : willing.get(level.getRandom().nextInt(willing.size()));
    }

    /** Whether someone already belongs to an order that makes its members something. */
    private static boolean sworn(LivingEntity person) {
        PoliticalSavedData data = PoliticalSavedData.get(person.getServer());
        for (ResourceLocation id : FactionMembership.of(person)) {
            Faction faction = data.faction(id);
            FactionKind kind = faction == null ? null : PoliticalDefinitions.snapshot().kind(faction.kind());
            if (kind != null && kind.members().profession() != null) return true;
        }
        return false;
    }

    /** Free places: one for the head plus one per room block in the order's buildings, less members and recruits. */
    static int room(ServerLevel level, Faction order, FactionKind kind) {
        FactionKind.Recruitment recruitment = kind.recruitment();
        if (recruitment == null) return 0;
        int capacity = Integer.MAX_VALUE;
        if (recruitment.building() != null && order.home() != null) {
            Village village = VillageManager.get(level).getOrEmpty(order.home().villageId()).orElse(null);
            capacity = 1 + (village == null ? 0 : places(village, recruitment));
        }
        PoliticalSavedData data = PoliticalSavedData.get(level.getServer());
        int taken = FactionBonds.holders(data, order.id(), kind.membership().bond()).size()
                + OrderRecruits.get(level.getServer()).of(order.id()).size();
        return capacity == Integer.MAX_VALUE ? Integer.MAX_VALUE : capacity - taken;
    }

    private static int places(Village village, FactionKind.Recruitment recruitment) {
        TagKey<Block> tag = recruitment.perBlock() == null ? null : TagKey.create(Registries.BLOCK, recruitment.perBlock());
        int places = 0;
        for (Building building : McaBuildings.all(village)) {
            if (!recruitment.building().equals(building.getType())) continue;
            if (tag == null) {
                places++;
                continue;
            }
            for (Map.Entry<ResourceLocation, List<BlockPos>> blocks : building.getBlocks().entrySet()) {
                Block block = BuiltInRegistries.BLOCK.get(blocks.getKey());
                if (block.defaultBlockState().is(tag)) places += blocks.getValue().size();
            }
        }
        return places;
    }

    private static void announce(ServerLevel level, Village village, Component message) {
        BlockPos center = new BlockPos(village.getCenter());
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().closerThan(center, ANNOUNCE_RANGE)) player.displayClientMessage(message, false);
        }
    }
}
