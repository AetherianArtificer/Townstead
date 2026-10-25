package com.aetherianartificer.townstead.root.appearance;

import com.aetherianartificer.townstead.clothing.policy.SkinPicker;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * A player's look when they become a new root, the way a villager's is set when it is founded or
 * changes root: a fresh roll of the root's hair colours, its starting eye colour (an editable iris),
 * and an outfit made for its body when that body only wears its own (a zombie's tattered clothes)
 * and the current one doesn't fit. Written into the player's saved MCA data, which MCA draws a
 * villager-model player from, then broadcast.
 *
 * <p>Only for a player who already has MCA villager-model data: writing into an unset record would
 * switch their look on without their choice. The editor's Done also saves its preview over this,
 * which is the same pick when the preview made one.</p>
 */
public final class PlayerFounding {

    private PlayerFounding() {}

    public static void refresh(ServerPlayer player, ResourceLocation rootId) {
        net.conczin.mca.server.world.data.PlayerSaveData data =
                net.conczin.mca.server.world.data.PlayerSaveData.get(player);
        if (!data.isEntityDataSet()) return;
        CompoundTag entityData = data.getEntityData();

        HairColors.rollInto(entityData, HairResolver.resolve(rootId, com.aetherianartificer.townstead.root.PlayerRoot.getHeritage(player)),
                player.getRandom());

        int[] iris = IrisColors.editable(player);
        if (iris != null) IrisColors.seedInto(entityData, iris[0], iris[1]);

        // an outfit the new body can't wear (a Ribbit's, on a human) gives way to one it can
        SkinPicker.refit(rootId, com.aetherianartificer.townstead.root.PlayerRoot.getHeritage(player),
                entityData.getString("Clothes"),
                net.conczin.mca.entity.ai.relationship.Gender.byId(entityData.getInt("Gender")), "minecraft:none",
                player.getUUID().getMostSignificantBits()).ifPresent(skin -> entityData.putString("Clothes", skin));

        com.aetherianartificer.townstead.root.trait.TraitBlocks.applyTo(entityData, rootId);

        // getEntityData is a copy on the 1.21.1 line; store the edit or only the broadcast sees it.
        data.setEntityData(entityData);
        data.setEntityDataSet(true);
        data.setDirty();
        // MCA renders players-as-villagers from this record on every client; mirror its own broadcast.
        for (ServerPlayer p : player.serverLevel().players()) {
            //? if neoforge {
            net.conczin.mca.network.Network.sendToPlayer(
                    new net.conczin.mca.network.s2c.PlayerDataMessage(player.getUUID(), entityData), p);
            //?} else {
            /*net.conczin.mca.cobalt.network.NetworkHandler.sendToPlayer(
                    new net.conczin.mca.network.s2c.PlayerDataMessage(player.getUUID(), entityData), p);
            *///?}
        }
    }
}
