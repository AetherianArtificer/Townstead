package com.aetherianartificer.townstead.chronicle.emit;

import com.aetherianartificer.townstead.chronicle.Chronicles;
import com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate.TriggerKey;
import com.aetherianartificer.townstead.chronicle.template.ChronicleTriggerIndex;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * One-line emission taps for game code. Every entry point is hardened
 * (never throws into the caller) and cheap when nothing listens.
 *
 * <p>Truth firewall: counters increment here unconditionally — they are the
 * mechanical record and never depend on whether a template turned the action
 * into a story.</p>
 */
public final class ChronicleTaps {

    private ChronicleTaps() {}

    /** Work action without a notable object: counts + maybe narrates. */
    public static void work(LivingEntity actor, String verb, float magnitude) {
        work(actor, verb, null, null, magnitude);
    }

    /**
     * Work action. Counts {@code verb} and {@code verb:objectId} (both
     * granularities — broad and specific unlock requirements), then offers the
     * trigger to templates with the object's display name as {@code paramName}.
     */
    public static void work(LivingEntity actor, String verb, @Nullable ResourceLocation objectId,
                            @Nullable String paramName, float magnitude) {
        work(actor, verb, objectId, paramName, magnitude, Map.of());
    }

    /** Work completion with stable semantic parameters for future Careers and institutions. */
    public static void work(LivingEntity actor, String verb, @Nullable ResourceLocation objectId,
                            @Nullable String paramName, float magnitude, Map<String, String> semanticParams) {
        try {
            if (!(actor.level() instanceof ServerLevel level)) return;
            MinecraftServer server = level.getServer();
            int completed = counterAmount(semanticParams);
            Chronicles.addCounter(server, actor.getUUID(), verb, completed);
            if (objectId != null) {
                Chronicles.addCounter(server, actor.getUUID(), verb + ":" + objectId, completed);
            }
            if (ChronicleTriggerIndex.isEmpty()) return;
            Map<String, String> params = new HashMap<>(semanticParams == null ? Map.of() : semanticParams);
            if (objectId != null && paramName != null) {
                params.put(paramName, itemName(objectId));
            }
            ChronicleEmitter.emit(level, new TriggerKey("work", verb), actor, magnitude, params);
        } catch (Throwable t) {
            swallow(t);
        }
    }

    /** A completion is one event, but batch-producing stations can finish several items at once. */
    static int counterAmount(@Nullable Map<String, String> semanticParams) {
        if (semanticParams == null) return 1;
        try {
            return Math.max(1, Integer.parseInt(semanticParams.getOrDefault("amount", "1")));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    /**
     * A line crossed — eating sapient flesh, and whatever taboos come after it. Counts always
     * (the mechanical record survives whether or not anyone tells the story), then offers the
     * trigger to templates, whose witness gathering is the whole point: who saw it cannot be
     * reconstructed later, so it is recorded now and judged whenever cultures learn to care.
     */
    public static void taboo(LivingEntity actor, String key, @Nullable ResourceLocation objectId,
                             Map<String, String> params) {
        try {
            if (!(actor.level() instanceof ServerLevel level)) return;
            MinecraftServer server = level.getServer();
            Chronicles.addCounter(server, actor.getUUID(), key, 1);
            if (objectId != null) {
                Chronicles.addCounter(server, actor.getUUID(), key + ":" + objectId, 1);
            }
            if (ChronicleTriggerIndex.isEmpty()) return;
            Map<String, String> merged = new HashMap<>(params == null ? Map.of() : params);
            if (objectId != null) merged.put("meat", itemName(objectId));
            ChronicleEmitter.emit(level, new TriggerKey("taboo", key), actor, 1.0f, merged);
        } catch (Throwable t) {
            swallow(t);
        }
    }

    /**
     * Something that happened <em>to</em> someone rather than something they did:
     * hunger becoming starvation, a cured villager waking up themselves again.
     * Counted like work, because "how many times has this person starved" is a
     * mechanical fact, but it is not work and does not read as work.
     */
    public static void survival(LivingEntity actor, String key, Map<String, String> params) {
        try {
            if (!(actor.level() instanceof ServerLevel level)) return;
            Chronicles.addCounter(level.getServer(), actor.getUUID(), key, 1);
            if (ChronicleTriggerIndex.isEmpty()) return;
            ChronicleEmitter.emit(level, new TriggerKey("survival", key), actor, 1.0f,
                    params == null ? Map.of() : params);
        } catch (Throwable t) {
            swallow(t);
        }
    }

    /** Heart shift between two entities; one friendship/argument story per pair per day. */
    public static void social(LivingEntity actor, LivingEntity other, boolean positive) {
        try {
            if (!(actor.level() instanceof ServerLevel level)) return;
            if (ChronicleTriggerIndex.isEmpty()) return;
            if (!ChronicleRateLimiter.allowPair(level.getServer(), actor.getUUID(), other.getUUID(),
                    positive ? "friendship" : "argument")) {
                return;
            }
            ChronicleEmitter.emit(level,
                    new TriggerKey("social", positive ? "townstead:friendship" : "townstead:argument"),
                    actor, other, 1.0f, Map.of());
        } catch (Throwable t) {
            swallow(t);
        }
    }

    public static void death(LivingEntity deceased, @Nullable DamageSource source) {
        try {
            if (!(deceased.level() instanceof ServerLevel level)) return;
            if (ChronicleTriggerIndex.isEmpty()) return;
            Map<String, String> params = source == null ? Map.of() : Map.of("cause", source.getMsgId());
            ChronicleEmitter.emit(level, new TriggerKey("lifecycle", "townstead:death"),
                    deceased, 1.0f, params);
        } catch (Throwable t) {
            swallow(t);
        }
    }

    public static void birth(LivingEntity baby) {
        try {
            if (!(baby.level() instanceof ServerLevel level)) return;
            if (ChronicleTriggerIndex.isEmpty()) return;
            // Both the Pregnancy.createChild mixin and the spawn-site callers tap
            // births; the pair gate collapses them to one event per baby.
            if (!ChronicleRateLimiter.allowPair(level.getServer(),
                    baby.getUUID(), baby.getUUID(), "birth")) {
                return;
            }
            ChronicleEmitter.emit(level, new TriggerKey("lifecycle", "townstead:birth"),
                    baby, 1.0f, Map.of());
        } catch (Throwable t) {
            swallow(t);
        }
    }

    /** One wedding story per pair per day regardless of which side detects it. */
    public static void marriage(LivingEntity partner, @Nullable LivingEntity spouse) {
        try {
            if (!(partner.level() instanceof ServerLevel level)) return;
            if (ChronicleTriggerIndex.isEmpty()) return;
            if (spouse != null && !ChronicleRateLimiter.allowPair(level.getServer(),
                    partner.getUUID(), spouse.getUUID(), "marriage")) {
                return;
            }
            ChronicleEmitter.emit(level, new TriggerKey("lifecycle", "townstead:marriage"),
                    partner, spouse, 1.0f, Map.of());
        } catch (Throwable t) {
            swallow(t);
        }
    }

    /** The one place an item becomes headline text; pre-history params use it too. */
    public static String itemName(ResourceLocation itemId) {
        try {
            Item item = BuiltInRegistries.ITEM.get(itemId);
            return item.getDescription().getString();
        } catch (Throwable t) {
            return itemId.getPath();
        }
    }

    private static void swallow(Throwable t) {
        // A chronicle tap must never break the work/AI path it rides on.
        com.aetherianartificer.townstead.Townstead.LOGGER.debug("[Chronicles] tap failed", t);
    }
}
