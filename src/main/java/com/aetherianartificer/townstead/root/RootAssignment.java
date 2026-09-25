package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.root.appearance.HairColors;
import com.aetherianartificer.townstead.root.appearance.HairResolver;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Server-side root assignment with no editor preview: the command and the public API. Runs the
 * editor's Apply path, re-rolls body metrics and hair inside the new root's ranges the way a
 * natural spawn does, syncs every tracking client, and posts the root-changed event.
 */
public final class RootAssignment {

    private RootAssignment() {}

    /** The target's current root, the default when unset, or null for an entity that has none. */
    public static String currentRoot(Entity entity) {
        String raw;
        if (entity instanceof VillagerEntityMCA villager) raw = TownsteadVillagers.get(villager).life().rootId();
        else if (entity instanceof ServerPlayer player) raw = PlayerRoot.getRootId(player);
        else return null;
        return raw == null || raw.isEmpty() ? RootRegistry.DEFAULT_ID.toString() : raw;
    }

    /**
     * Assign {@code id} (already resolved through {@link RootServerLogic#resolveKnown}) to a villager
     * or player. Returns false, touching nothing, when the target already has that root or cannot
     * carry one.
     */
    public static boolean assign(Entity entity, ResourceLocation id) {
        String before = currentRoot(entity);
        if (before == null || before.equals(id.toString())) return false;
        if (entity instanceof VillagerEntityMCA villager) {
            assignVillager(villager, id);
        } else {
            assignPlayer((ServerPlayer) entity, id);
        }
        com.aetherianartificer.townstead.api.impl.v1.ApiEvents.rootChanged(
                (net.minecraft.world.entity.LivingEntity) entity, before, id.toString());
        return true;
    }

    private static void assignVillager(VillagerEntityMCA villager, ResourceLocation id) {
        RootServerLogic.setVillagerRoot(villager, id);
        TownsteadVillager state = TownsteadVillagers.get(villager);
        RootGenes.apply(villager, RootGenes.resolveBodyMetrics(RootRegistry.effectiveInheritedGenes(id)),
                villager.getRandom());
        HairColors.roll(villager, HairResolver.resolve(id,
                state.life().hasHeritage() ? state.life().heritage() : null), villager.getRandom());
        villager.refreshDimensions();

        sendToTracking(villager, new RootSyncS2CPayload(villager.getId(), id.toString()));
        sendToTracking(villager, ExpressedGenesS2CPayload.forEntity(villager.getId(), villager));
        var lifeSync = com.aetherianartificer.townstead.Townstead.townstead$lifeSync(villager);
        if (lifeSync != null) sendToTracking(villager, lifeSync);
    }

    private static void assignPlayer(ServerPlayer player, ResourceLocation id) {
        RootServerLogic.setPlayerRoot(player, id);
        // Only re-roll a player's body when they already have MCA villager-model data; writing
        // gene keys into an unset snapshot would switch their look on without their choice.
        net.conczin.mca.server.world.data.PlayerSaveData data =
                net.conczin.mca.server.world.data.PlayerSaveData.get(player);
        if (data.isEntityDataSet()) {
            CompoundTag entityData = data.getEntityData();
            float[] genes = RootGenes.readFromPlayerData(entityData);
            RootGenes.apply(genes, RootGenes.resolveBodyMetrics(RootRegistry.effectiveInheritedGenes(id)),
                    player.getRandom());
            RootServerLogic.commitGenes(player, RootSetC2SPayload.SELF, genes, entityData.getInt("HairColor"));
        }

        sendToPlayer(player, new RootSyncS2CPayload(RootSetC2SPayload.SELF, id.toString()));
        RootSyncS2CPayload entitySync = new RootSyncS2CPayload(player.getId(), id.toString());
        sendToPlayer(player, entitySync);
        sendToTracking(player, entitySync);
        ExpressedGenesS2CPayload genes = ExpressedGenesS2CPayload.forEntity(player.getId(), player);
        sendToPlayer(player, genes);
        sendToTracking(player, genes);
    }

    private static void sendToPlayer(ServerPlayer player, Object payload) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                (net.minecraft.network.protocol.common.custom.CustomPacketPayload) payload);
        //?} else if forge {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
        *///?}
    }

    private static void sendToTracking(Entity entity, Object payload) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntity(entity,
                (net.minecraft.network.protocol.common.custom.CustomPacketPayload) payload);
        //?} else if forge {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToTrackingEntity(entity, payload);
        *///?}
    }
}
