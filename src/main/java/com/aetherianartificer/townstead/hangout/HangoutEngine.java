package com.aetherianartificer.townstead.hangout;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.mca.McaBuildings;
import com.aetherianartificer.townstead.compat.mca.McaPersonalityCompat;
import com.aetherianartificer.townstead.dialogue.contextual.DialogueDirector;
import com.aetherianartificer.townstead.expression.ExpressionService;
import com.aetherianartificer.townstead.needs.Amenities;
import com.aetherianartificer.townstead.performance.PerformanceHandle;
import com.aetherianartificer.townstead.performance.PerformanceProviders;
import com.aetherianartificer.townstead.performance.PerformanceRequest;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.profession.ProfessionIdentity;
import com.aetherianartificer.townstead.profession.ProfessionSites;
import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.social.Bond;
import com.aetherianartificer.townstead.social.Bonds;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Venue attendance and social coordination. Attendance is individual: every visitor owns one
 * visit, seat and lifecycle. Group-shaped activities are short beats assembled from whoever is
 * already present, and never own or end their participants' visits.
 */
public final class HangoutEngine {
    private static final int ACQUISITION_STRIDE = 200;
    private static final int ACTIVE_STRIDE = 20;
    private static final int BEAT_RETRY_TICKS = 100;
    private static final int DANGER_GRACE_TICKS = 200;
    private static final double ARRIVAL_DISTANCE_SQUARED = 2.75D;
    private static final HangoutClaims CLAIMS = new HangoutClaims();
    private static final HostedServiceCoordinator HOSTED_SERVICE = new HostedServiceCoordinator();
    private static final Map<UUID, HangoutVisit> VISITS = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> BY_VISITOR = new ConcurrentHashMap<>();
    private static final Map<UUID, HangoutBeat> BEATS = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> BY_BEAT_MEMBER = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> COOLDOWNS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_DIAGNOSTIC = new ConcurrentHashMap<>();

    private record VenueCandidate(HangoutVenue definition, Village village, Building building,
                                  BlockPos anchor, List<SpotCandidate> spots) {}
    private record SpotCandidate(HangoutSpot definition, BlockPos anchor) {}

    private HangoutEngine() {}

    /** Used by embodiment orphan recovery; the owner id is an individual visit id. */
    public static boolean isLive(UUID owner) {
        HangoutVisit visit = VISITS.get(owner);
        return visit != null && visit.phase() != HangoutVisit.Phase.COMPLETE
                && visit.phase() != HangoutVisit.Phase.INTERRUPTED;
    }

    public static @Nullable HangoutVisit visit(UUID visitor) {
        UUID id = BY_VISITOR.get(visitor);
        return id == null ? null : VISITS.get(id);
    }

    /** Data-authored fatigue recovery for this visitor's active rest surface, or zero. */
    public static float restRecovery(UUID visitor) {
        HangoutVisit current = visit(visitor);
        if (current == null || current.phase() != HangoutVisit.Phase.PRESENT) return 0F;
        HangoutSpot.RestBonus rest = current.visitor().rest();
        return rest == null ? 0F : rest.fatigueRecovery();
    }

    public static void onReload() {
        // Visits and beats resolve definitions every tick and leave cleanly if one disappeared.
    }

