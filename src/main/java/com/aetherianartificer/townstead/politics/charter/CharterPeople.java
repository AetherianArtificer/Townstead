package com.aetherianartificer.townstead.politics.charter;

import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

/** Resolve public identities without requiring their chunks or player sessions to be loaded. */
final class CharterPeople {
    private CharterPeople() {}

    static Component name(ServerPlayer viewer, UUID person) {
        var recorded = FamilyTree.get(viewer.serverLevel()).getOrEmpty(person)
                .map(FamilyTreeNode::getName).filter(name -> name != null && !name.isBlank());
        if (recorded.isPresent()) return Component.literal(recorded.get());
        var player = viewer.server.getPlayerList().getPlayer(person);
        if (player != null) return player.getName();
        for (var level : viewer.server.getAllLevels()) {
            var entity = level.getEntity(person);
            if (entity != null) return entity.getName();
        }
        var cache = viewer.server.getProfileCache();
        if (cache != null) {
            var profile = cache.get(person);
            if (profile.isPresent()) return Component.literal(profile.get().getName());
        }
        return Component.translatable("charter.townstead.name_unavailable");
    }
}
