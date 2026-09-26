package com.aetherianartificer.townstead.dialogue.conversation;

import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.chronicle.Chronicles;
import com.aetherianartificer.townstead.chronicle.knowledge.KnownStoriesCache;
import com.aetherianartificer.townstead.chronicle.model.ChronicleEvent;
import com.aetherianartificer.townstead.chronicle.model.Participation;
import com.aetherianartificer.townstead.dialogue.conversation.generative.LineComposer;
import com.aetherianartificer.townstead.dialogue.conversation.generative.SubjectDefinition;
import com.aetherianartificer.townstead.hangout.HangoutEngine;
import com.aetherianartificer.townstead.hangout.HangoutVisit;
import com.google.gson.JsonObject;
import com.aetherianartificer.townstead.compat.mca.McaPersonalityCompat;
import net.conczin.mca.entity.Infectable;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Turns game state into live subject instances. A source type owns one kind of fact; other mods can
 * register more. A type with no instances simply never offers its topics.
 */
public final class SubjectSources {
    /** Everything a source may read about one pair of speakers. */
    public record Query(ServerLevel level, VillagerEntityMCA speaker, VillagerEntityMCA listener, SubjectDefinition subject) {}

    @FunctionalInterface
    public interface Source {
        List<LineComposer.Subject> instances(Query query);
    }

    private static final Map<ResourceLocation, Source> SOURCES = new LinkedHashMap<>();
    private static final Set<String> PLURAL_BIOMES = Set.of("plains", "badlands", "mushroom_fields", "sunflower_plains",
            "snowy_plains", "wooded_badlands", "eroded_badlands", "stony_peaks", "jagged_peaks", "frozen_peaks",
            "windswept_hills", "windswept_gravelly_hills", "snowy_slopes", "dripstone_caves", "lush_caves");

    static {
        register("townstead:weather", SubjectSources::weather);
        register("townstead:biome", SubjectSources::biome);
        register("townstead:chronicle_account", SubjectSources::chronicle);
        register("townstead:general", q -> List.of(plain(q.subject().id(), "", "neutral")));
        register("townstead:venue", SubjectSources::venue);
        register("townstead:need", SubjectSources::need);
        register("townstead:mob_sighting", SubjectSources::mobSighting);
        register("townstead:company", SubjectSources::company);
        register("townstead:infection", SubjectSources::infection);
        // Designed sources whose game data does not exist yet. Their topics stay silent until a
        // system or another mod registers a real source under the same id.
        for (String pending : List.of("townstead:quest_board", "townstead:project", "townstead:realm_rumour"))
            register(pending, q -> List.of());
    }

    private SubjectSources() {}

    public static synchronized void register(String type, Source source) {
        ResourceLocation id = ResourceLocation.tryParse(type);
        if (id != null) SOURCES.put(id, source);
    }

