package com.aetherianartificer.townstead.root.attachment;

import com.aetherianartificer.townstead.root.ability.ResourceValues;
import com.aetherianartificer.townstead.root.gene.types.ResourceGeneType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Live numbers a body shows to everyone who can see it: how full a meter is, what state a part is in,
 * how muddy someone has got. Systems add their values through a {@link Source}. The server works them
 * out from the entity and sends what changed, so a client reads them for anyone in view. A resource
 * meter's own sync only reaches the player who owns it.
 */
public final class BodyValues {

    /** Adds this entity's current values, by key. */
    @FunctionalInterface
    public interface Source {
        void collect(LivingEntity entity, BiConsumer<String, Float> out);
    }

    private static final int SYNC_INTERVAL = 5;
    // Changes smaller than this wait, so a slowly refilling meter doesn't send a packet every interval.
    private static final float SEND_STEP = 1f / 64f;

    private static final List<Source> SOURCES = new ArrayList<>();
    private static final Map<LivingEntity, Map<String, Float>> SENT = java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Integer, Map<String, Float>> CLIENT = new ConcurrentHashMap<>();

    static {
        register(BodyValues::wornResources);
    }

    private BodyValues() {}

    public static void register(Source source) {
        SOURCES.add(source);
    }

    public static String resourceKey(String resourceId) {
        return "resource:" + resourceId;
    }

    /** A value on either side: worked out live on the server, last received on a client. */
    public static float read(LivingEntity entity, String key, float fallback) {
        if (entity.level().isClientSide) {
            Map<String, Float> values = CLIENT.get(entity.getId());
            Float value = values == null ? null : values.get(key);
            return value == null ? fallback : value;
        }
        Float value = current(entity).get(key);
        return value == null ? fallback : value;
    }

    /** Every value last received for an entity, or null. Client only. */
    public static Map<String, Float> clientValues(int entityId) {
        return CLIENT.get(entityId);
    }

    /** A channel of this attachment fed by live state, or null when the channel isn't one. */
    public static Float channel(LivingEntity entity, AttachmentDef def, String channel) {
        AttachmentBody.ValueSource source = def.body().values().get(channel);
        return source == null ? null : source.map(read(entity, source.key(), 0f));
    }

    public static void tick(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        BodyParts.tick(entity);
        BodyPose.tickClips(entity);
        BodyPoseSim.tick(entity);
        if (entity instanceof net.conczin.mca.entity.VillagerEntityMCA villager) BodyFit.tick(villager);
        // A part that folds can't stay spread in flight where it no longer fits.
        if (entity.isFallFlying() && BodyVolumes.folded(entity)) {
            if (entity instanceof net.minecraft.world.entity.player.Player player) player.stopFallFlying();
        }
        if (entity.tickCount % SYNC_INTERVAL != 0) return;
        Map<String, Float> values = current(entity);
        Map<String, Float> sent = SENT.get(entity);
        if (sent == null ? values.isEmpty() : !changed(sent, values)) return;
        SENT.put(entity, values);
        BodyValuesS2CPayload payload = new BodyValuesS2CPayload(entity.getId(), values);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToTrackingEntity(entity, payload);
        if (entity instanceof ServerPlayer self) com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(self, payload);
        *///?}
    }

    /** Tells a player who starts seeing {@code target} its values. */
    public static void syncTo(ServerPlayer player, Entity target) {
        if (!(target instanceof LivingEntity living)) return;
        Map<String, Float> values = current(living);
        if (values.isEmpty()) return;
        BodyValuesS2CPayload payload = new BodyValuesS2CPayload(living.getId(), values);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
        *///?}
    }

    public static void receive(int entityId, Map<String, Float> values) {
        if (values.isEmpty()) CLIENT.remove(entityId);
        else CLIENT.put(entityId, values);
    }

    /** Forgets everything received; the ids mean nothing in the next world. */
    public static void clear() {
        CLIENT.clear();
    }

    private static Map<String, Float> current(LivingEntity entity) {
        Map<String, Float> values = new HashMap<>();
        for (Source source : SOURCES) source.collect(entity, values::put);
        return values;
    }

    /** True when a value appeared, went away, reached either end of its range, or moved a full step. */
    static boolean changed(Map<String, Float> sent, Map<String, Float> now) {
        if (!sent.keySet().equals(now.keySet())) return true;
        for (Map.Entry<String, Float> entry : now.entrySet()) {
            float before = sent.get(entry.getKey());
            float value = entry.getValue();
            if (Math.abs(value - before) >= SEND_STEP) return true;
            if (value != before && (value == 0f || value == 1f)) return true;
        }
        return false;
    }

    private static void wornResources(LivingEntity entity, BiConsumer<String, Float> out) {
        for (AttachmentDef def : WornAttachments.of(entity)) {
            for (AttachmentBody.ValueSource source : def.body().values().values()) {
                if (!source.key().startsWith("resource:")) continue;
                ResourceLocation id = ResourceLocation.tryParse(source.key().substring("resource:".length()));
                if (id != null) out.accept(source.key(), fill(entity, id));
            }
        }
    }

    private static float fill(LivingEntity entity, ResourceLocation id) {
        ResourceGeneType.Instance meter = ResourceValues.meter(entity, id);
        if (meter == null || meter.max() <= meter.min()) return 0f;
        return (ResourceValues.get(entity, id) - meter.min()) / (float) (meter.max() - meter.min());
    }
}
