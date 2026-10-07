package com.aetherianartificer.townstead.journey;

import com.aetherianartificer.townstead.building.BuildingCells;
import com.aetherianartificer.townstead.downed.DownedService;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.MoveState;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * A villager travelling with a player: MCA's follow, kept across reloads, with a way to keep up.
 * Left far behind in the same world, they catch up out of sight. When the player changes
 * dimension, they come after them as a short journey. Their home stays theirs. When the trip ends
 * away from home, they make their own way back. Their pets can be told to stay home.
 */
public final class Companions {
    /** The journey purpose for a companion catching up with their player across worlds. */
    public static final ResourceLocation CATCH_UP = ResourceLocation.tryParse("townstead:companion");
    private static final String KEY = "townstead:companion";
    private static final String LEADER = "leader";
    private static final String LEAVE_PETS = "leave_pets";
    private static final double CATCH_UP_DISTANCE = 48;
    private static final Map<VillagerEntityMCA, Boolean> LOADED = Collections.synchronizedMap(new WeakHashMap<>());

    private Companions() {}

    /** {@code villager} travels with {@code player} until {@link #end}. */
    public static void start(VillagerEntityMCA villager, ServerPlayer player, boolean leavePets) {
        CompoundTag data = new CompoundTag();
        data.putUUID(LEADER, player.getUUID());
        data.putBoolean(LEAVE_PETS, leavePets);
        villager.getPersistentData().put(KEY, data);
        LOADED.put(villager, Boolean.TRUE);
        villager.getVillagerBrain().setMoveState(MoveState.FOLLOW, player);
    }

    /** Ends the trip. Away from home, they walk back on their own. */
    public static void end(VillagerEntityMCA villager) {
        villager.getPersistentData().remove(KEY);
        LOADED.remove(villager);
        villager.getVillagerBrain().setMoveState(MoveState.MOVE, null);
        if (!(villager.level() instanceof ServerLevel level) || atHome(level, villager)) return;
        Village home = villager.getResidency().getHomeVillage().orElse(null);
        if (home == null) return;
        CompoundTag data = new CompoundTag();
        data.putString("return_dimension", level.dimension().location().toString());
        data.putInt("return_village", home.getId());
        data.putBoolean(Journeys.KEEP_HOME, true);
        // They set off home once nobody is watching, not the moment the trip ends mid-conversation.
        Journeys.departUnseen(villager, Journeys.ERRAND, 0, data);
    }

    public static @Nullable UUID leader(Entity entity) {
        CompoundTag data = entity.getPersistentData().getCompound(KEY);
        return data.hasUUID(LEADER) ? data.getUUID(LEADER) : null;
    }

    public static boolean travelsWith(Entity entity, Entity player) {
        return player.getUUID().equals(leader(entity));
    }

    /** Whether the villager's pets stay home while they travel. */
    public static boolean leavesPets(Entity entity) {
        return leader(entity) != null && entity.getPersistentData().getCompound(KEY).getBoolean(LEAVE_PETS);
    }

    /** Join hook: a companion loading back in picks their trip up again. */
    public static void onJoin(Entity entity) {
        if (entity instanceof VillagerEntityMCA villager && !entity.level().isClientSide && leader(villager) != null) {
            LOADED.put(villager, Boolean.TRUE);
        }
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        if (!LOADED.isEmpty()) {
            for (VillagerEntityMCA villager : new ArrayList<>(LOADED.keySet())) keepUp(server, villager);
        }
        Journeys journeys = Journeys.get(server);
        for (Journeys.Journey journey : journeys.by(CATCH_UP)) {
            UUID leader = journey.data().hasUUID(LEADER) ? journey.data().getUUID(LEADER) : null;
            ServerPlayer player = leader == null ? null : server.getPlayerList().getPlayer(leader);
            if (player == null) continue;
            VillagerEntityMCA villager = Journeys.arrive(player.serverLevel(), journey.traveller(), near(player));
            if (villager != null) villager.getVillagerBrain().setMoveState(MoveState.FOLLOW, player);
        }
    }

    private static void keepUp(MinecraftServer server, VillagerEntityMCA villager) {
        UUID leaderId = leader(villager);
        if (villager.isRemoved() || !villager.isAlive() || leaderId == null) {
            LOADED.remove(villager);
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(leaderId);
        if (player == null || DownedService.isDowned(villager)) return;
        if (player.level() != villager.level()) {
            // The player went through a portal: follow them there as a journey, pets and all.
            LOADED.remove(villager);
            CompoundTag data = new CompoundTag();
            data.putUUID(LEADER, leaderId);
            data.putBoolean(Journeys.KEEP_HOME, true);
            if (leavesPets(villager)) data.putBoolean(Journeys.LEAVE_PETS, true);
            Journeys.depart(villager, CATCH_UP, 0, data);
            return;
        }
        // MCA does not keep who is being followed across a reload.
        //? if >=1.21 {
        boolean following = villager.getBrain().hasMemoryValue(MemoryModuleTypeMCA.PLAYER_FOLLOWING);
        //?} else {
        /*boolean following = villager.getBrain().hasMemoryValue(MemoryModuleTypeMCA.PLAYER_FOLLOWING.get());
        *///?}
        if (!following) {
            villager.getVillagerBrain().setMoveState(MoveState.FOLLOW, player);
        }
        if (villager.distanceToSqr(player) > CATCH_UP_DISTANCE * CATCH_UP_DISTANCE) {
            BlockPos to = near(player);
            villager.getNavigation().stop();
            villager.teleportTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
        }
    }

    /** A standing cell a few blocks from the player, else their own spot. */
    private static BlockPos near(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos center = player.blockPosition();
        for (int r = 2; r <= 4; r++) {
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -2, -r), center.offset(r, 2, r))) {
                if (pos.distManhattan(center) >= r && BuildingCells.standable(level, pos)) return pos.immutable();
            }
        }
        return center;
    }

    private static boolean atHome(ServerLevel level, VillagerEntityMCA villager) {
        Village home = villager.getResidency().getHomeVillage().orElse(null);
        if (home == null) return true;
        Village here = VillageManager.get(level).findNearestVillage(villager.blockPosition(), Village.MERGE_MARGIN).orElse(null);
        return here != null && here.getId() == home.getId();
    }
}