    public static List<LineComposer.Subject> instances(Query query) {
        Source source = SOURCES.get(query.subject().sourceType());
        if (source == null) return List.of();
        try {
            return source.instances(query);
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private static LineComposer.Subject plain(ResourceLocation id, String variant, String valence) {
        return new LineComposer.Subject(id, variant, valence, Map.of(), Set.of(), null);
    }

    private static List<LineComposer.Subject> weather(Query q) {
        ServerLevel level = q.level();
        BlockPos pos = q.speaker().blockPosition();
        String variant;
        if (level.isThundering()) variant = "thunder";
        else if (level.isRaining()) variant = level.getBiome(pos).value().coldEnoughToSnow(pos) ? "snow" : "rain";
        else variant = "clear";
        return List.of(plain(q.subject().id(), variant, "neutral"));
    }

    private static List<LineComposer.Subject> biome(Query q) {
        Holder<Biome> biome = q.level().getBiome(q.speaker().blockPosition());
        Optional<ResourceLocation> key = biome.unwrapKey().map(k -> k.location());
        if (key.isEmpty()) return List.of();
        String path = key.get().getPath();
        String english = "the " + path.replace('_', ' ');
        Map<String, String> meta = Map.of("number", PLURAL_BIOMES.contains(path) ? "plural" : "singular");
        LineComposer.SlotValue value = new LineComposer.SlotValue(english,
                "townstead.biome_phrase." + key.get().getNamespace() + "." + path, null, meta);
        return List.of(new LineComposer.Subject(q.subject().id(), "", "neutral", Map.of("biome", value), Set.of(), null));
    }

    private static List<LineComposer.Subject> venue(Query q) {
        HangoutVisit visit = HangoutEngine.visit(q.speaker().getUUID());
        if (visit == null || visit.phase() != HangoutVisit.Phase.PRESENT) return List.of();
        return List.of(plain(q.subject().id(), "", "neutral"));
    }

    /** The speaker's most pressing need, when one is low enough to mention. */
    private static List<LineComposer.Subject> need(Query q) {
        var levels = com.aetherianartificer.townstead.api.impl.v1.NeedScales.levels(
                com.aetherianartificer.townstead.villager.TownsteadVillagers.get(q.speaker()).needs());
        String variant = null;
        double urgency = 0;
        for (var level : levels.values()) {
            if (!level.enabled()) continue;
            String band = level.band();
            double u = switch (band) {
                case "hungry", "thirsty", "tired", "chilly", "warm" -> 0.4;
                case "famished", "parched", "drowsy", "cold", "hot" -> 0.8;
                case "freezing", "sweltering" -> 1.0;
                default -> 0;
            };
            if (u <= urgency) continue;
            urgency = u;
            variant = switch (band) {
                case "hungry", "famished" -> "hungry";
                case "thirsty", "parched" -> "thirsty";
                case "tired", "drowsy" -> "tired";
                case "chilly", "cold", "freezing" -> "cold";
                default -> "hot";
            };
        }
        if (variant == null || variant.equals("hot") && urgency < 0.8 || variant.equals("cold") && urgency < 0.8) return List.of();
        return List.of(new LineComposer.Subject(q.subject().id(), variant, "negative", Map.of(), Set.of(), null, urgency));
    }

    /** A hostile mob someone saw near the village in the last two days. */
    private static List<LineComposer.Subject> mobSighting(Query q) {
        List<LineComposer.Subject> out = new ArrayList<>();
        long today = TownsteadCalendar.worldDay(q.level().getServer());
        for (MobSightings.Sighting s : MobSightings.recent(q.level(), q.speaker(), 2)) {
            String name = s.name().toLowerCase(Locale.ROOT);
            String article = "aeiou".indexOf(name.isEmpty() ? 'x' : name.charAt(0)) >= 0 ? "an " : "a ";
            String key = "townstead.mob_name." + s.type().getNamespace() + "." + s.type().getPath();
            Map<String, LineComposer.SlotValue> slots = new LinkedHashMap<>();
            slots.put("mob", new LineComposer.SlotValue(name, key, null, Map.of()));
            slots.put("a_mob", new LineComposer.SlotValue(article + name, key + ".a", null, Map.of()));
            slots.put("who", new LineComposer.SlotValue(s.witnessName(), null, s.witness(), Map.of()));
            if (s.witness().equals(q.listener().getUUID())) continue;
            boolean witnessed = s.witness().equals(q.speaker().getUUID());
            double urgency = today - s.day() == 0 ? 0.9 : 0.5;
            out.add(new LineComposer.Subject(q.subject().id(), "", "negative", Map.copyOf(slots),
                    Set.of(witnessed ? "witnessed" : "heard"), s, urgency));
        }
        return out;
    }

    /** A friend the speaker wants to spend time with: plans, invitations, a little flirting. */
    private static List<LineComposer.Subject> company(Query q) {
        var social = com.aetherianartificer.townstead.social.RelationshipService.data(q.level().getServer());
        double affection = social.relationships().value(q.speaker().getUUID(), q.listener().getUUID(),
                com.aetherianartificer.townstead.social.RelationshipQualities.AFFECTION, TownsteadCalendar.worldDay(q.level().getServer()));
        if (affection < 10) return List.of();
        String variant = affection >= 25 ? "close" : "friend";
        return List.of(new LineComposer.Subject(q.subject().id(), variant, "positive", Map.of(), Set.of(), null,
                Math.min(0.8, affection / 50)));
    }

    /** Personalities that keep a fresh bite to themselves until the fever gives it away. */
    private static final Set<String> HIDE_BITE = Set.of("crabby", "grumpy", "greedy", "shy", "introverted", "confident");

    /**
     * A zombie infection the speaker can talk about now: their own, unless they are hiding a fresh bite, or the
     * listener's once the fever shows. Past the babbling point MCA garbles the speech, so it stops here.
     */
    private static List<LineComposer.Subject> infection(Query q) {
        List<LineComposer.Subject> out = new ArrayList<>();
        float own = q.speaker().getInfectionProgress(), theirs = q.listener().getInfectionProgress();
        if (own > 0 && own <= Infectable.BABBLING_THRESHOLD) {
            boolean fever = own >= Infectable.FEVER_THRESHOLD;
            String personality = McaPersonalityCompat.id(q.speaker().getVillagerBrain().getPersonality()).replace("mca:", "");
            if (fever || !HIDE_BITE.contains(personality))
                out.add(infected(q, q.speaker(), fever ? "fever" : "bitten", fever ? 0.8 : 0.6));
        }
        if (theirs >= Infectable.FEVER_THRESHOLD && theirs <= Infectable.BABBLING_THRESHOLD)
            out.add(infected(q, q.listener(), "fever", 0.9));
        return out;
    }

    private static LineComposer.Subject infected(Query q, VillagerEntityMCA who, String variant, double urgency) {
        return new LineComposer.Subject(q.subject().id(), variant, "negative",
                Map.of("who", new LineComposer.SlotValue(who.getName().getString(), null, who.getUUID(), Map.of())),
                Set.of("witnessed"), null, urgency);
    }

    /** Stories the speaker believes, for the event templates this subject maps. */
    private static List<LineComposer.Subject> chronicle(Query q) {
        JsonObject source = q.subject().source();
        if (!source.has("events") || !KnownStoriesCache.ready(q.speaker().getUUID())) return List.of();
        JsonObject mapping = source.getAsJsonObject("events");
        long today = TownsteadCalendar.worldDay(q.level().getServer());
        int maxAge = source.has("max_age_days") ? source.get("max_age_days").getAsInt() : 14;
        float minFidelity = source.has("min_fidelity") ? source.get("min_fidelity").getAsFloat() : 0f;
        UUID speaker = q.speaker().getUUID(), listener = q.listener().getUUID();
        List<LineComposer.Subject> out = new ArrayList<>();
        for (KnownStoriesCache.Entry entry : KnownStoriesCache.entries(speaker)) {
            if (!mapping.has(entry.templateId.toString())) continue;
            if (today - entry.eventDay > maxAge || entry.fidelity < minFidelity) continue;
            ChronicleEvent event = Chronicles.buffer().byId(entry.storyEventId);
            if (event == null) continue;
            JsonObject spec = mapping.getAsJsonObject(entry.templateId.toString());
            JsonObject roles = spec.has("roles") ? spec.getAsJsonObject("roles") : new JsonObject();
            Map<String, LineComposer.SlotValue> slots = new LinkedHashMap<>();
            Map<String, String> believedNames = entry.overlay.applyToParams(Map.of());
            for (var role : roles.entrySet()) {
                String roleId = role.getValue().getAsString();
                Participation who = event.participations().stream()
                        .filter(p -> p.role().equals(roleId) && p.ref().kind().isPerson()).findFirst().orElse(null);
                if (who == null) continue;
                UUID person = entry.overlay.believedUuid(roleId, who.ref().uuid());
                String name = believedNames.getOrDefault(roleId, who.ref().displayName());
                slots.put(role.getKey(), new LineComposer.SlotValue(name, null, person, Map.of()));
            }
            // A story about an ongoing state goes stale when the state ends, such as a bite once cured or turned.
            if (spec.has("while_infected") && spec.get("while_infected").getAsBoolean() && !stillInfected(q.level(), slots)) continue;
            orient(slots, q.subject().symmetric(), speaker, listener);
            Set<UUID> people = new HashSet<>();
            slots.values().forEach(v -> { if (v.person() != null) people.add(v.person()); });
            if (people.contains(speaker) && people.contains(listener)) continue;
            boolean witnessed = event.participations().stream().anyMatch(p -> speaker.equals(p.ref().uuid()));
            String valence = spec.has("valence") ? spec.get("valence").getAsString() : "neutral";
            // "shared": the listener already knows this story. News learned today is what the speaker wants to tell.
            boolean shared = KnownStoriesCache.knows(listener, entry.storyEventId)
                    || event.participations().stream().anyMatch(p -> listener.equals(p.ref().uuid()));
            Set<String> provenance = new HashSet<>();
            provenance.add(witnessed ? "witnessed" : "heard");
            if (shared) provenance.add("shared");
            double urgency = shared ? -0.3 : today - entry.learnedDay <= 1 ? 0.6 * entry.fidelity + 0.2 : 0;
            out.add(new LineComposer.Subject(q.subject().id(), spec.has("variant") ? spec.get("variant").getAsString() : "",
                    valence, Map.copyOf(slots), Set.copyOf(provenance), entry, urgency));
        }
        return out;
    }

    /** False when a loaded villager in these slots is no longer infected. An unloaded one keeps the story. */
    private static boolean stillInfected(ServerLevel level, Map<String, LineComposer.SlotValue> slots) {
        for (LineComposer.SlotValue value : slots.values()) {
            if (value.person() == null) continue;
            var entity = level.getEntity(value.person());
            if (entity == null) continue;
            if (!(entity instanceof VillagerEntityMCA villager) || villager.getInfectionProgress() <= 0) return false;
        }
        return true;
    }

    /** Puts a participating speaker or listener in the first symmetric role. */
    static void orient(Map<String, LineComposer.SlotValue> slots, List<String> symmetric, UUID speaker, UUID listener) {
        if (symmetric.size() < 2) return;
        LineComposer.SlotValue first = slots.get(symmetric.get(0)), second = slots.get(symmetric.get(1));
        if (first == null || second == null) return;
        boolean firstInvolved = involved(first, speaker, listener), secondInvolved = involved(second, speaker, listener);
        if (secondInvolved && !firstInvolved) {
            slots.put(symmetric.get(0), second);
            slots.put(symmetric.get(1), first);
        }
    }

    private static boolean involved(@Nullable LineComposer.SlotValue value, UUID speaker, UUID listener) {
        return value != null && value.person() != null && (value.person().equals(speaker) || value.person().equals(listener));
    }
}
