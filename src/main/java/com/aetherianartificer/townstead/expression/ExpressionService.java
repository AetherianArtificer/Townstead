package com.aetherianartificer.townstead.expression;

import com.aetherianartificer.townstead.data.DataPackLang;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Server authority for expression rate limits, audience routing, and vanilla particle fallback. */
public final class ExpressionService {
    /** Cool each cue independently so one expression never suppresses an unrelated reaction. */
    private static final Map<CooldownKey, Long> LAST = new ConcurrentHashMap<>();

    private record CooldownKey(java.util.UUID actor, ResourceLocation cue) {}

    private ExpressionService() {}

    public static boolean emit(LivingEntity actor, ResourceLocation cueId, @Nullable LivingEntity counterpart) {
        return emit(actor, cueId, counterpart, false);
    }

    /** Debug path: exercise the real audience/render transport without waiting out cue cooldown. */
    public static boolean emitDebug(LivingEntity actor, ResourceLocation cueId,
                                    @Nullable LivingEntity counterpart) {
        return emit(actor, cueId, counterpart, true);
    }

    private static boolean emit(LivingEntity actor, ResourceLocation cueId,
                                @Nullable LivingEntity counterpart, boolean bypassCooldown) {
        ExpressionCue cue = ExpressionCues.get(cueId);
        if (cue == null || !(actor.level() instanceof ServerLevel level)) return false;
        long now = level.getGameTime();
        CooldownKey cooldownKey = new CooldownKey(actor.getUUID(), cueId);
        Long previous = LAST.get(cooldownKey);
        if (!bypassCooldown && previous != null && now - previous < cue.cooldownTicks()) return false;
        ExpressionCueS2CPayload payload = ExpressionCueS2CPayload.of(actor, cue);
        switch (cue.audience()) {
            case TARGET -> {
                if (!(counterpart instanceof ServerPlayer player)) {
                    boolean emitted = spawnParticles(actor, cue.particleBurst());
                    if (emitted) LAST.put(cooldownKey, now);
                    return emitted;
                }
                send(player, payload);
            }
            case NEARBY -> {
                double maxSqr = cue.maxDistance() * cue.maxDistance();
                for (ServerPlayer player : level.players()) {
                    if (player.distanceToSqr(actor) <= maxSqr) send(player, payload);
                }
            }
            case TRACKING -> sendTracking(actor, payload);
        }
        // There is no client-capability handshake for expression billboards. Emitting the small
        // authored vanilla particle alongside the resource-pack icon/text keeps the reaction
        // readable for clients that disable custom rendering and gives the cue physical sparkle
        // in-world rather than leaving fallback_particle dead data.
        spawnParticles(actor, cue.particleBurst());
        LAST.put(cooldownKey, now);
        return true;
    }

    /** Used when authored display data is unavailable or an integration explicitly requests vanilla feedback. */
    public static boolean spawnFallback(LivingEntity actor, String particleId) {
        return spawnParticles(actor, new ExpressionCue.ParticleBurst(particleId, 2, 0.15f, 0.1f, 0.01f));
    }

    private static boolean spawnParticles(LivingEntity actor, ExpressionCue.ParticleBurst burst) {
        if (!(actor.level() instanceof ServerLevel level)) return false;
        if (burst == null || burst.count() <= 0) return false;
        ResourceLocation id = DataPackLang.parseId(burst.type());
        if (id == null) return false;
        Object value = BuiltInRegistries.PARTICLE_TYPE.get(id);
        if (!(value instanceof ParticleOptions particle)) return false;
        level.sendParticles(particle, actor.getX(), actor.getY() + actor.getBbHeight() + 0.25,
                actor.getZ(), burst.count(), burst.spread(), burst.verticalSpread(), burst.spread(),
                burst.speed());
        return true;
    }

    private static void send(ServerPlayer player, ExpressionCueS2CPayload payload) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        //?} else if forge {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
        *///?}
    }

    private static void sendTracking(LivingEntity actor, ExpressionCueS2CPayload payload) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(actor, payload);
        //?} else if forge {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToTrackingEntity(actor, payload);
        if (actor instanceof ServerPlayer self) com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(self, payload);
        *///?}
    }

    static void clearForTests() { LAST.clear(); }
}
