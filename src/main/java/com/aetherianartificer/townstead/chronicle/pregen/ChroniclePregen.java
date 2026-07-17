package com.aetherianartificer.townstead.chronicle.pregen;

import com.aetherianartificer.townstead.calendar.CalendarProfile;
import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.calendar.WorldCalendarSavedData.VillageKey;
import com.aetherianartificer.townstead.chronicle.Chronicles;
import com.aetherianartificer.townstead.chronicle.arc.ArcManager;
import com.aetherianartificer.townstead.chronicle.concept.ConceptLedger;
import com.aetherianartificer.townstead.chronicle.knowledge.AccountLedger;
import com.aetherianartificer.townstead.chronicle.knowledge.DistortionOverlay;
import com.aetherianartificer.townstead.chronicle.knowledge.SpreadChannel;
import com.aetherianartificer.townstead.chronicle.model.Arc;
import com.aetherianartificer.townstead.chronicle.model.ChronicleEvent;
import com.aetherianartificer.townstead.chronicle.model.ChronicleRef;
import com.aetherianartificer.townstead.chronicle.model.Participation;
import com.aetherianartificer.townstead.chronicle.model.VillageHistory;
import com.aetherianartificer.townstead.chronicle.store.ChronicleSavedData;
import com.aetherianartificer.townstead.chronicle.template.ChronicleEventRegistry;
import com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate;
import com.aetherianartificer.townstead.reaction.WeightedPicker;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.Names;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
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
 */
public final class ChroniclePregen {

    private static final int MAX_EVENTS = 40;
    private static final long SEED_MIX = 0x5EEDC0DEL;
    private static final int RESIDENT_BIND_WINDOW_YEARS = 25;
    private static final int MAX_ANCESTORS = 8;

    private ChroniclePregen() {}

    public static void generate(MinecraftServer server, VillageKey key, long birthDay,
                                boolean playerFounded, List<VillagerEntityMCA> residents) {
        ChronicleSavedData data = ChronicleSavedData.get(server);
        VillageHistory existing = data.historyIfPresent(key);
        if (existing != null && !existing.entries().isEmpty()) return;

        long today = TownsteadCalendar.worldDay(server);
        CalendarProfile profile = TownsteadCalendar.activeProfile(server);
        int dpy = profile != null && profile.daysPerYear() > 0 ? profile.daysPerYear() : 360;
        RandomSource rng = RandomSource.create(
                (long) key.villageId() ^ key.dimension().toString().hashCode() * 0x9E3779B97F4A7C15L ^ SEED_MIX);

        ConceptLedger concepts = ConceptLedger.get(server);
        List<ChronicleRef> ancestors = new ArrayList<>();
        ChronicleRef founder = newAncestor(concepts, key, birthDay, rng);
        ancestors.add(founder);

        Arc arc = ArcManager.open(server, "prehistory", key.villageId(), birthDay, Map.of());

        ChronicleEventTemplate foundingTemplate = ChronicleEventRegistry.byId(
                rl("townstead", "village_founded"));
        if (foundingTemplate != null) {
            recordPregenEvent(server, data, key, foundingTemplate, birthDay, arc.arcId(),
                    List.of(new Participation(foundingTemplate.primaryRole().id(), founder)),
                    Map.of(foundingTemplate.primaryRole().id(), founder.displayName()));
        }

        if (playerFounded) {
            ArcManager.close(server, arc.arcId(), birthDay);
            return;
        }

        List<ChronicleEventTemplate> pool = new ArrayList<>();
        for (ChronicleEventTemplate template : ChronicleEventRegistry.all().values()) {
            if (template.contexts().contains(ChronicleEventTemplate.Context.PREGEN)
                    && (foundingTemplate == null || !template.id().equals(foundingTemplate.id()))) {
                pool.add(template);
            }
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
                        ? bindResident(residents, participations, role)
                        : null;
                if (ref == null) ref = pickAncestor(ancestors, concepts, key, birthDay, rng);
                participations.add(new Participation(role.id(), ref));
                params.put(role.id(), ref.displayName());
            }

            ChronicleEvent event = recordPregenEvent(server, data, key, template, day,
                    arc.arcId(), participations, params);
            if (bindable) recent.add(event);
            count++;
        }
        ArcManager.close(server, arc.arcId(), today - 1);

