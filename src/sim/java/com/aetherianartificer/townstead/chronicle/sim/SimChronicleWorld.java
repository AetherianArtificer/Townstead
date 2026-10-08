package com.aetherianartificer.townstead.chronicle.sim;

import com.aetherianartificer.townstead.calendar.WorldCalendarSavedData.VillageKey;
import com.aetherianartificer.townstead.chronicle.concept.ConceptLedger;
import com.aetherianartificer.townstead.chronicle.knowledge.KnownStoriesCache;
import com.aetherianartificer.townstead.chronicle.model.Account;
import com.aetherianartificer.townstead.chronicle.model.Arc;
import com.aetherianartificer.townstead.chronicle.model.ChronicleEvent;
import com.aetherianartificer.townstead.chronicle.model.VillageHistory;
import com.aetherianartificer.townstead.chronicle.model.VillagerMemory;
import com.aetherianartificer.townstead.chronicle.store.ChronicleSavedData;
import com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate;
import com.aetherianartificer.townstead.chronicle.world.ChronicleWorld;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The offline binding of {@link ChronicleWorld}: fabricated calendar, templates
 * loaded from disk, and every write captured in memory for printing. Nothing
 * touches a server, a save file, or the durable archive.
 *
 * <p>The hot tier is a real {@link ChronicleSavedData} instance rather than a
 * hand-rolled copy, so memory reinforcement, sentiment caps and mood clamping
 * behave exactly as they do in game. Only its NBT save/load is unused.</p>
 */
public final class SimChronicleWorld implements ChronicleWorld {

    private final ChronicleSavedData data = new ChronicleSavedData();
    private final Map<ResourceLocation, ChronicleEventTemplate> templates;
    private final long today;
    private final int daysPerYear;
    private final RandomSource nameRng;

    private final List<ChronicleEvent> events = new ArrayList<>();
    private final List<Account> accounts = new ArrayList<>();
    private final List<ConceptLedger.ConceptEntry> concepts = new ArrayList<>();
    private final Map<Long, Arc> arcs = new LinkedHashMap<>();
    private final Map<UUID, List<KnownStoriesCache.Entry>> known = new LinkedHashMap<>();

    private final Map<ResourceLocation, List<ResourceLocation>> itemTags;
    private final Set<ResourceLocation> stubbedTags = new LinkedHashSet<>();

    public SimChronicleWorld(Map<ResourceLocation, ChronicleEventTemplate> templates,
                             long today, int daysPerYear, RandomSource nameRng,
                             Map<ResourceLocation, List<ResourceLocation>> itemTags) {
        this.templates = templates;
        this.today = today;
        this.daysPerYear = daysPerYear;
        this.nameRng = nameRng;
        this.itemTags = itemTags;
    }

    // ---- calendar ----

    @Override
    public long today() {
        return today;
    }

    @Override
    public int daysPerYear() {
        return daysPerYear;
    }

    // ---- templates ----

    @Override
    public Map<ResourceLocation, ChronicleEventTemplate> templates() {
        return templates;
    }

    @Override
    public @Nullable ChronicleEventTemplate template(ResourceLocation id) {
        return templates.get(id);
    }

    // ---- truth side ----

    @Override
    public boolean hasHistory(VillageKey village) {
        VillageHistory existing = data.historyIfPresent(village);
        return existing != null && !existing.entries().isEmpty();
    }

    /** Mirrors {@code Chronicles.record} minus the archive append and the recent-events buffer. */
    @Override
    public long record(ChronicleEvent draft) {
        ChronicleEvent event = draft.withId(data.assignEventId());
        if (event.villageId() != ChronicleEvent.VILLAGE_NONE && !event.category().isEmpty()) {
            data.historyFor(new VillageKey(event.dimension(), event.villageId()))
                    .bumpCount(event.category());
        }
        events.add(event);
        return event.eventId();
    }

    @Override
    public void recordDigestEntry(VillageKey village, VillageHistory.Entry entry) {
        data.historyFor(village).addEntry(entry);
    }

    @Override
    public long openArc(String type, int villageId, long startDay, Map<String, String> params) {
        long arcId = data.assignArcId();
        arcs.put(arcId, new Arc(arcId, type, villageId, Arc.STATUS_OPEN, startDay, startDay, params));
        return arcId;
    }

    @Override
    public void closeArc(long arcId, long endDay) {
        Arc arc = arcs.get(arcId);
        if (arc != null) arcs.put(arcId, arc.closed(endDay));
    }

    @Override
    public void putConcept(ConceptLedger.ConceptEntry entry) {
        concepts.add(entry);
    }

    /**
     * Tags cannot resolve without a loaded registry, so an unlisted tag falls
     * back to a sample and is reported. Pass {@code --items} to model what a
     * real pack puts in one.
     */
    @Override
    public List<ResourceLocation> itemsInTag(ResourceLocation tag) {
        List<ResourceLocation> declared = itemTags.get(tag);
        if (declared != null) return declared;
        stubbedTags.add(tag);
        return SampleItems.DEFAULT;
    }

    @Override
    public void addCounter(UUID subject, String key, int amount) {
        data.addCounter(subject, key, amount);
    }

    @Override
    public String itemName(ResourceLocation itemId) {
        return SimItemNames.of(itemId);
    }

    @Override
    public String fabricateName(RandomSource rng, UUID identity) {
        return SimNames.pick(rng.nextBoolean(), nameRng);
    }

    // ---- belief side ----

    @Override
    public long assignAccountId() {
        return data.assignAccountId();
    }

    @Override
    public void appendAccount(Account account) {
        accounts.add(account);
    }

    @Override
    public void noteKnownStory(UUID knower, KnownStoriesCache.Entry entry) {
        known.computeIfAbsent(knower, ignored -> new ArrayList<>()).add(entry);
    }

    @Override
    public void addMoodImpact(UUID knower, float amount) {
        data.addMoodImpact(knower, amount);
    }

    @Override
    public void adjustSentiment(UUID from, UUID toward, float delta, long day, long accountId) {
        data.adjustSentiment(from, toward, delta, day, accountId);
    }

    @Override
    public void addOrReinforceMemory(UUID knower, String memoryKey, @Nullable UUID otherParty,
                                     long day, float strength, float valence,
                                     Map<String, String> params) {
        data.addOrReinforceMemory(knower, memoryKey, otherParty, day, strength, valence, params);
    }

    // ---- readback for the printer ----

    public List<ChronicleEvent> events() {
        return events;
    }

    public List<Account> accounts() {
        return accounts;
    }

    public List<ConceptLedger.ConceptEntry> concepts() {
        return concepts;
    }

    public List<Arc> arcs() {
        return List.copyOf(arcs.values());
    }

    public VillageHistory history(VillageKey village) {
        return data.historyFor(village);
    }

    public List<VillagerMemory> memories(UUID knower) {
        return data.memoriesFor(knower);
    }

    public float moodTarget(UUID knower) {
        return data.moodTarget(knower);
    }

    public float sentiment(UUID from, UUID toward) {
        return data.sentiment(from, toward);
    }

    public java.util.Map<String, Integer> counters(UUID subject) {
        return data.countersFor(subject);
    }

    public int knownStories(UUID knower) {
        return known.getOrDefault(knower, List.of()).size();
    }

    public Set<ResourceLocation> stubbedTags() {
        return stubbedTags;
    }
}