    public static void tick(VillagerEntityMCA villager) {
        if (!(villager.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        HangoutEmbodiment.bootstrap();
        if ((now + (villager.getUUID().hashCode() & 31)) % 400L == 0L) {
            HangoutEmbodiment.recoverNearby(level, villager.blockPosition());
            CLAIMS.prune(now);
            HOSTED_SERVICE.prune(now);
            COOLDOWNS.entrySet().removeIf(entry -> entry.getValue() <= now);
            LAST_DIAGNOSTIC.entrySet().removeIf(entry -> now - entry.getValue() > 12000L);
        }

        UUID visitId = BY_VISITOR.get(villager.getUUID());
        if (visitId != null) {
            HangoutVisit active = VISITS.get(visitId);
            if (active == null) BY_VISITOR.remove(villager.getUUID(), visitId);
            else advanceVisit(level, active, now);
            return;
        }

        if (HangoutData.policies().isEmpty() || HangoutData.venues().isEmpty()
                || HangoutData.activities().isEmpty() || HangoutData.spots().isEmpty()) return;
        if ((now + (villager.getUUID().hashCode() & Integer.MAX_VALUE)) % ACQUISITION_STRIDE != 0L) return;
        long cooldown = COOLDOWNS.getOrDefault(villager.getUUID(), 0L);
        if (cooldown > now) {
            if (isMeet(villager)) traceRejection(villager, now, "cooldown " + (cooldown - now) + " ticks");
            return;
        }
        if (!available(villager)) {
            if (isMeet(villager)) traceRejection(villager, now, availabilityReason(villager));
            return;
        }
        tryStartVisit(level, villager, now);
    }

    public static void forget(VillagerEntityMCA villager) {
        UUID id = BY_VISITOR.get(villager.getUUID());
        HangoutVisit current = id == null ? null : VISITS.get(id);
        if (current != null && villager.level() instanceof ServerLevel level) {
            cleanupVisit(level, current, false, "visitor_removed", level.getGameTime());
        }
    }

    private static synchronized void tryStartVisit(ServerLevel level, VillagerEntityMCA villager, long now) {
        if (BY_VISITOR.containsKey(villager.getUUID())) return;
        String rejection = "no policy accepted visitor_when";
        for (HangoutPolicy policy : HangoutData.policies().values()) {
            if (!test(policy.visitorWhen(), villager)) continue;
            List<VenueCandidate> venues = preferredVenueOrder(
                    discover(level, villager, policy.venueRadius()), villager, policy);
            if (venues.isEmpty()) {
                rejection = "no recognized venue within " + policy.venueRadius() + " blocks";
                continue;
            }
            for (VenueCandidate venue : venues) {
                if (!test(venue.definition().openWhen(), villager)) {
                    rejection = "venue " + venue.definition().id() + " rejected open_when";
                    continue;
                }
                if (!test(venue.definition().admissionWhen(), villager)) {
                    rejection = "venue " + venue.definition().id() + " rejected admission_when";
                    continue;
                }
                if (isVenueStaff(level, venue.definition(), venue.building().getId(), villager)) {
                    rejection = villager.getName().getString() + " is staff at " + venue.definition().id();
                    continue;
                }
                List<String> details = new ArrayList<>();
                HangoutVisit visit = claimVisit(level, venue, policy, villager, now, details);
                if (visit == null) {
                    rejection = "venue " + venue.definition().id() + " rejected admission: "
                            + String.join(", ", details.stream().limit(4).toList());
                    continue;
                }
                VISITS.put(visit.id(), visit);
                BY_VISITOR.put(villager.getUUID(), visit.id());
                ownTravelTarget(villager, visit.visitor().approach(), true);
                traceVisitStart(villager, visit, venue.definition(), policy, now);
                com.aetherianartificer.townstead.api.impl.v1.ApiEvents.hangoutStarted(villager, visit);
                return;
            }
        }
        traceRejection(villager, now, rejection);
    }

    private static @Nullable HangoutVisit claimVisit(ServerLevel level, VenueCandidate venue,
                                                      HangoutPolicy policy, VillagerEntityMCA villager,
                                                      long now, List<String> diagnostics) {
        UUID visitId = UUID.randomUUID();
        String dimension = level.dimension().location().toString();
        String venueKey = venue.definition().id() + "/" + venue.village().getId()
                + "/" + venue.building().getId();
        HangoutClaims.Key venueSlot = null;
        for (int slot = 0; slot < venue.definition().capacity(); slot++) {
            HangoutClaims.Key candidate = new HangoutClaims.Key(dimension, "venue", venueKey + "#" + slot);
            if (CLAIMS.available(candidate, visitId, now)) {
                venueSlot = candidate;
                break;
            }
        }
        if (venueSlot == null) {
            diagnostics.add("venue full");
            return null;
        }

        HangoutClaims.Key visitorKey = new HangoutClaims.Key(
                dimension, "visitor", villager.getUUID().toString());
        List<SpotCandidate> spots = venue.spots().stream()
                .filter(spot -> supportsAnyActivity(venue.definition(), spot.definition().posture()))
                .sorted(Comparator.comparingDouble(spot -> distance(villager, spot.anchor())))
                .toList();
        if (spots.isEmpty()) {
            diagnostics.add("no activity-compatible furniture");
            return null;
        }

        for (SpotCandidate spot : spots) {
            BlockState state = level.getBlockState(spot.anchor());
            BlockPos approach = findApproach(level, villager, spot.anchor(), visitId, now);
            if (approach == null) {
                diagnostics.add("no free reachable approach at " + spot.anchor().toShortString());
                continue;
            }
            for (int slot = 0; slot < spot.definition().capacity(); slot++) {
                List<HangoutClaims.Key> requested = new ArrayList<>();
                requested.add(visitorKey);
                requested.add(venueSlot);
                requested.add(HangoutClaims.seat(dimension, spot.anchor().asLong(), slot));
                boolean linkedFree = true;
                for (BlockPos linkedPos : spot.definition().linkedPositions(state, spot.anchor())) {
                    HangoutClaims.Key linked = HangoutClaims.seat(dimension, linkedPos.asLong(), 0);
                    if (!CLAIMS.available(linked, visitId, now)) {
                        linkedFree = false;
                        break;
                    }
                    requested.add(linked);
                }
                if (!linkedFree) continue;
                HangoutClaims.Key approachKey = new HangoutClaims.Key(
                        dimension, "approach", Long.toString(approach.asLong()));
                if (!CLAIMS.available(approachKey, visitId, now)) continue;
                requested.add(approachKey);
                if (!CLAIMS.tryClaimAll(visitId, requested, now, policy.leaseTicks())) continue;

                HangoutVisit.Visitor visitor = new HangoutVisit.Visitor(villager.getUUID(),
                        spot.anchor(), approach, spot.definition().posture(), spot.definition().adapter(),
                        spot.definition().embodimentPosition(state, spot.anchor()),
                        spot.definition().rest(), null);
                return new HangoutVisit(visitId, level.dimension().location(), venue.definition().id(),
                        venue.building().getId(), policy.id(), venue.anchor(), visitor,
                        now, arrivalDeadline(villager, approach, policy, now));
            }
        }
        diagnostics.add("all compatible furniture claimed or lacks a distinct approach");
        return null;
    }

    private static void advanceVisit(ServerLevel level, HangoutVisit visit, long now) {
        synchronized (visit) {
            if (visit.lastTick() == now) return;
            visit.markTick(now);
            HangoutPolicy policy = HangoutData.policies().get(visit.policy());
            HangoutVenue venue = HangoutData.venues().get(visit.venueDefinition());
            VillagerEntityMCA villager = villager(level, visit.visitor().entity());
            if (policy == null || venue == null) {
                cleanupVisit(level, visit, false, "definition_reloaded", now);
                return;
            }
            if (villager == null) {
                cleanupVisit(level, visit, false, "visitor_missing", now);
                return;
            }
            if (!CLAIMS.renew(visit.id(), now, policy.leaseTicks())) {
                cleanupVisit(level, visit, false, "lease_expired", now);
                return;
            }
            if (!safe(villager)) {
                cleanupVisit(level, visit, false, "unsafe_or_unavailable", now);
                return;
            }
            if (!isMeet(villager)) {
                cleanupVisit(level, visit, visit.phase() == HangoutVisit.Phase.PRESENT,
                        "meet_window_closed", now);
                return;
            }

            if (visit.phase() == HangoutVisit.Phase.TRAVELING) {
                if (distance(villager, visit.visitor().approach()) > ARRIVAL_DISTANCE_SQUARED) {
                    if (now >= visit.deadline()) {
                        cleanupVisit(level, visit, false, "arrival_timeout", now);
                    } else {
                        // MCA's ordinary MEET behaviors also author WALK_TARGET. A bare navigation
                        // command is therefore only advisory: on the following brain tick the
                        // resident resumes village wandering and never reaches the reserved seat.
                        // Keep the visit's intent authoritative for every traveling tick, while
                        // limiting direct path recalculation to the normal retry cadence.
                        ownTravelTarget(villager, visit.visitor().approach(), now % 40L == 0L);
                        traceTravel(villager, visit, now);
                    }
                    return;
                }
                releaseTravelTarget(villager);
                HangoutEmbodiment.Handle handle = HangoutEmbodiment.enter(level, villager,
                        visit.visitor().spot(), visit.visitor().adapter(), visit.visitor().posture(),
                        visit.visitor().embodimentPosition(), visit.id());
                if (handle == null || HangoutEmbodiment.blocked(handle)) {
                    cleanupVisit(level, visit, false, "embodiment_refused", now);
                    return;
                }
                visit.setHandle(handle);
                CLAIMS.releaseKind(visit.id(), "approach");
                visit.arrive(now, departureAt(villager, visit, policy, now));
                traceArrival(villager, visit, now);
            }

            if (visit.phase() != HangoutVisit.Phase.PRESENT) return;
            HangoutEmbodiment.maintain(level, villager, visit.visitor().spot(),
                    visit.visitor().adapter(), visit.visitor().posture(),
                    visit.visitor().embodimentPosition(), visit.id());
            if (now >= visit.deadline()) {
                cleanupVisit(level, visit, true, "individual_departure", now);
                return;
            }

            UUID beatId = BY_BEAT_MEMBER.get(villager.getUUID());
            if (beatId == null && now >= visit.nextBeatAt()) {
                tryStartBeat(level, visit, villager, venue, policy, now);
                beatId = BY_BEAT_MEMBER.get(villager.getUUID());
            }
            HangoutBeat beat = beatId == null ? null : BEATS.get(beatId);
            if (beat == null && beatId != null) BY_BEAT_MEMBER.remove(villager.getUUID(), beatId);
            else if (beat != null) advanceBeat(level, beat, now);
            HangoutDrinks.tick(level, villager, visit, venue, now);
        }
    }

    private static synchronized void tryStartBeat(ServerLevel level, HangoutVisit initiatorVisit,
                                                  VillagerEntityMCA initiator, HangoutVenue venue,
                                                  HangoutPolicy policy, long now) {
        if (BY_BEAT_MEMBER.containsKey(initiator.getUUID())) return;
        List<HangoutVisit> present = VISITS.values().stream()
                .filter(candidate -> candidate != initiatorVisit)
                .filter(candidate -> candidate.phase() == HangoutVisit.Phase.PRESENT)
                .filter(candidate -> candidate.venueDefinition().equals(initiatorVisit.venueDefinition())
                        && candidate.buildingId() == initiatorVisit.buildingId())
                .filter(HangoutEngine::availableForGroupBeat)
                .filter(candidate -> {
                    VillagerEntityMCA member = villager(level, candidate.visitor().entity());
                    return member != null && safe(member) && test(policy.companionWhen(), member)
                            && initiator.distanceToSqr(member) <= (double) policy.socialRadius() * policy.socialRadius();
                })
                .sorted(Comparator.<HangoutVisit>comparingInt(candidate -> {
                    VillagerEntityMCA member = villager(level, candidate.visitor().entity());
                    return member == null ? 0 : -bondScore(initiator, member, policy);
                }).thenComparingDouble(candidate -> {
                    VillagerEntityMCA member = villager(level, candidate.visitor().entity());
                    return member == null ? Double.MAX_VALUE : initiator.distanceToSqr(member);
                }).thenComparing(candidate -> candidate.visitor().entity().toString()))
                .toList();

        for (ResourceLocation activityId : venue.activities()) {
            HangoutActivity activity = HangoutData.activities().get(activityId);
            if (activity == null || !supports(activity, initiatorVisit.visitor().posture())
                    || !test(activity.participantWhen(), initiator)
                    || !test(activity.startWhen(), initiator)) continue;
            List<HangoutVisit> members = new ArrayList<>();
            members.add(initiatorVisit);
            for (HangoutVisit candidate : present) {
                if (members.size() >= activity.maximumParticipants()) break;
                // A solo fallback may yield to an actual social activity, but must never be
                // interrupted merely so another visitor can start a different solo fallback.
                if (activity.minimumParticipants() == 1
                        && BY_BEAT_MEMBER.containsKey(candidate.visitor().entity())) continue;
                VillagerEntityMCA entity = villager(level, candidate.visitor().entity());
                if (entity != null && supports(activity, candidate.visitor().posture())
                        && test(activity.participantWhen(), entity)
                        && test(activity.continueWhen(), entity)) members.add(candidate);
            }
            if (members.size() < activity.minimumParticipants()) continue;

            if (activity.minimumParticipants() > 1) {
                Set<UUID> yieldingBeats = new LinkedHashSet<>();
                for (HangoutVisit memberVisit : members) {
                    UUID existing = BY_BEAT_MEMBER.get(memberVisit.visitor().entity());
                    if (existing != null) yieldingBeats.add(existing);
                }
                for (UUID existing : yieldingBeats) {
                    HangoutBeat solo = BEATS.get(existing);
                    if (solo != null) endBeat(level, solo, true, "yielded_to_group", now);
                }
            }

            Map<UUID, String> roles = assignVisitorRoles(activity, members);
            HangoutBeat beat = new HangoutBeat(UUID.randomUUID(), venue.id(),
                    initiatorVisit.buildingId(), activity.id(), roles,
                    now, now + activity.durationTicks());
            BEATS.put(beat.id(), beat);
            for (HangoutVisit memberVisit : members) {
                UUID memberId = memberVisit.visitor().entity();
                BY_BEAT_MEMBER.put(memberId, beat.id());
                VillagerEntityMCA member = villager(level, memberId);
                if (member == null) continue;
                run(activity.onStart(), member);
                HangoutActivity.Performance cue = activity.performance();
                if (cue != null && !("social".equals(cue.channel()) && connectedConversations(activity.socialCues()))) {
                    PerformanceHandle handle = PerformanceProviders.play(level,
                            new PerformanceRequest(member, cue.id(), cue.channel(), cue.durationTicks(),
                                    cue.priority(), cue.fallback()));
                    if (handle != null) beat.performances().put(memberId, handle);
                }
            }
            traceBeatStart(beat);
            return;
        }
        initiatorVisit.deferBeat(now + BEAT_RETRY_TICKS
                + Math.floorMod(initiator.getUUID().hashCode(), BEAT_RETRY_TICKS));
    }

    private static void advanceBeat(ServerLevel level, HangoutBeat beat, long now) {
        synchronized (beat) {
            if (beat.lastTick() == now) return;
            beat.markTick(now);
            HangoutActivity activity = HangoutData.activities().get(beat.activity());
            HangoutVenue venue = HangoutData.venues().get(beat.venueDefinition());
            if (activity == null || venue == null) {
                endBeat(level, beat, false, "definition_reloaded", now);
                return;
            }

            for (UUID memberId : new ArrayList<>(beat.mutableRoles().keySet())) {
                HangoutVisit visit = visit(memberId);
                VillagerEntityMCA member = villager(level, memberId);
                if (visit == null || visit.phase() != HangoutVisit.Phase.PRESENT || member == null
                        || !safe(member) || !test(activity.participantWhen(), member)
                        || !test(activity.continueWhen(), member)) {
                    removeBeatMember(level, beat, memberId, false, now);
                }
            }
            if (beat.mutableRoles().size() < activity.minimumParticipants()) {
                endBeat(level, beat, false, "below_minimum", now);
                return;
            }
            if (now >= beat.deadline()) {
                endBeat(level, beat, true, "complete", now);
                return;
            }
            if (now % ACTIVE_STRIDE != 0L) return;

            List<VillagerEntityMCA> members = beat.mutableRoles().keySet().stream()
                    .map(id -> villager(level, id)).filter(java.util.Objects::nonNull).toList();
            emitSocialCues(beat, venue, activity, members, now);
            for (int i = 0; i < members.size(); i++) {
                VillagerEntityMCA member = members.get(i);
                member.getNavigation().stop();
                VillagerEntityMCA partner = members.get((i + 1) % members.size());
                if (partner != member && !com.aetherianartificer.townstead.dialogue.conversation.ConversationEngine.active(member.getUUID()))
                    member.getLookControl().setLookAt(partner, 30F, 30F);
                run(activity.onTick(), member);
                serve(level, beat, venue, activity, member, now);
            }
        }
    }

    /**
     * Activities can invite an independent conversation or retain their authored contextual asides.
     * The conversation director owns its participants, topic, turn-taking and outcome.
     */
    private static void emitSocialCues(HangoutBeat beat, HangoutVenue venue,
                                       HangoutActivity activity, List<VillagerEntityMCA> members,
                                       long now) {
        HangoutActivity.SocialCues cues = activity.socialCues();
        if (cues == null || members.isEmpty()) return;
        boolean connected = connectedConversations(cues);
        if (members.size() > 1 && (connected || cues.dialogueIntent() != null)
                && beat.dialogueDue(now, cues.dialogueIntervalTicks())) {
            int speakerIndex = beat.nextDialogueIndex(members.size());
            VillagerEntityMCA speaker = members.get(speakerIndex);
            VillagerEntityMCA counterpart = members.get((speakerIndex + 1) % members.size());
            if (connected) {
                com.aetherianartificer.townstead.dialogue.conversation.ConversationEngine.request(speaker, counterpart, null, false);
            } else {
                Set<String> facts = new LinkedHashSet<>();
                facts.add("hangout"); facts.add("venue:" + venue.id());
                venue.tags().forEach(tag -> facts.add("venue_tag:" + tag));
                DialogueDirector.speak(speaker, cues.dialogueIntent(), facts, Set.of(), counterpart);
            }
        }
        if (!cues.expressions().isEmpty()
                && beat.expressionDue(now, cues.expressionIntervalTicks())) {
            int speakerIndex = beat.nextExpressionIndex(members.size());
            VillagerEntityMCA speaker = members.get(speakerIndex);
            VillagerEntityMCA counterpart = members.size() > 1
                    ? members.get((speakerIndex + 1) % members.size()) : null;
            int cueIndex = Math.floorMod(beat.id().hashCode() + speakerIndex
                    + (int) (now / cues.expressionIntervalTicks()), cues.expressions().size());
            if (!com.aetherianartificer.townstead.dialogue.conversation.ConversationEngine.active(speaker.getUUID()))
                ExpressionService.emit(speaker, cues.expressions().get(cueIndex), counterpart);
        }
    }

    private static boolean connectedConversations(HangoutActivity.SocialCues cues) {
        return cues != null && cues.conversations() && TownsteadConfig.ENABLE_CONVERSATIONS.get()
                && !com.aetherianartificer.townstead.dialogue.conversation.ConversationTopics.all().isEmpty();
    }

    private static void endBeat(ServerLevel level, HangoutBeat beat, boolean success,
                                String reason, long now) {
        if (!BEATS.remove(beat.id(), beat)) return;
        for (UUID member : new ArrayList<>(beat.mutableRoles().keySet())) {
            removeBeatMember(level, beat, member, success, now);
        }
        HOSTED_SERVICE.forget(beat.id());
        if (TownsteadConfig.DEBUG_LOGGING.get()) {
            Townstead.LOGGER.info("[Hangouts] ended beat={} venue={} activity={} success={} reason={} age={}",
                    beat.id(), beat.venueDefinition(), beat.activity(), success, reason, now - beat.startedAt());
        }
    }

    private static void removeBeatMember(ServerLevel level, HangoutBeat beat, UUID memberId,
                                         boolean success, long now) {
        if (beat.mutableRoles().remove(memberId) == null) return;
        BY_BEAT_MEMBER.remove(memberId, beat.id());
        PerformanceHandle performance = beat.performances().remove(memberId);
        if (performance != null) performance.stop();
        VillagerEntityMCA member = villager(level, memberId);
        HangoutActivity activity = HangoutData.activities().get(beat.activity());
        if (success && member != null && activity != null) run(activity.onFinish(), member);
        HangoutVisit visit = visit(memberId);
        if (visit != null && visit.phase() == HangoutVisit.Phase.PRESENT) {
            visit.deferBeat(now + BEAT_RETRY_TICKS
                    + Math.floorMod(memberId.hashCode(), BEAT_RETRY_TICKS));
        }
    }

    private static void cleanupVisit(ServerLevel level, HangoutVisit visit, boolean success,
                                     String reason, long now) {
        if (visit.phase() == HangoutVisit.Phase.COMPLETE
                || visit.phase() == HangoutVisit.Phase.INTERRUPTED) return;
        UUID visitorId = visit.visitor().entity();
        UUID beatId = BY_BEAT_MEMBER.get(visitorId);
        HangoutBeat beat = beatId == null ? null : BEATS.get(beatId);
        if (beat != null) {
            removeBeatMember(level, beat, visitorId, false, now);
            HangoutActivity activity = HangoutData.activities().get(beat.activity());
            if (activity == null || beat.mutableRoles().size() < activity.minimumParticipants()) {
                endBeat(level, beat, false, "visitor_departed", now);
            }
        } else if (beatId != null) {
            BY_BEAT_MEMBER.remove(visitorId, beatId);
        }

        VillagerEntityMCA villager = villager(level, visitorId);
        if (villager != null) {
            com.aetherianartificer.townstead.hunger.VillagerConsumptionManager.finishRecreationalDrink(villager);
            releaseTravelTarget(villager);
            if (visit.visitor().handle() != null) {
                visit.visitor().handle().close(level, villager);
            }
        }
        BY_VISITOR.remove(visitorId, visit.id());
        CLAIMS.release(visit.id());
        VISITS.remove(visit.id(), visit);
        com.aetherianartificer.townstead.api.impl.v1.ApiEvents.hangoutEnded(villager, visitorId, visit, success);
        HangoutPolicy policy = HangoutData.policies().get(visit.policy());
        if (policy != null) {
            COOLDOWNS.put(visitorId, now + (success
                    ? policy.revisitCooldownTicks() : policy.retryCooldownTicks()));
        }
        if (success) visit.complete(reason); else visit.interrupt(reason);
        HangoutEmbodiment.recoverNearby(level, visit.venueAnchor());
        if (TownsteadConfig.DEBUG_LOGGING.get()) {
            Townstead.LOGGER.info("[Hangouts] ended visit={} visitor={} venue={} success={} reason={} age={} pos={}",
                    visit.id(), visitorId, visit.venueDefinition(), success, reason,
                    now - visit.createdAt(), villager == null ? "missing" : villager.blockPosition().toShortString());
        }
    }

    private static long departureAt(VillagerEntityMCA villager, HangoutVisit visit,
                                    HangoutPolicy policy, long now) {
        long meetEnd = contiguousMeetEnd(villager, now);
        long earliest = Math.min(meetEnd, now + policy.minimumVisitTicks());
        long latest = Math.min(meetEnd, now + policy.maximumVisitTicks());
        if (latest <= earliest) return latest;
        long span = latest - earliest;
        int salt = java.util.Objects.hash(villager.getUUID(), visit.venueDefinition(),
                villager.level().getDayTime() / 24000L);
        return earliest + Math.floorMod(salt, (int) Math.min(Integer.MAX_VALUE, span + 1L));
    }

    private static long arrivalDeadline(VillagerEntityMCA villager, BlockPos approach,
                                        HangoutPolicy policy, long now) {
        // The authored timeout remains the minimum. Long village trips receive enough time for
        // ordinary doors, slopes and partial-path replanning, while the MEET-window check still
        // ends travel immediately when that resident's schedule changes.
        long distanceAllowance = (long) Math.ceil(Math.sqrt(distance(villager, approach)) * 12D) + 200L;
        return now + Math.max(policy.arrivalTimeoutTicks(), distanceAllowance);
    }

    private static long contiguousMeetEnd(VillagerEntityMCA villager, long now) {
        long dayTime = villager.level().getDayTime();
        for (int offset = 1; offset <= 24000; offset++) {
            int time = (int) Math.floorMod(dayTime + offset, 24000L);
            if (villager.getBrain().getSchedule().getActivityAt(time) != Activity.MEET) return now + offset;
        }
        return now + 24000L;
    }

    private static Map<UUID, String> assignVisitorRoles(HangoutActivity activity,
                                                         List<HangoutVisit> visitors) {
        Map<String, Integer> remaining = new LinkedHashMap<>(activity.roles());
        Map<UUID, String> assigned = new LinkedHashMap<>();
        for (HangoutVisit visit : visitors) assigned.put(visit.visitor().entity(), takeRole(remaining));
        return assigned;
    }

    private static String takeRole(Map<String, Integer> remaining) {
        for (Map.Entry<String, Integer> role : remaining.entrySet()) {
            if (role.getValue() < 1) continue;
            remaining.put(role.getKey(), role.getValue() - 1);
            return role.getKey();
        }
        return "participant";
    }

    private static void serve(ServerLevel level, HangoutBeat beat, HangoutVenue venue,
                              HangoutActivity activity, VillagerEntityMCA guest, long now) {
        long elapsed = now - beat.startedAt();
        if (activity.serviceCourses().isEmpty()) {
            if (beat.simpleServiceAttempted(guest.getUUID())
                    || elapsed < activity.durationTicks() / 2L) return;
            HangoutVisit visit = visit(guest.getUUID());
            boolean accepted = visit != null && test(activity.serviceWhen(), guest)
                    && useHospitality(level, guest, activity.kind(), visit.venueAnchor());
            run(accepted ? activity.onServiceAccepted() : activity.onServiceRefused(), guest);
            beat.markSimpleServiceAttempted(guest.getUUID());
            return;
        }
        HangoutVisit visit = visit(guest.getUUID());
        if (visit == null) return;
        for (int index = 0; index < activity.serviceCourses().size(); index++) {
            HangoutActivity.ServiceCourse course = activity.serviceCourses().get(index);
            UUID worker = workerFor(level, venue, beat.buildingId(), course.role(), guest);
            Amenities.Candidate amenity = hospitalityCandidate(
                    level, guest, course.kind(), visit.venueAnchor());
            HostedServiceCoordinator.Result result = HOSTED_SERVICE.attempt(level.dimension().location(),
                    beat.id(), venue.id() + "/" + beat.buildingId(), guest.getUUID(), worker,
                    course, index, elapsed, now, test(activity.serviceWhen(), guest),
                    test(course.eligibleWhen(), guest), amenity != null,
                    () -> amenity != null && Amenities.use(level, guest, amenity));
            if (result.terminal() && TownsteadConfig.DEBUG_LOGGING.get()) {
                Townstead.LOGGER.info("[Hangouts] service result beat={} guest={} course={} status={} reason={}",
                        beat.id(), guest.getName().getString(), course.id(), result.status(), result.reason());
            }
            if (result.status() == HostedServiceCoordinator.Status.ACCEPTED) {
                run(activity.onServiceAccepted(), guest);
            } else if (result.status() == HostedServiceCoordinator.Status.REFUSED) {
                run(activity.onServiceRefused(), guest);
            } else if (result.status() == HostedServiceCoordinator.Status.MISSING_AMENITY
                    || result.status() == HostedServiceCoordinator.Status.MISSING_SERVER) {
                run(activity.onServiceMissing(), guest);
            }
            if (result.terminal()) return;
        }
    }

    private static @Nullable UUID workerFor(ServerLevel level, HangoutVenue venue, int buildingId,
                                             String role, VillagerEntityMCA guest) {
        Condition condition = venue.staffRoles().get(role);
        if (condition == null) return null;
        return level.getEntitiesOfClass(VillagerEntityMCA.class,
                        new AABB(guest.blockPosition()).inflate(32D), worker -> worker != guest
                                && worker.isAlive() && !worker.isRemoved()
                                && !BY_VISITOR.containsKey(worker.getUUID()) && test(condition, worker)
                                && assignedToBuilding(level, worker, buildingId))
                .stream().min(Comparator.<VillagerEntityMCA>comparingDouble(guest::distanceToSqr)
                        .thenComparing(worker -> worker.getUUID().toString()))
                .map(VillagerEntityMCA::getUUID).orElse(null);
    }

    private static boolean isVenueStaff(ServerLevel level, HangoutVenue venue, int buildingId,
                                        VillagerEntityMCA villager) {
        return assignedToBuilding(level, villager, buildingId)
                && venue.staffRoles().values().stream().anyMatch(condition -> test(condition, villager));
    }

    private static boolean assignedToBuilding(ServerLevel level, VillagerEntityMCA villager,
                                              int buildingId) {
        ResourceLocation raw = ProfessionIdentity.rawId(villager);
        ProfessionDef def = ProfessionDefs.byId(ProfessionDefs.canonicalId(raw));
        if (def == null) return false;
        return ProfessionSites.serviceSite(level, villager, def)
                .map(ProfessionSites.Site::building)
                .map(building -> building.getId() == buildingId)
                .orElse(false);
    }

    private static boolean useHospitality(ServerLevel level, VillagerEntityMCA villager,
                                          HangoutActivity.Kind kind, BlockPos venueAnchor) {
        if (kind == HangoutActivity.Kind.SOCIALIZE) return true;
        Amenities.Candidate selected = hospitalityCandidate(level, villager, kind, venueAnchor);
        return selected != null && Amenities.use(level, villager, selected);
    }

    private static @Nullable Amenities.Candidate hospitalityCandidate(ServerLevel level,
                                                                       VillagerEntityMCA villager,
                                                                       HangoutActivity.Kind kind,
                                                                       BlockPos venue) {
        if (kind == HangoutActivity.Kind.SOCIALIZE) return null;
        return Amenities.candidates(level, villager).stream()
                .filter(candidate -> candidate.pos().distSqr(venue) <= 256D)
                .filter(candidate -> candidate.pos().distSqr(villager.blockPosition()) <= 16D)
                .filter(candidate -> switch (kind) {
                    case EAT -> candidate.feeds(level);
                    case DRINK -> candidate.hydrates(level);
                    case MIXED -> candidate.feeds(level) || candidate.hydrates(level);
                    default -> false;
                })
                .min(Comparator.comparingDouble(candidate ->
                        candidate.pos().distSqr(villager.blockPosition()))).orElse(null);
    }

    private static List<VenueCandidate> discover(ServerLevel level, VillagerEntityMCA villager, int radius) {
        Village village = villager.getResidency().getHomeVillage()
                .orElseGet(() -> Village.findNearest(villager).orElse(null));
        if (village == null) return List.of();
        List<VenueCandidate> out = new ArrayList<>();
        for (HangoutVenue venue : HangoutData.venues().values()) {
            for (Building building : McaBuildings.all(village)) {
                if (!building.isComplete() || !venue.buildings().contains(building.getType())) continue;
                BlockPos center = building.getCenter();
                if (center == null || center.distSqr(villager.blockPosition()) > (double) radius * radius) continue;
                List<SpotCandidate> spots = scanSpots(level, building);
                if (!spots.isEmpty()) out.add(new VenueCandidate(venue, village, building,
                        center.immutable(), spots));
            }
        }
        out.sort(Comparator.comparingDouble(candidate ->
                candidate.anchor().distSqr(villager.blockPosition())));
        return out;
    }

    /**
     * Produces a weighted order without replacement. Every discovered venue retains a positive
     * chance: personality and distance are preferences, never admission conditions. The gentle
     * distance factor keeps a resident from habitually crossing an enormous village for two
     * otherwise identical venues without letting proximity erase a strong personality affinity.
     */
    private static List<VenueCandidate> preferredVenueOrder(List<VenueCandidate> discovered,
                                                             VillagerEntityMCA villager,
                                                             HangoutPolicy policy) {
        if (discovered.size() < 2) return discovered;
        List<VenueCandidate> remaining = new ArrayList<>(discovered);
        List<VenueCandidate> ordered = new ArrayList<>(discovered.size());
        String personality = basePersonalityKey(villager);
        Map<VenueCandidate, Double> weights = new LinkedHashMap<>();
        for (VenueCandidate candidate : discovered)
            weights.put(candidate, venueSelectionWeight(candidate, villager, policy, personality));
        while (!remaining.isEmpty()) {
            double total = 0D;
            for (VenueCandidate candidate : remaining) {
                total += weights.get(candidate);
            }
            double roll = villager.getRandom().nextDouble() * total;
            int selected = remaining.size() - 1;
            for (int index = 0; index < remaining.size(); index++) {
                roll -= weights.get(remaining.get(index));
                if (roll < 0D) {
                    selected = index;
                    break;
                }
            }
            ordered.add(remaining.remove(selected));
        }
        return List.copyOf(ordered);
    }

    private static double venueSelectionWeight(VenueCandidate candidate,
                                                VillagerEntityMCA villager,
                                                HangoutPolicy policy,
                                                String personality) {
        double blocks = Math.sqrt(candidate.anchor().distSqr(villager.blockPosition()));
        double distancePreference = 0.55D + 0.45D / (1D + blocks / 64D);
        double thermal = 1;
        if (TownsteadConfig.isVillagerTemperatureEnabled()
                && !com.aetherianartificer.townstead.root.needs.NeedSuppression.suppressesTemperature(villager)) {
            var needs = com.aetherianartificer.townstead.villager.TownsteadVillagers.get(villager).needs();
            // Sample furniture, not a building center that may be inside a wall or on another floor.
            float bestLoad = Float.POSITIVE_INFINITY;
            int samples = 0;
            for (SpotCandidate spot : candidate.spots()) {
                if (!supportsAnyActivity(candidate.definition(), spot.definition().posture())) continue;
                float load = destinationComfort((ServerLevel) villager.level(), villager, spot.anchor());
                if (Math.abs(load) < Math.abs(bestLoad)) bestLoad = load;
                if (++samples >= 4) break;
            }
            thermal = HangoutPreferences.thermalWeight(needs.comfortLoad(), bestLoad);
        }
        return HangoutPreferences.affinity(candidate.definition(), policy, personality) * distancePreference * thermal;
    }

    private static float destinationComfort(ServerLevel level, VillagerEntityMCA villager, BlockPos pos) {
        var needs = com.aetherianartificer.townstead.villager.TownsteadVillagers.get(villager).needs();
        var profile = com.aetherianartificer.townstead.temperature.ThermalProfile.of(villager);
        float ambient = com.aetherianartificer.townstead.temperature.TemperatureData.ambientCelsius(level, pos);
        // Covered seats allow wet visitors to dry during the visit; exposed rain keeps soaking them.
        float wetness = level.isRainingAt(pos.above()) ? 1 : level.canSeeSky(pos.above()) ? needs.thermalWetness() : 0;
        return com.aetherianartificer.townstead.temperature.ThermalComfort.load(ambient, wetness, false, 0,
                com.aetherianartificer.townstead.temperature.Insulation.clothingProtection(villager), profile);
    }

    private static String basePersonalityKey(VillagerEntityMCA villager) {
        return McaPersonalityCompat.id(villager.getVillagerBrain().getPersonality())
                .toLowerCase(Locale.ROOT);
    }

    private static List<SpotCandidate> scanSpots(ServerLevel level, Building building) {
        List<SpotCandidate> out = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (BlockPos raw : (Iterable<BlockPos>) building.getBlockPosStream()::iterator) {
            if (!level.isLoaded(raw)) continue;
            BlockState state = level.getBlockState(raw);
            ResourceLocation block = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            List<HangoutSpot> definitions = HangoutSpot.candidatesForBlock(HangoutData.spots().values(), block);
            // A broad compatibility tag must not steal a block from its native furniture adapter.
            // Exact authored definitions (for example Beachparty chairs) always get first refusal.
            for (HangoutSpot definition : definitions) {
                if (!matches(definition, block, state)) continue;
                if (definition.availableWhen() != null && !definition.availableWhen().test(level, raw)) continue;
                BlockPos anchor = raw.offset(definition.canonicalOffset()).immutable();
                if (seen.add(anchor.asLong())) out.add(new SpotCandidate(definition, anchor));
                break;
            }
        }
        return out;
    }

    private static boolean matches(HangoutSpot definition, ResourceLocation id, BlockState state) {
        if (definition.blocks().contains(id)) return true;
        for (ResourceLocation tag : definition.blockTags()) {
            if (state.is(TagKey.create(Registries.BLOCK, tag))) return true;
        }
        return false;
    }

    private static boolean supportsAnyActivity(HangoutVenue venue, ResourceLocation posture) {
        for (ResourceLocation id : venue.activities()) {
            HangoutActivity activity = HangoutData.activities().get(id);
            if (activity != null && supports(activity, posture)) return true;
        }
        return false;
    }

    private static boolean supports(HangoutActivity activity, ResourceLocation posture) {
        return activity.postures().isEmpty() || activity.postures().contains(posture);
    }

    private static boolean availableForGroupBeat(HangoutVisit visit) {
        UUID memberId = visit.visitor().entity();
        UUID beatId = BY_BEAT_MEMBER.get(memberId);
        if (beatId == null) return true;
        HangoutBeat beat = BEATS.get(beatId);
        if (beat == null) {
            BY_BEAT_MEMBER.remove(memberId, beatId);
            return true;
        }
        HangoutActivity current = HangoutData.activities().get(beat.activity());
        return current != null && current.yieldToGroups() && beat.mutableRoles().size() == 1;
    }

    private static @Nullable BlockPos findApproach(ServerLevel level, VillagerEntityMCA villager,
                                                    BlockPos seat, UUID visitId, long now) {
        List<BlockPos> candidates = new ArrayList<>();
        int[][] horizontal = {{1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int dy : new int[]{0, -1, 1}) {
            for (int[] offset : horizontal) {
                BlockPos candidate = seat.offset(offset[0], dy, offset[1]).immutable();
                if (canOccupy(level, villager, candidate)) candidates.add(candidate);
            }
        }
        candidates.sort(Comparator.comparingDouble(candidate -> distance(villager, candidate)));
        String dimension = level.dimension().location().toString();
        return HangoutApproaches.select(candidates,
                candidate -> CLAIMS.available(new HangoutClaims.Key(
                        dimension, "approach", Long.toString(candidate.asLong())), visitId, now),
                candidate -> {
                    if (distance(villager, candidate) <= ARRIVAL_DISTANCE_SQUARED)
                        return HangoutApproaches.Reachability.REACHABLE;
                    // Arrival accepts a resident within roughly one block of the reserved approach.
                    // Requiring the pathfinder to land on the exact block was stricter than embodiment
                    // and rejected otherwise usable seats at real, uneven village venues.
                    Path path = villager.getNavigation().createPath(candidate, 1);
                    if (path != null && path.canReach()) return HangoutApproaches.Reachability.REACHABLE;
                    // A non-null partial path is still useful for a village-scale trip. The visit keeps
                    // the final approach as its authoritative target and recalculates every 40 ticks;
                    // after walking the partial path, the next path commonly reaches the remaining leg.
                    if (path != null && path.getEndNode() != null
                            && usefulPartialApproach(villager.blockPosition(), candidate, path.getEndNode().asBlockPos()))
                        return HangoutApproaches.Reachability.PARTIAL;
                    return HangoutApproaches.Reachability.UNREACHABLE;
                });
    }

    static boolean usefulPartialApproach(BlockPos start, BlockPos target, BlockPos end) {
        // Do not reserve upstairs seats when the partial path stops on the floor below.
        return HangoutPreferences.usefulPartialApproach(start.distSqr(target), end.distSqr(target),
                end.getY() - target.getY());
    }

    private static boolean canOccupy(ServerLevel level, VillagerEntityMCA villager, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;
        BlockPos support = pos.below();
        if (level.getBlockState(support).getCollisionShape(level, support).isEmpty()
                || !level.getFluidState(pos).isEmpty() || !level.getFluidState(support).isEmpty()) return false;
        AABB moved = villager.getBoundingBox().move(
                pos.getX() + 0.5D - villager.getX(), pos.getY() - villager.getY(),
                pos.getZ() + 0.5D - villager.getZ());
        return level.noCollision(villager, moved);
    }

    private static boolean available(VillagerEntityMCA villager) {
        return safe(villager) && !villager.isPassenger() && isMeet(villager);
    }

    private static String availabilityReason(VillagerEntityMCA villager) {
        if (!villager.isAlive() || villager.isRemoved()) return "not alive";
        if (villager.isSleeping()) return "sleeping";
        if (villager.isPassenger()) return "already riding";
        if (villager.getVillagerBrain().isPanicking()) return "panicking";
        if (recentlyAttacked(villager)) return "was attacked in the last " + DANGER_GRACE_TICKS + " ticks";
        if (!isMeet(villager)) return "schedule is not MEET";
        return "unavailable";
    }

    private static boolean safe(VillagerEntityMCA villager) {
        return villager.isAlive() && !villager.isRemoved() && !villager.isSleeping()
                && !(TownsteadConfig.isVillagerTemperatureEnabled()
                    && com.aetherianartificer.townstead.villager.TownsteadVillagers.get(villager).needs().seekingRelief())
                && !villager.getVillagerBrain().isPanicking() && !recentlyAttacked(villager);
    }

    private static boolean recentlyAttacked(VillagerEntityMCA villager) {
        if (villager.getLastHurtByMob() == null) return false;
        return villager.tickCount - villager.getLastHurtByMobTimestamp() < DANGER_GRACE_TICKS;
    }

    private static boolean isMeet(VillagerEntityMCA villager) {
        int time = (int) (villager.level().getDayTime() % 24000L);
        return villager.getBrain().getSchedule().getActivityAt(time) == Activity.MEET;
    }

    private static int bondScore(VillagerEntityMCA first, VillagerEntityMCA second,
                                 HangoutPolicy policy) {
        int score = 0;
        for (Bond bond : Bonds.of(first).all()) {
            if (second.getUUID().equals(bond.other()) && bond.active()) {
                score += policy.bondWeights().getOrDefault(bond.kind(), 0);
            }
        }
        for (Bond bond : Bonds.of(second).all()) {
            if (first.getUUID().equals(bond.other()) && bond.active()) {
                score += policy.bondWeights().getOrDefault(bond.kind(), 0);
            }
        }
        return score;
    }

    private static boolean test(@Nullable Condition condition, VillagerEntityMCA villager) {
        return condition == null || condition.test(new ConditionContext(villager));
    }

    private static void run(@Nullable Action action, VillagerEntityMCA villager) {
        if (action != null) action.run(new ActionContext(villager));
    }

    private static void ownTravelTarget(VillagerEntityMCA villager, BlockPos target,
                                        boolean recalculatePath) {
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(Vec3.atBottomCenterOf(target), 0.55F, 0));
        if (recalculatePath || villager.getNavigation().isDone()) {
            villager.getNavigation().moveTo(target.getX() + 0.5D, target.getY(),
                    target.getZ() + 0.5D, 0.55D);
        }
    }

    private static void releaseTravelTarget(VillagerEntityMCA villager) {
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getNavigation().stop();
    }

    private static double distance(VillagerEntityMCA villager, BlockPos target) {
        return villager.distanceToSqr(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D);
    }

    private static @Nullable VillagerEntityMCA villager(ServerLevel level, UUID id) {
        Entity entity = level.getEntity(id);
        return entity instanceof VillagerEntityMCA villager ? villager : null;
    }

    private static void traceVisitStart(VillagerEntityMCA villager, HangoutVisit visit,
                                        HangoutVenue venue, HangoutPolicy policy, long now) {
        if (!TownsteadConfig.DEBUG_LOGGING.get()) return;
        LAST_DIAGNOSTIC.put(villager.getUUID(), now);
        String personality = basePersonalityKey(villager);
        Townstead.LOGGER.info("[Hangouts] visit started id={} visitor={} venue={} personality={} tags={} affinity={} spot={} approach={}",
                visit.id(), villager.getName().getString(), visit.venueDefinition(),
                personality, venue.tags(),
                String.format(Locale.ROOT, "%.2f", HangoutPreferences.affinity(venue, policy, personality)),
                visit.visitor().spot().toShortString(), visit.visitor().approach().toShortString());
    }

    private static void traceArrival(VillagerEntityMCA villager, HangoutVisit visit, long now) {
        if (!TownsteadConfig.DEBUG_LOGGING.get()) return;
        Townstead.LOGGER.info("[Hangouts] visitor arrived id={} visitor={} venue={} travelTicks={} departIn={}",
                visit.id(), villager.getName().getString(), visit.venueDefinition(),
                now - visit.createdAt(), visit.deadline() - now);
    }

    private static void traceTravel(VillagerEntityMCA villager, HangoutVisit visit, long now) {
        if (!TownsteadConfig.DEBUG_LOGGING.get() || (now - visit.createdAt()) % 100L != 0L) return;
        Townstead.LOGGER.info(
                "[Hangouts] visitor traveling id={} visitor={} pos={} approach={} distance={} navigationDone={} ownsWalkTarget={}",
                visit.id(), villager.getName().getString(), villager.blockPosition().toShortString(),
                visit.visitor().approach().toShortString(),
                String.format(java.util.Locale.ROOT, "%.2f", Math.sqrt(distance(villager, visit.visitor().approach()))),
                villager.getNavigation().isDone(), villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET));
    }

    private static void traceBeatStart(HangoutBeat beat) {
        if (!TownsteadConfig.DEBUG_LOGGING.get()) return;
        Townstead.LOGGER.info("[Hangouts] beat started id={} venue={} activity={} roles={}",
                beat.id(), beat.venueDefinition(), beat.activity(), beat.roles());
    }

    private static void traceRejection(VillagerEntityMCA villager, long now, String reason) {
        if (!TownsteadConfig.DEBUG_LOGGING.get()) return;
        long previous = LAST_DIAGNOSTIC.getOrDefault(villager.getUUID(), Long.MIN_VALUE / 2);
        if (now - previous < 1200L) return;
        LAST_DIAGNOSTIC.put(villager.getUUID(), now);
        Townstead.LOGGER.info("[Hangouts] no visit visitor={} uuid={} pos={} dayTime={} reason={}",
                villager.getName().getString(), villager.getUUID(),
                villager.blockPosition().toShortString(), villager.level().getDayTime() % 24000L, reason);
    }
}
