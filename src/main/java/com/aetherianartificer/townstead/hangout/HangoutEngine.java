package com.aetherianartificer.townstead.hangout;

import com.aetherianartificer.townstead.compat.mca.McaBuildings;
import com.aetherianartificer.townstead.needs.Amenities;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.performance.PerformanceHandle;
import com.aetherianartificer.townstead.performance.PerformanceProviders;
import com.aetherianartificer.townstead.performance.PerformanceRequest;
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
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mod-neutral resident session coordinator. It deliberately owns joint lifecycle and claims while
 * delegating posture, animation and consumption to their existing public boundaries.
 */
public final class HangoutEngine {
    private static final int ACQUISITION_STRIDE = 200;
    private static final int ACTIVE_STRIDE = 20;
    private static final double ARRIVAL_DISTANCE_SQUARED = 2.75D;
    private static final HangoutClaims CLAIMS = new HangoutClaims();
    private static final HostedServiceCoordinator HOSTED_SERVICE = new HostedServiceCoordinator();
    private static final Map<UUID, HangoutSession> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> BY_PARTICIPANT = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> COOLDOWNS = new ConcurrentHashMap<>();

    private record VenueCandidate(HangoutVenue definition, Village village, Building building,
                                  BlockPos anchor, List<SpotCandidate> spots) {}
    private record SpotCandidate(HangoutSpot definition, BlockPos anchor) {}

    private HangoutEngine() {}

    public static boolean isLive(UUID session) {
        HangoutSession value = SESSIONS.get(session);
        return value != null && value.phase() != HangoutSession.Phase.COMPLETE
                && value.phase() != HangoutSession.Phase.INTERRUPTED;
    }

    public static @Nullable HangoutSession session(UUID participant) {
        UUID session = BY_PARTICIPANT.get(participant);
        return session == null ? null : SESSIONS.get(session);
    }

    /** Data-authored fatigue recovery for the participant's active rest surface, or zero. */
    public static float restRecovery(UUID participant) {
        HangoutSession current = session(participant);
        if (current == null || current.phase() != HangoutSession.Phase.ACTIVE) return 0F;
        HangoutSession.Participant member = current.participants().get(participant);
        return member == null || member.rest() == null ? 0F : member.rest().fatigueRecovery();
    }

    public static void onReload() {
        // Active sessions keep only ids and will cleanly stop if a referenced definition vanished.
    }

