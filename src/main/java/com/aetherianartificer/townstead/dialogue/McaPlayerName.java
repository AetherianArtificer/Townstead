package com.aetherianartificer.townstead.dialogue;

import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Resolves the name a villager addresses a player by: a reborn player's current name, else the MCA
 * family-tree name. MCA lets players set their own name in the
 * family tree, so its dialogue substitutes that ({@code Messenger.getName}) for {@code %1$s}, not the
 * raw account username. Townstead routes every line through its own voice, so it must resolve the same
 * name or villagers revert to calling the player by their Minecraft username.
 *
 * <p>The family-tree lookup is mirrored here rather than calling MCA's {@code Messenger.getName} static
 * directly, which the forge compile jar sometimes lags on (see the MCA compile-jar drift note).</p>
 */
public final class McaPlayerName {

    private McaPlayerName() {}

    public static String of(Player target) {
        if (target == null) return "";
        String character = com.aetherianartificer.townstead.rebirth.Rebirth.characterName(target);
        if (character != null && !character.isBlank()) return character;
        if (target instanceof ServerPlayer serverPlayer && serverPlayer.level() instanceof ServerLevel world) {
            return FamilyTree.get(world)
                    .getOrEmpty(serverPlayer.getUUID())
                    .map(FamilyTreeNode::getName)
                    .filter(n -> n != null && !n.isBlank())
                    .orElse(target.getName().getString());
        }
        return target.getName().getString();
    }
}
