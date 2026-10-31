package com.aetherianartificer.townstead.politics.charter;

import com.aetherianartificer.townstead.politics.state.Faction;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Who may rename a village from MCA's blueprint: anyone while no faction holds it, else only those
 * who may draft that faction's charter. A village under another mod's government keeps MCA's rule.
 */
public final class VillageRenames {
    private VillageRenames() {}

    /** False, with a message to the player, when they may not rename this village. */
    public static boolean allowed(ServerPlayer player, int villageId) {
        PoliticalSavedData data = PoliticalSavedData.get(player.server);
        Faction faction = data.faction(new SettlementRef(player.serverLevel().dimension().location(), villageId));
        if (faction == null || !faction.active() || data.externalGovernment(faction.id())) return true;
        if (CharterDrafts.mayDraft(player, faction)) return true;
        player.displayClientMessage(Component.translatable("charter.townstead.village_rename_denied"), true);
        return false;
    }
}