    /** Called from the shared villager dispatcher; only one participant advances a session per tick. */
    public static void tick(VillagerEntityMCA villager) {
        if (!(villager.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        HangoutEmbodiment.bootstrap();
        if ((now + (villager.getUUID().hashCode() & 31)) % 400L == 0L) {
            HangoutEmbodiment.recoverNearby(level, villager.blockPosition());
            CLAIMS.prune(now);
            HOSTED_SERVICE.prune(now);
            COOLDOWNS.entrySet().removeIf(entry -> entry.getValue() <= now);
        }

        UUID sessionId = BY_PARTICIPANT.get(villager.getUUID());
        if (sessionId != null) {
            HangoutSession active = SESSIONS.get(sessionId);
            if (active == null) BY_PARTICIPANT.remove(villager.getUUID(), sessionId);
            else advance(level, active, now);
            return;
        }

        if (HangoutData.policies().isEmpty() || HangoutData.venues().isEmpty()
                || HangoutData.activities().isEmpty() || HangoutData.spots().isEmpty()) return;
        if ((now + (villager.getUUID().hashCode() & Integer.MAX_VALUE)) % ACQUISITION_STRIDE != 0L) return;
        if (COOLDOWNS.getOrDefault(villager.getUUID(), 0L) > now || !available(villager)) return;
        tryStart(level, villager, now);
    }

    public static void forget(VillagerEntityMCA villager) {
        UUID sessionId = BY_PARTICIPANT.get(villager.getUUID());
        HangoutSession session = sessionId == null ? null : SESSIONS.get(sessionId);
        if (session != null && villager.level() instanceof ServerLevel level) {
            cleanup(level, session, false, "participant_removed", level.getGameTime());
        }
    }

    private static synchronized void tryStart(ServerLevel level, VillagerEntityMCA initiator, long now) {
        if (BY_PARTICIPANT.containsKey(initiator.getUUID())) return;
        for (HangoutPolicy policy : HangoutData.policies().values()) {
            if (!test(policy.initiatorWhen(), initiator)) continue;
            List<VenueCandidate> venues = discover(level, initiator, policy.venueRadius());
            for (VenueCandidate venue : venues) {
                if (!test(venue.definition().openWhen(), initiator)) continue;
                for (ResourceLocation activityId : venue.definition().activities()) {
                    HangoutActivity activity = HangoutData.activities().get(activityId);
                    if (activity == null || !test(activity.startWhen(), initiator)) continue;
                    List<VillagerEntityMCA> group = formGroup(level, initiator, policy, activity);
                    if (group.size() < activity.minimumParticipants()) continue;
                    int count = Math.min(Math.min(group.size(), activity.maximumParticipants()), venue.definition().capacity());
                    if (count < Math.max(policy.minimumGroup(), activity.minimumParticipants())) continue;
                    group = new ArrayList<>(group.subList(0, count));
                    List<SpotCandidate> spots = compatibleSpots(venue.spots(), activity);
                    if (spots.stream().mapToInt(spot -> spot.definition().capacity()).sum() < count) continue;
                    HangoutSession session = claimSession(level, venue, activity, policy, group, spots, now);
                    if (session != null) {
                        SESSIONS.put(session.id(), session);
                        for (UUID participant : session.participants().keySet()) BY_PARTICIPANT.put(participant, session.id());
                        for (HangoutSession.Participant participant : session.participants().values()) {
                            VillagerEntityMCA entity = villager(level, participant.entity());
                            if (entity != null) moveTo(entity, participant.spot());
                        }
                        return;
                    }
                }
            }
        }
    }

    private static @Nullable HangoutSession claimSession(ServerLevel level, VenueCandidate venue,
                                                          HangoutActivity activity, HangoutPolicy policy,
                                                          List<VillagerEntityMCA> group,
                                                          List<SpotCandidate> spots, long now) {
        UUID sessionId = UUID.randomUUID();
        String dimension = level.dimension().location().toString();
        List<HangoutClaims.Key> keys = new ArrayList<>();
        for (VillagerEntityMCA member : group) {
            keys.add(new HangoutClaims.Key(dimension, "participant", member.getUUID().toString()));
        }
        String venueKey = venue.definition().id() + "/" + venue.village().getId() + "/" + venue.building().getId();
        int venueSlots = 0;
        for (int slot = 0; slot < venue.definition().capacity() && venueSlots < group.size(); slot++) {
            HangoutClaims.Key key = new HangoutClaims.Key(dimension, "venue", venueKey + "#" + slot);
            if (CLAIMS.available(key, sessionId, now)) { keys.add(key); venueSlots++; }
        }
        if (venueSlots < group.size()) return null;

        Map<UUID, HangoutSession.Participant> participants = new LinkedHashMap<>();
        Map<String, Integer> remainingRoles = new LinkedHashMap<>(activity.roles());
        int assigned = 0;
        for (SpotCandidate spot : spots) {
            for (int slot = 0; slot < spot.definition().capacity() && assigned < group.size(); slot++) {
                String spotValue = venueKey + "/" + spot.anchor().asLong() + "#" + slot;
                HangoutClaims.Key spotKey = new HangoutClaims.Key(dimension, "spot", spotValue);
                if (!CLAIMS.available(spotKey, sessionId, now)) continue;
                List<HangoutClaims.Key> spotKeys = new ArrayList<>();
                spotKeys.add(spotKey);
                boolean linkedFree = true;
                BlockState spotState = level.getBlockState(spot.anchor());
                for (BlockPos linkedPos : spot.definition().linkedPositions(spotState, spot.anchor())) {
                    HangoutClaims.Key linked = new HangoutClaims.Key(dimension, "linked_spot",
                            venueKey + "/" + linkedPos.asLong());
                    if (!CLAIMS.available(linked, sessionId, now)) { linkedFree = false; break; }
                    spotKeys.add(linked);
                }
                if (!linkedFree) continue;
                keys.addAll(spotKeys);
                VillagerEntityMCA member = group.get(assigned++);
                participants.put(member.getUUID(), new HangoutSession.Participant(member.getUUID(),
                        takeRole(remainingRoles), spot.anchor(), spot.definition().posture(),
                        spot.definition().adapter(), spot.definition().embodimentPosition(spotState, spot.anchor()),
                        spot.definition().rest(), null, false));
            }
            if (assigned == group.size()) break;
        }
        if (assigned < group.size() || !CLAIMS.tryClaimAll(sessionId, keys, now, policy.leaseTicks())) return null;
        return new HangoutSession(sessionId, level.dimension().location(), venue.definition().id(),
                venue.building().getId(), activity.id(), policy.id(), venue.anchor(), participants,
                now, now + policy.arrivalTimeoutTicks());
    }

    private static void advance(ServerLevel level, HangoutSession session, long now) {
        synchronized (session) {
            if (session.lastTick() == now) return;
            session.markTick(now);
            HangoutPolicy policy = HangoutData.policies().get(session.policy());
            HangoutActivity activity = HangoutData.activities().get(session.activity());
            if (policy == null || activity == null) {
                cleanup(level, session, false, "definition_reloaded", now);
                return;
            }
            if (!CLAIMS.renew(session.id(), now, policy.leaseTicks())) {
                cleanup(level, session, false, "lease_expired", now);
                return;
            }
            List<VillagerEntityMCA> members = liveMembers(level, session);
            if (members.size() < activity.minimumParticipants()) {
                cleanup(level, session, false, "participant_lost", now);
                return;
            }
            for (VillagerEntityMCA member : members) {
                if (!continuing(member) || !test(activity.continueWhen(), member)) {
                    cleanup(level, session, false, "interrupted", now);
                    return;
                }
            }

            if (session.phase() == HangoutSession.Phase.TRAVELING) {
                boolean arrived = true;
                for (VillagerEntityMCA member : members) {
                    HangoutSession.Participant participant = session.mutableParticipants().get(member.getUUID());
                    if (participant == null) continue;
                    if (distance(member, participant.spot()) > ARRIVAL_DISTANCE_SQUARED) {
                        arrived = false;
                        if (now % 40L == 0L) moveTo(member, participant.spot());
                    }
                }
                if (arrived) {
                    if (!activate(level, session, activity, members, now)) {
                        cleanup(level, session, false, "embodiment_refused", now);
                    }
                } else if (now >= session.deadline()) {
                    cleanup(level, session, false, "arrival_timeout", now);
                }
                return;
            }

            if (session.phase() != HangoutSession.Phase.ACTIVE) return;
            for (VillagerEntityMCA member : members) {
                HangoutSession.Participant participant = session.mutableParticipants().get(member.getUUID());
                if (participant == null) continue;
                HangoutEmbodiment.maintain(level, member, participant.spot(), participant.adapter(),
                        participant.posture(), participant.embodimentPosition(), session.id());
            }
            if (now % ACTIVE_STRIDE == 0L) {
                for (int i = 0; i < members.size(); i++) {
                    VillagerEntityMCA member = members.get(i);
                    member.getNavigation().stop();
                    VillagerEntityMCA partner = members.get((i + 1) % members.size());
                    if (partner != member) member.getLookControl().setLookAt(partner, 30.0F, 30.0F);
                    run(activity.onTick(), member);
                    HangoutSession.Participant participant = session.mutableParticipants().get(member.getUUID());
                    if (participant != null) serve(level, session, activity, member, participant, now);
                }
            }
            if (now >= session.deadline()) cleanup(level, session, true, "complete", now);
        }
    }

    private static boolean activate(ServerLevel level, HangoutSession session, HangoutActivity activity,
                                    List<VillagerEntityMCA> members, long now) {
        for (VillagerEntityMCA member : members) {
            HangoutSession.Participant participant = session.mutableParticipants().get(member.getUUID());
            member.getNavigation().stop();
            HangoutEmbodiment.Handle posture = HangoutEmbodiment.enter(level, member, participant.spot(),
                    participant.adapter(), participant.posture(), participant.embodimentPosition(), session.id());
            if (HangoutEmbodiment.blocked(posture)) return false;
            HangoutActivity.Performance cue = activity.performance();
            PerformanceHandle performance = cue == null ? null : PerformanceProviders.play(level,
                    new PerformanceRequest(member, cue.id(), cue.channel(), cue.durationTicks(),
                            cue.priority(), cue.fallback()));
            HangoutEmbodiment.Handle combined = combine(posture, performance);
            session.mutableParticipants().put(member.getUUID(), participant.withHandle(combined));
            run(activity.onStart(), member);
        }
        session.activate(now, now + activity.durationTicks());
        return true;
    }

    private static void cleanup(ServerLevel level, HangoutSession session, boolean success,
                                String reason, long now) {
        if (session.phase() == HangoutSession.Phase.COMPLETE || session.phase() == HangoutSession.Phase.INTERRUPTED) return;
        HangoutActivity activity = HangoutData.activities().get(session.activity());
        if (success) session.leave(reason); else session.interrupt(reason);
        for (HangoutSession.Participant participant : session.participants().values()) {
            VillagerEntityMCA member = villager(level, participant.entity());
            if (member != null) {
                if (participant.handle() != null) participant.handle().close(level, member);
                if (success && activity != null) run(activity.onFinish(), member);
            }
            BY_PARTICIPANT.remove(participant.entity(), session.id());
            HangoutPolicy policy = HangoutData.policies().get(session.policy());
            if (policy != null) COOLDOWNS.put(participant.entity(), now + policy.cooldownTicks());
        }
        CLAIMS.release(session.id());
        HOSTED_SERVICE.forget(session.id());
        if (success) session.finish();
        SESSIONS.remove(session.id(), session);
        HangoutEmbodiment.recoverNearby(level, session.venueAnchor());
    }

    private static List<VillagerEntityMCA> formGroup(ServerLevel level, VillagerEntityMCA initiator,
                                                      HangoutPolicy policy, HangoutActivity activity) {
        List<VillagerEntityMCA> candidates = level.getEntitiesOfClass(VillagerEntityMCA.class,
                new AABB(initiator.blockPosition()).inflate(policy.inviteRadius()), candidate ->
                        candidate != initiator && !BY_PARTICIPANT.containsKey(candidate.getUUID())
                                && available(candidate) && test(policy.companionWhen(), candidate));
        candidates.sort(Comparator.<VillagerEntityMCA>comparingInt(candidate -> -bondScore(initiator, candidate, policy))
                .thenComparingDouble(initiator::distanceToSqr)
                .thenComparing(candidate -> candidate.getUUID().toString()));
        int target = Math.min(Math.min(policy.maximumGroup(), activity.maximumParticipants()), candidates.size() + 1);
        List<VillagerEntityMCA> group = new ArrayList<>(target);
        group.add(initiator);
        for (VillagerEntityMCA candidate : candidates) {
            if (group.size() >= target) break;
            group.add(candidate);
        }
        int minimum = Math.max(policy.minimumGroup(), activity.minimumParticipants());
        if (group.size() < minimum && !(policy.soloFallback() && activity.minimumParticipants() == 1)) return List.of();
        return group;
    }

    private static int bondScore(VillagerEntityMCA first, VillagerEntityMCA second, HangoutPolicy policy) {
        int score = 0;
        for (Bond bond : Bonds.of(first).all()) {
            if (second.getUUID().equals(bond.other()) && bond.active()) score += policy.bondWeights().getOrDefault(bond.kind(), 0);
        }
        for (Bond bond : Bonds.of(second).all()) {
            if (first.getUUID().equals(bond.other()) && bond.active()) score += policy.bondWeights().getOrDefault(bond.kind(), 0);
        }
        return score;
    }

    private static List<VenueCandidate> discover(ServerLevel level, VillagerEntityMCA initiator, int radius) {
        Village village = initiator.getResidency().getHomeVillage().orElseGet(() -> Village.findNearest(initiator).orElse(null));
        if (village == null) return List.of();
        List<VenueCandidate> out = new ArrayList<>();
        for (HangoutVenue venue : HangoutData.venues().values()) {
            for (Building building : McaBuildings.all(village)) {
                if (!building.isComplete() || !venue.buildings().contains(building.getType())) continue;
                BlockPos center = building.getCenter();
                if (center == null || center.distSqr(initiator.blockPosition()) > (double) radius * radius) continue;
                List<SpotCandidate> spots = scanSpots(level, building);
                if (!spots.isEmpty()) out.add(new VenueCandidate(venue, village, building,
                        new BlockPos(center.getX(), center.getY(), center.getZ()), spots));
            }
        }
        out.sort(Comparator.comparingDouble(candidate -> candidate.anchor().distSqr(initiator.blockPosition())));
        return out;
    }

    private static List<SpotCandidate> scanSpots(ServerLevel level, Building building) {
        List<SpotCandidate> out = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (BlockPos raw : (Iterable<BlockPos>) building.getBlockPosStream()::iterator) {
            if (!level.isLoaded(raw)) continue;
            BlockState state = level.getBlockState(raw);
            ResourceLocation block = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            for (HangoutSpot definition : HangoutData.spots().values()) {
                if (!matches(definition, block, state)) continue;
                if (definition.availableWhen() != null && !definition.availableWhen().test(level, raw)) continue;
                BlockPos shifted = raw.offset(definition.canonicalOffset());
                BlockPos anchor = new BlockPos(shifted.getX(), shifted.getY(), shifted.getZ());
                if (seen.add(anchor.asLong())) out.add(new SpotCandidate(definition, anchor));
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

    private static List<SpotCandidate> compatibleSpots(List<SpotCandidate> candidates, HangoutActivity activity) {
        return candidates.stream().filter(candidate -> activity.postures().isEmpty()
                        || activity.postures().contains(candidate.definition().posture()))
                .sorted(Comparator.comparing(candidate -> candidate.anchor().asLong())).toList();
    }

    private static boolean useHospitality(ServerLevel level, VillagerEntityMCA villager,
                                          HangoutActivity activity, BlockPos venue) {
        if (activity.kind() == HangoutActivity.Kind.SOCIALIZE) return true;
        Amenities.Candidate selected = Amenities.candidates(level, villager).stream()
                .filter(candidate -> candidate.pos().distSqr(venue) <= 256D)
                .filter(candidate -> candidate.pos().distSqr(villager.blockPosition()) <= 16D)
                .filter(candidate -> switch (activity.kind()) {
                    case EAT -> candidate.feeds(level);
                    case DRINK -> candidate.hydrates(level);
                    case MIXED -> candidate.feeds(level) || candidate.hydrates(level);
                    default -> false;
                })
                .min(Comparator.comparingDouble(candidate -> candidate.pos().distSqr(villager.blockPosition())))
                .orElse(null);
        return selected != null && Amenities.use(level, villager, selected);
    }

    private static void serve(ServerLevel level, HangoutSession session, HangoutActivity activity,
                              VillagerEntityMCA guest, HangoutSession.Participant participant, long now) {
        if (activity.serviceCourses().isEmpty()) {
            if (participant.serviceAttempted() || now - session.activeAt() < activity.durationTicks() / 2L) return;
            boolean accepted = test(activity.serviceWhen(), guest)
                    && useHospitality(level, guest, activity, session.venueAnchor());
            run(accepted ? activity.onServiceAccepted() : activity.onServiceRefused(), guest);
            session.mutableParticipants().put(guest.getUUID(), participant.markServiceAttempted());
            return;
        }
        // Staff are labor participants for this session, not patrons in its course queue.
        if (activity.serviceCourses().stream().anyMatch(course -> participant.role().equals(course.role()))) return;
        long elapsed = now - session.activeAt();
        for (int index = 0; index < activity.serviceCourses().size(); index++) {
            HangoutActivity.ServiceCourse course = activity.serviceCourses().get(index);
            UUID worker = workerFor(session, course.role());
            Amenities.Candidate amenity = hospitalityCandidate(level, guest, course.kind(), session.venueAnchor());
            HostedServiceCoordinator.Result result = HOSTED_SERVICE.attempt(level.dimension().location(),
                    session.id(), session.venueDefinition() + "/" + session.buildingId(), guest.getUUID(), worker,
                    course, index, elapsed, now, test(activity.serviceWhen(), guest), amenity != null,
                    () -> amenity != null && Amenities.use(level, guest, amenity));
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

    private static @Nullable UUID workerFor(HangoutSession session, String role) {
        return session.participants().values().stream()
                .filter(participant -> participant.role().equals(role))
                .map(HangoutSession.Participant::entity)
                .sorted(Comparator.comparing(UUID::toString)).findFirst().orElse(null);
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
                .min(Comparator.comparingDouble(candidate -> candidate.pos().distSqr(villager.blockPosition())))
                .orElse(null);
    }

    private static boolean available(VillagerEntityMCA villager) {
        if (!villager.isAlive() || villager.isRemoved() || villager.isPassenger() || villager.isSleeping()) return false;
        return continuing(villager);
    }

    private static boolean continuing(VillagerEntityMCA villager) {
        if (!villager.isAlive() || villager.isRemoved() || villager.isSleeping()) return false;
        if (villager.getVillagerBrain().isPanicking() || villager.getLastHurtByMob() != null) return false;
        long dayTime = villager.level().getDayTime() % 24000L;
        return villager.getBrain().getSchedule().getActivityAt((int) dayTime) == Activity.MEET;
    }

    private static boolean test(@Nullable Condition condition, VillagerEntityMCA villager) {
        return condition == null || condition.test(new ConditionContext(villager));
    }

    private static void run(@Nullable Action action, VillagerEntityMCA villager) {
        if (action != null) action.run(new ActionContext(villager));
    }

    private static void moveTo(VillagerEntityMCA villager, BlockPos target) {
        villager.getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, 0.55D);
    }

    private static double distance(VillagerEntityMCA villager, BlockPos target) {
        return villager.distanceToSqr(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D);
    }

    private static List<VillagerEntityMCA> liveMembers(ServerLevel level, HangoutSession session) {
        List<VillagerEntityMCA> members = new ArrayList<>();
        for (UUID id : session.participants().keySet()) {
            VillagerEntityMCA member = villager(level, id);
            if (member != null) members.add(member);
        }
        return members;
    }

    private static @Nullable VillagerEntityMCA villager(ServerLevel level, UUID id) {
        Entity entity = level.getEntity(id);
        return entity instanceof VillagerEntityMCA villager ? villager : null;
    }

    private static String takeRole(Map<String, Integer> remaining) {
        for (Map.Entry<String, Integer> role : remaining.entrySet()) {
            if (role.getValue() < 1) continue;
            remaining.put(role.getKey(), role.getValue() - 1);
            return role.getKey();
        }
        return "participant";
    }

    private static @Nullable HangoutEmbodiment.Handle combine(@Nullable HangoutEmbodiment.Handle first,
                                                               @Nullable PerformanceHandle second) {
        if (first == null) return second == null ? null : (level, villager) -> second.stop();
        if (second == null) return first;
        return (level, villager) -> {
            try { second.stop(); }
            finally { first.close(level, villager); }
        };
    }
}
