package com.aetherianartificer.townstead.chronicle.emit;

import com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate.TriggerKey;
import com.aetherianartificer.townstead.chronicle.template.ChronicleTriggerIndex;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Minecraft's own {@code GameEvent}s as a chronicle source. Templates bind to
 * them by registry id with trigger type {@code game}:
 *
 * <pre>{@code { "trigger": { "type": "game", "key": "minecraft:lightning_strike" } }}</pre>
 *
 * <p>This is the perception layer, and it is mod-neutral for free: any mod's
 * explosion fires {@code minecraft:explode}, so nothing needs a compat module.
 * Work-completion taps (such as {@code townstead_work:cook}) stay, because they
 * carry meaning a raw game event cannot — which recipe, which profession, how
 * much XP — while these carry only "this happened, here".</p>
 *
 * <p>Vanilla fires these constantly, so the first thing this does is ask whether
 * any template listens for that id at all. Nothing else runs otherwise.</p>
 */
public final class GameEventTap {

    /** How far a villager can be and still be said to have seen it. */
    private static final double WITNESS_RADIUS = 16.0;

    private GameEventTap() {}

    /**
     * The story belongs to a person, not to the creeper that caused it: a cause
     * that is a villager or a player is the actor, and otherwise the nearest
     * villager is, as the one who saw it happen.
     */
    public static void onGameEvent(Level level, @Nullable ResourceLocation id, Vec3 position,
                                   @Nullable Entity cause) {
        if (id == null || !ChronicleTriggerIndex.watchesGameEvent(id)) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        LivingEntity actor = null;
        if (cause instanceof VillagerEntityMCA || cause instanceof ServerPlayer) {
            actor = (LivingEntity) cause;
        } else {
            actor = nearestVillager(serverLevel, position);
        }
        if (actor == null) return;

        ChronicleEmitter.emit(serverLevel, new TriggerKey(ChronicleTriggerIndex.TYPE_GAME,
                id.toString()), actor, 1.0f, Map.of());
    }

    private static @Nullable VillagerEntityMCA nearestVillager(ServerLevel level, Vec3 position) {
        AABB box = new AABB(position, position).inflate(WITNESS_RADIUS);
        List<VillagerEntityMCA> nearby = level.getEntitiesOfClass(VillagerEntityMCA.class, box);
        VillagerEntityMCA closest = null;
        double best = Double.MAX_VALUE;
        for (VillagerEntityMCA villager : nearby) {
            if (villager.isBaby()) continue;
            double distance = villager.position().distanceToSqr(position);
            if (distance < best) {
                best = distance;
                closest = villager;
            }
        }
        return closest;
    }
}
