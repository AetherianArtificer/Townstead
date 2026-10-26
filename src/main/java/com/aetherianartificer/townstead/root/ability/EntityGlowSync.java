package com.aetherianartificer.townstead.root.ability;

import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.aetherianartificer.townstead.root.EntityGlowS2CPayload;
import com.aetherianartificer.townstead.root.gene.types.GlowGeneType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Resolves a player's {@code pheno:glow} genes that carry an {@code on} and sends the selected
 * entity ids to that player alone, whose client draws the outline. Only a change is sent; a new
 * player object (join, respawn) always gets one send, so a client never keeps stale ids.
 */
final class EntityGlowSync {

    private record Sent(Level level, Set<Integer> ids) {}

    private static final Map<ServerPlayer, Sent> SENT = new WeakHashMap<>();

    private EntityGlowSync() {}

    static void update(LivingEntity entity, List<GlowGeneType.Instance> glows, @Nullable ConditionContext ctx) {
        if (!(entity instanceof ServerPlayer player)) return;
        Set<Integer> ids = new LinkedHashSet<>();
        if (ctx != null) {
            for (GlowGeneType.Instance glow : glows) {
                if (glow.on() == null) continue;
                if (glow.condition() != null && !glow.condition().test(ctx)) continue;
                for (LivingEntity selected : glow.on().select(SelectorContext.of(ctx))) {
                    if (selected != player) ids.add(selected.getId());
                }
            }
        }
        send(player, ids);
    }

    static void clear(LivingEntity entity) {
        if (entity instanceof ServerPlayer player) send(player, Set.of());
    }

    private static void send(ServerPlayer player, Set<Integer> ids) {
        Sent last = SENT.get(player);
        Level level = player.level();
        if (last != null && last.level() == level && last.ids().equals(ids)) return;
        SENT.put(player, new Sent(level, Set.copyOf(ids)));
        EntityGlowS2CPayload payload = new EntityGlowS2CPayload(ids.stream().mapToInt(Integer::intValue).toArray());
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
        *///?}
    }
}
