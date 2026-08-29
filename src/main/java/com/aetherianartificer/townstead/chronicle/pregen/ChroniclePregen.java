package com.aetherianartificer.townstead.chronicle.pregen;

import com.aetherianartificer.townstead.calendar.WorldCalendarSavedData.VillageKey;
import com.aetherianartificer.townstead.chronicle.model.ChronicleEvent;
import com.aetherianartificer.townstead.chronicle.model.ChronicleRef;
import com.aetherianartificer.townstead.chronicle.model.Participation;
import com.aetherianartificer.townstead.chronicle.model.VillageHistory;
import com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate;
import com.aetherianartificer.townstead.chronicle.world.ChronicleSubject;
import com.aetherianartificer.townstead.chronicle.world.ChronicleWorld;
import com.aetherianartificer.townstead.chronicle.world.ServerChronicleWorld;
import com.aetherianartificer.townstead.reaction.WeightedPicker;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Fabricates a village's pre-history at first sight: a founding arc, a
 * founder, ancestor concepts, and a believable spread of pregen-context
 * template events between the fabricated founding day and today. Deterministic
 * per village (same seed family as the birth fabrication, distinct mix-in) so
 * a re-roll reproduces the same past. Impacts never execute — elders get
 * seeded memories/accounts instead. This is seeded mythology: distorted old
 * accounts that outlive their events are the myths of the culture layer.
 *
 * <p>Generation runs against {@link ChronicleWorld}, so the offline harness in
 * {@code src/sim} drives this same code with fabricated values.</p>
 */
public final class ChroniclePregen {

    private static final int MAX_EVENTS = 40;
    private static final long SEED_MIX = 0x5EEDC0DEL;
    private static final int RESIDENT_BIND_WINDOW_YEARS = 25;
    private static final int MAX_ANCESTORS = 8;
    private static final int REUSE_ATTEMPTS = 4;
    /** Elders remember the recent past well, but they were not always there. */
    private static final float ELDER_FIDELITY = 0.9f;

    private ChroniclePregen() {}

    public static void generate(MinecraftServer server, VillageKey key, long birthDay,
                                boolean playerFounded, List<VillagerEntityMCA> residents) {
        generate(new ServerChronicleWorld(server), key, birthDay, playerFounded,
                ServerChronicleWorld.subjects(residents));
    }

    public static void generate(ChronicleWorld world, VillageKey key, long birthDay,
                                boolean playerFounded, List<ChronicleSubject> residents) {
        if (world.hasHistory(key)) return;

        long today = world.today();
        int dpy = world.daysPerYear();
        RandomSource rng = RandomSource.create(
                (long) key.villageId() ^ key.dimension().toString().hashCode() * 0x9E3779B97F4A7C15L ^ SEED_MIX);

        List<ChronicleRef> ancestors = new ArrayList<>();
        ChronicleRef founder = freshAncestor(ancestors, world, key, birthDay, rng);

        long arcId = world.openArc("prehistory", key.villageId(), birthDay, Map.of());

        ChronicleEventTemplate foundingTemplate = world.template(rl("townstead", "village_founded"));
        if (foundingTemplate != null) {
            // A founding is a founding: no magnitude spread on the one event that defines the place.
            recordPregenEvent(world, key, foundingTemplate, birthDay, arcId, 1.0f,
                    List.of(new Participation(foundingTemplate.primaryRole().id(), founder)),
                    Map.of(foundingTemplate.primaryRole().id(), founder.displayName()));
        }

        if (playerFounded) {
            world.closeArc(arcId, birthDay);
            return;
        }

        List<ChronicleEventTemplate> pool = new ArrayList<>();
        Map<ResourceLocation, Map<String, List<ResourceLocation>>> paramPools = new HashMap<>();
        for (ChronicleEventTemplate template : world.templates().values()) {
            if (!template.contexts().contains(ChronicleEventTemplate.Context.PREGEN)) continue;
            if (foundingTemplate != null && template.id().equals(foundingTemplate.id())) continue;
            Map<String, List<ResourceLocation>> resolved = PregenParams.resolve(template, world);
            if (resolved == null) continue;
            paramPools.put(template.id(), resolved);
            pool.add(template);
        }

        List<ChronicleEvent> recent = new ArrayList<>();
        long day = birthDay;
        int count = 0;
        while (count < MAX_EVENTS) {
            day += (long) (3 + rng.nextInt(4)) * dpy - rng.nextInt(dpy);
            if (day >= today - 30) break;
            Optional<ChronicleEventTemplate> picked =
                    WeightedPicker.pick(pool, ChronicleEventTemplate::pickWeight, rng);
            if (picked.isEmpty()) break;
            ChronicleEventTemplate template = picked.get();

            List<Participation> participations = new ArrayList<>();
            Map<String, String> params = new HashMap<>();
            boolean bindable = today - day <= (long) RESIDENT_BIND_WINDOW_YEARS * dpy;
            for (ChronicleEventTemplate.RoleSpec role : template.roles()) {
                ChronicleRef ref = bindable
                        ? PregenPeople.bindResident(residents, participations, role, rng)
                        : null;
                if (ref == null) {
                    ref = pickAncestor(ancestors, participations, world, key, birthDay, rng);
                }
                participations.add(new Participation(role.id(), ref));
                params.put(role.id(), ref.displayName());
            }
            PregenParams.fill(world, params, paramPools.get(template.id()), rng);

            ChronicleEvent event = recordPregenEvent(world, key, template, day, arcId,
                    PregenMagnitude.draw(rng, template), participations, params);
            if (bindable) recent.add(event);
            count++;
        }
        world.closeArc(arcId, today - 1);

        seedElderMemories(world, residents, recent, rng);
    }

