package com.aetherianartificer.townstead.pheno.action.block;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who a stand-in player is acting for. A villager uses a block through a fake player, so the block sees a player
 * with no skills, no career and nobody to credit. While the interaction runs, a block that cares can ask here and
 * apply the villager's own pace, skills and rewards instead.
 */
public final class StandIns {

    private static final Map<UUID, WeakReference<LivingEntity>> ACTING = new ConcurrentHashMap<>();

    private StandIns() {}

    /** {@code standIn} acts for {@code actor} until {@link #end}. */
    public static void begin(Player standIn, @Nullable LivingEntity actor) {
        if (standIn == null) return;
        if (actor == null || actor == standIn) ACTING.remove(standIn.getUUID());
        else ACTING.put(standIn.getUUID(), new WeakReference<>(actor));
    }

    public static void end(Player standIn) {
        if (standIn != null) ACTING.remove(standIn.getUUID());
    }

    /** The entity {@code player} is acting for just now, or null if it acts for itself. */
    public static @Nullable LivingEntity actorFor(@Nullable Player player) {
        if (player == null) return null;
        WeakReference<LivingEntity> ref = ACTING.get(player.getUUID());
        LivingEntity actor = ref == null ? null : ref.get();
        return actor == null || !actor.isAlive() ? null : actor;
    }
}