        seedElderMemories(server, residents, recent, rng);
    }

    private static ChronicleEvent recordPregenEvent(MinecraftServer server, ChronicleSavedData data,
                                                    VillageKey key, ChronicleEventTemplate template,
                                                    long day, long arcId,
                                                    List<Participation> participations,
                                                    Map<String, String> params) {
        ChronicleEvent draft = new ChronicleEvent(
                0L, template.id(), day, 0L, key.dimension(), 0L, key.villageId(),
                template.category(), 1.0f, template.reach(), ChronicleEvent.NONE, arcId,
                true, participations, params);
        long eventId = Chronicles.record(server, draft);
        Chronicles.recordDigestEntry(server, key, new VillageHistory.Entry(
                day, eventId, template.id().toString(),
                template.display().headlineLiteral(), template.display().headlineLangKey(), params));
        return draft.withId(eventId);
    }

    private static @Nullable ChronicleRef bindResident(List<VillagerEntityMCA> residents,
                                                       List<Participation> alreadyBound,
                                                       ChronicleEventTemplate.RoleSpec role) {
        if (role.kind() != ChronicleRef.Kind.VILLAGER) return null;
        ChronicleEventTemplate.PregenFilter filter = role.pregen();
        outer:
        for (VillagerEntityMCA resident : residents) {
            if (resident.isBaby()) continue;
            if (filter != null && filter.age() != null && "baby".equals(filter.age())) continue;
            for (Participation bound : alreadyBound) {
                if (resident.getUUID().equals(bound.ref().uuid())) continue outer;
            }
            if (filter != null && filter.profession() != null
                    && !filter.profession().equals(professionId(resident))) {
                continue;
            }
            return ChronicleRef.villager(resident.getUUID(), resident.getName().getString());
        }
        return null;
    }

    private static String professionId(VillagerEntityMCA villager) {
        try {
            var key = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION
                    .getKey(villager.getVillagerData().getProfession());
            return key == null ? "" : key.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static ChronicleRef pickAncestor(List<ChronicleRef> ancestors, ConceptLedger concepts,
                                             VillageKey key, long birthDay, RandomSource rng) {
        if (ancestors.size() < MAX_ANCESTORS && rng.nextFloat() < 0.4f) {
            ChronicleRef fresh = newAncestor(concepts, key, birthDay, rng);
            ancestors.add(fresh);
            return fresh;
        }
        return ancestors.get(rng.nextInt(ancestors.size()));
    }

    private static ChronicleRef newAncestor(ConceptLedger concepts, VillageKey key,
                                            long foundingDay, RandomSource rng) {
        UUID id = new UUID(rng.nextLong(), rng.nextLong());
        String name;
        try {
            name = Names.pickCitizenName(rng.nextBoolean() ? Gender.FEMALE : Gender.MALE);
        } catch (Throwable t) {
            name = "Elder " + Integer.toHexString(id.hashCode() & 0xFFFF);
        }
        String conceptId = "ancestor:" + id;
        concepts.put(new ConceptLedger.ConceptEntry(conceptId, "ancestor", name, "", foundingDay, key));
        return ChronicleRef.concept(conceptId, name);
    }

    /** Long-time residents remember the recent slice of the fabricated past. */
    private static void seedElderMemories(MinecraftServer server,
                                          List<VillagerEntityMCA> residents,
                                          List<ChronicleEvent> recentEvents,
                                          RandomSource rng) {
        if (recentEvents.isEmpty()) return;
        ChronicleSavedData data = ChronicleSavedData.get(server);
        int seeded = 0;
        for (VillagerEntityMCA resident : residents) {
            if (resident.isBaby() || seeded >= 3) break;
            int memoriesToSeed = 1 + rng.nextInt(2);
            for (int i = 0; i < memoriesToSeed && i < recentEvents.size(); i++) {
                ChronicleEvent event = recentEvents.get(rng.nextInt(recentEvents.size()));
                ChronicleEventTemplate template = ChronicleEventRegistry.byId(event.templateId());
                if (template == null) continue;
                AccountLedger.learn(server, template, event, resident.getUUID(), false,
                        null, SpreadChannel.WITNESS, ChronicleEvent.NONE, 0.9f,
                        DistortionOverlay.NONE, event.worldDay());
                float valence = 0.3f;
                float strength = 1.0f;
                ChronicleEventTemplate.Impact impact = template.impacts().get("on_learn");
                if (impact != null && impact.memory() != null) {
                    valence = impact.memory().valence();
                    strength = Math.max(0.5f, impact.memory().strength());
                }
                UUID other = firstVillagerParticipant(event, resident.getUUID());
                data.addOrReinforceMemory(resident.getUUID(), event.templateId().toString(),
                        other, event.worldDay(), strength, valence, event.params());
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