    private static ChronicleEvent recordPregenEvent(ChronicleWorld world, VillageKey key,
                                                    ChronicleEventTemplate template,
                                                    long day, long arcId, float magnitude,
                                                    List<Participation> participations,
                                                    Map<String, String> params) {
        ChronicleEvent draft = new ChronicleEvent(
                0L, template.id(), day, 0L, key.dimension(), 0L, key.villageId(),
                template.category(), magnitude, template.reach(), ChronicleEvent.NONE, arcId,
                true, participations, params);
        long eventId = world.record(draft);
        world.recordDigestEntry(key, new VillageHistory.Entry(
                day, eventId, template.id().toString(),
                template.display().headlineLiteral(), template.display().headlineLangKey(), params));
        return draft.withId(eventId);
    }

    private static ChronicleRef pickAncestor(List<ChronicleRef> ancestors,
                                             List<Participation> alreadyBound, ChronicleWorld world,
                                             VillageKey key, long birthDay, RandomSource rng) {
        if (ancestors.size() < MAX_ANCESTORS && rng.nextFloat() < 0.4f) {
            return freshAncestor(ancestors, world, key, birthDay, rng);
        }
        // Nobody may hold two roles in one event: a wedding needs two people.
        for (int attempt = 0; attempt < REUSE_ATTEMPTS; attempt++) {
            ChronicleRef candidate = ancestors.get(rng.nextInt(ancestors.size()));
            if (!PregenPeople.isBound(alreadyBound, candidate)) return candidate;
        }
        return freshAncestor(ancestors, world, key, birthDay, rng);
    }

    private static ChronicleRef freshAncestor(List<ChronicleRef> ancestors, ChronicleWorld world,
                                              VillageKey key, long birthDay, RandomSource rng) {
        ChronicleRef fresh = PregenPeople.fabricate(
                world, key, PregenPeople.KIND_ANCESTOR, birthDay, rng);
        ancestors.add(fresh);
        return fresh;
    }

    /** Long-time residents remember the recent slice of the fabricated past. */
    private static void seedElderMemories(ChronicleWorld world,
                                          List<ChronicleSubject> residents,
                                          List<ChronicleEvent> recentEvents,
                                          RandomSource rng) {
        if (recentEvents.isEmpty()) return;
        int seeded = 0;
        for (ChronicleSubject resident : residents) {
            if (resident.baby() || seeded >= 3) break;
            int memoriesToSeed = 1 + rng.nextInt(2);
            for (int i = 0; i < memoriesToSeed && i < recentEvents.size(); i++) {
                ChronicleEvent event = recentEvents.get(rng.nextInt(recentEvents.size()));
                ChronicleEventTemplate template = world.template(event.templateId());
                if (template == null) continue;
                PregenMemories.remember(world, template, event, resident.uuid(), null,
                        ELDER_FIDELITY, event.worldDay());
            }
            seeded++;
        }
    }

    private static @Nullable UUID firstVillagerParticipant(ChronicleEvent event, UUID self) {
        for (Participation participation : event.participations()) {
            UUID uuid = participation.ref().uuid();
            if (uuid != null && !uuid.equals(self)) return uuid;
        }
        return null;
    }

    private static net.minecraft.resources.ResourceLocation rl(String namespace, String path) {
        //? if >=1.21 {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(namespace, path);
        //?} else {
        /*return new net.minecraft.resources.ResourceLocation(namespace, path);
        *///?}
    }
}
