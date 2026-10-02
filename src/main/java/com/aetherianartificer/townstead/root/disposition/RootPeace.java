package com.aetherianartificer.townstead.root.disposition;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.root.LegacyNamespace;
import com.aetherianartificer.townstead.root.PlayerRoot;
import com.aetherianartificer.townstead.switchboard.Switchboard;
import com.aetherianartificer.townstead.switchboard.WorldKeys;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * The world's truce between Roots. Folk (villagers and players) are at peace when Root hostility is
 * off for the world or either side's Root is marked peaceful. Peace only cancels the hostility Root
 * groups declare; wild mobs are never folk, so they stay hostile.
 */
public final class RootPeace {

    private RootPeace() {}

    public static boolean hostilityOn() {
        return Switchboard.get(TownsteadConfig.ROOT_HOSTILITY);
    }

    public static boolean peaceful(@Nullable ResourceLocation rootId) {
        if (rootId == null) return false;
        return (Boolean) Switchboard.content(WorldKeys.rootPeaceful(LegacyNamespace.canonical(rootId).toString()));
    }

    public static boolean between(LivingEntity a, LivingEntity b) {
        if (!isFolk(a) || !isFolk(b)) return false;
        return !hostilityOn() || peaceful(rootOf(a)) || peaceful(rootOf(b));
    }

    /** Whether a candidate Root may join a village whose groups would otherwise clash with it. */
    public static boolean shareVillage(ResourceLocation candidate, @Nullable ResourceLocation resident) {
        if (!Switchboard.get(TownsteadConfig.PEACEFUL_ROOTS_SHARE_VILLAGES)) return false;
        return !hostilityOn() || peaceful(candidate) || peaceful(resident);
    }

    private static boolean isFolk(LivingEntity entity) {
        return entity instanceof Player || entity instanceof AbstractVillager;
    }

    private static @Nullable ResourceLocation rootOf(LivingEntity entity) {
        String id = entity instanceof Player player ? PlayerRoot.getRootId(player)
                : entity instanceof VillagerEntityMCA villager ? TownsteadVillagers.get(villager).life().rootId()
                : null;
        return id == null || id.isEmpty() ? null : ResourceLocation.tryParse(id);
    }
}
