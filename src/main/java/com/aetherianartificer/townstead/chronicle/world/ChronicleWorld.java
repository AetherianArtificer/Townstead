package com.aetherianartificer.townstead.chronicle.world;

import com.aetherianartificer.townstead.calendar.WorldCalendarSavedData.VillageKey;
import com.aetherianartificer.townstead.chronicle.concept.ConceptLedger;
import com.aetherianartificer.townstead.chronicle.knowledge.KnownStoriesCache;
import com.aetherianartificer.townstead.chronicle.model.Account;
import com.aetherianartificer.townstead.chronicle.model.ChronicleEvent;
import com.aetherianartificer.townstead.chronicle.model.VillageHistory;
import com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything event generation asks of the world, so the generators can run
 * against a live server or against fabricated values. {@link ServerChronicleWorld}
 * is the live binding; the offline harness in {@code src/sim} supplies an
 * in-memory one, which is what lets the same generation code produce a village
 * history in a terminal without booting the game.
 *
 * <p>The truth firewall still holds across the seam: truth methods
 * ({@code record}, digest, arcs, concepts) and belief methods (accounts, mood,
 * sentiment, memories) stay separate here exactly as they are on the server.</p>
 */
public interface ChronicleWorld {

    // ---- calendar ----

    long today();

    int daysPerYear();

    // ---- templates ----

    Map<ResourceLocation, ChronicleEventTemplate> templates();

    @Nullable ChronicleEventTemplate template(ResourceLocation id);

    // ---- truth side ----

    /** True when this village already has a written history (pre-gen must not run twice). */
    boolean hasHistory(VillageKey village);

    /** Assigns the id and records the ground-truth event; returns the assigned id. */
    long record(ChronicleEvent draft);

    void recordDigestEntry(VillageKey village, VillageHistory.Entry entry);

    long openArc(String type, int villageId, long startDay, Map<String, String> params);

    void closeArc(long arcId, long endDay);

    void putConcept(ConceptLedger.ConceptEntry entry);

    /**
     * Adds to a chronicle counter. A fabricated background writes the competence
     * its age and trade imply here — a number, not a replay, so no invented event
     * ever enters the archive.
     */
    void addCounter(UUID subject, String key, int amount);

    /**
     * Item ids in a tag, for pre-history display params the live path takes
     * from a real item. Empty means the template cannot render in pre-history,
     * and generation drops it rather than write a headline with a hole in it.
     */
    List<ResourceLocation> itemsInTag(ResourceLocation tag);

    /** An item's display name, as the live tap would write it into a headline. */
    String itemName(ResourceLocation itemId);

    /**
     * A name for a fabricated person. Implementations must consume exactly one
     * {@code rng.nextBoolean()} (the gender draw) and take the name itself from
     * elsewhere, so a village's event stream is identical in game and in the
     * harness for a given seed even though the names differ. {@code identity}
     * is the ancestor's uuid, for name sources that need a stable fallback.
     */
    String fabricateName(RandomSource rng, UUID identity);

    // ---- belief side ----

    long assignAccountId();

    void appendAccount(Account account);

    void noteKnownStory(UUID knower, KnownStoriesCache.Entry entry);

    void addMoodImpact(UUID knower, float amount);

    void adjustSentiment(UUID from, UUID toward, float delta, long day, long accountId);

    void addOrReinforceMemory(UUID knower, String memoryKey, @Nullable UUID otherParty,
                              long day, float strength, float valence, Map<String, String> params);
}
