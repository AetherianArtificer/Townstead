package com.aetherianartificer.townstead.chronicle.knowledge;

import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.chronicle.Chronicles;
import com.aetherianartificer.townstead.chronicle.emit.ChronicleEmitter;
import com.aetherianartificer.townstead.chronicle.model.ChronicleEvent;
import com.aetherianartificer.townstead.chronicle.model.Participation;
import com.aetherianartificer.townstead.chronicle.template.ChronicleEventRegistry;
import com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Information spread on a slow per-villager stride. Two modes per stride:
 * conversational gossip (a nearby villager learns the juiciest story they
 * lack, through the gossip channel's fidelity/distortion) and, with no
 * partner, ambient absorption of village-public news past its digest delay.
 *
 * <p>Stories circulate only while their truth event is in the recent-events
 * buffer — old news naturally falls out of conversation.</p>
 */
public final class GossipTicker {

    private static final int STRIDE_TICKS = 200;
    private static final double PARTNER_RADIUS = 4.0;
    private static final float GOSSIP_CHANCE = 0.25f;
    private static final float ABSORB_CHANCE = 0.10f;

    private GossipTicker() {}

    public static void tick(VillagerEntityMCA villager, long gameTime) {
        if ((gameTime + villager.getId()) % STRIDE_TICKS != 0) return;
        if (!(villager.level() instanceof ServerLevel level)) return;
        if (ChronicleEventRegistry.isEmpty()) return;
        if (!KnownStoriesCache.ready(villager.getUUID())) return;

        MinecraftServer server = level.getServer();
        long today = TownsteadCalendar.worldDay(server);

        List<VillagerEntityMCA> nearby = level.getEntitiesOfClass(VillagerEntityMCA.class,
                villager.getBoundingBox().inflate(PARTNER_RADIUS));
        VillagerEntityMCA partner = null;
        for (VillagerEntityMCA candidate : nearby) {
            if (candidate == villager || candidate.isBaby()) continue;
            if (!KnownStoriesCache.ready(candidate.getUUID())) continue;
            partner = candidate;
            break;
        }

        if (partner != null && level.random.nextFloat() < GOSSIP_CHANCE) {
            gossip(server, level, villager, partner, nearby, today);
        } else if (partner == null && level.random.nextFloat() < ABSORB_CHANCE) {
            absorbDigest(server, level, villager, today);
        }
    }

    private static void gossip(MinecraftServer server, ServerLevel level,
                               VillagerEntityMCA teller, VillagerEntityMCA listener,
                               List<VillagerEntityMCA> nearby, long today) {
        int listenerVillage = ChronicleEmitter.resolveVillageId(listener);
        KnownStoriesCache.Entry best = null;
        float bestScore = 0f;
        ChronicleEventTemplate bestTemplate = null;
        for (KnownStoriesCache.Entry entry : KnownStoriesCache.entries(teller.getUUID())) {
            if (entry.reach < ChronicleEvent.REACH_VILLAGE) continue;
            if (entry.reach == ChronicleEvent.REACH_VILLAGE
                    && entry.villageId >= 0 && entry.villageId != listenerVillage) continue;
            if (KnownStoriesCache.knows(listener.getUUID(), entry.storyEventId)) continue;
            ChronicleEventTemplate template = ChronicleEventRegistry.byId(entry.templateId);
            if (template == null) continue;
            float score = NewsScore.score(template, entry.magnitude, entry.eventDay,
                    entry.villageId, today, listenerVillage);
            if (score > bestScore) {
                bestScore = score;
                best = entry;
                bestTemplate = template;
            }
        }
        if (best == null) return;
        ChronicleEvent event = Chronicles.buffer().byId(best.storyEventId);
        if (event == null) return;

        SpreadChannel channel = SpreadChannel.GOSSIP;
        LivingEntity substitute = pickSubstituteCandidate(nearby, teller, listener, event);
        DistortionOverlay overlay = best.overlay.compound(bestTemplate, channel, level.random,
                substitute == null ? null : substitute.getUUID(),
                substitute == null ? null : substitute.getName().getString());
        float fidelity = Math.max(channel.fidelityFloor(),
                best.fidelity * channel.fidelityFactor() * (0.9f + level.random.nextFloat() * 0.2f));

        AccountLedger.learn(server, bestTemplate, event, listener.getUUID(), true, null,
                channel, best.accountId, fidelity, overlay, today);
    }

    private static void absorbDigest(MinecraftServer server, ServerLevel level,
                                     VillagerEntityMCA villager, long today) {
        int myVillage = ChronicleEmitter.resolveVillageId(villager);
        if (myVillage == ChronicleEvent.VILLAGE_NONE) return;
        ChronicleEvent best = null;
        ChronicleEventTemplate bestTemplate = null;
        float bestScore = 0f;
        for (ChronicleEvent event : Chronicles.buffer().matching(e ->
                e.reach() >= ChronicleEvent.REACH_VILLAGE && e.villageId() == myVillage, 32)) {
            ChronicleEventTemplate template = ChronicleEventRegistry.byId(event.templateId());
            if (template == null) continue;
            if (today - event.worldDay() < digestDelayDays(template.rarity())) continue;
            if (KnownStoriesCache.knows(villager.getUUID(), event.eventId())) continue;
            float score = NewsScore.score(template, event.magnitude(), event.worldDay(),
                    event.villageId(), today, myVillage);
            if (score > bestScore) {
                bestScore = score;
                best = event;
                bestTemplate = template;
            }
        }
        if (best == null) return;
        SpreadChannel channel = SpreadChannel.VILLAGE_DIGEST;
        AccountLedger.learn(server, bestTemplate, best, villager.getUUID(), true, null,
                channel, ChronicleEvent.NONE, channel.fidelityFloor() + 0.1f,
                DistortionOverlay.NONE, today);
    }

    /** Institutions later buy reach AND speed; ambient awareness is deliberately slow. */
    private static int digestDelayDays(ChronicleEventTemplate.Rarity rarity) {
        return switch (rarity) {
            case LEGENDARY -> 1;
            case RARE -> 2;
            case UNCOMMON -> 3;
            case COMMON -> 4;
        };
    }

    private static @Nullable LivingEntity pickSubstituteCandidate(List<VillagerEntityMCA> nearby,
                                                                  VillagerEntityMCA teller,
                                                                  VillagerEntityMCA listener,
                                                                  ChronicleEvent event) {
        outer:
        for (VillagerEntityMCA candidate : nearby) {
            // Never players (guardrail), never the conversation itself, never actual participants.
            if (candidate == teller || candidate == listener || candidate.isBaby()) continue;
            for (Participation participation : event.participations()) {
                if (candidate.getUUID().equals(participation.ref().uuid())) continue outer;
            }
            return candidate;
        }
        return null;
    }
}
