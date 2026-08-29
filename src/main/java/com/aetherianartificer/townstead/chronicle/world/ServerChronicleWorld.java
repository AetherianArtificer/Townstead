package com.aetherianartificer.townstead.chronicle.world;

import com.aetherianartificer.townstead.calendar.CalendarProfile;
import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.calendar.WorldCalendarSavedData.VillageKey;
import com.aetherianartificer.townstead.chronicle.Chronicles;
import com.aetherianartificer.townstead.chronicle.arc.ArcManager;
import com.aetherianartificer.townstead.chronicle.emit.ChronicleTaps;
import com.aetherianartificer.townstead.chronicle.concept.ConceptLedger;
import com.aetherianartificer.townstead.chronicle.knowledge.KnownStoriesCache;
import com.aetherianartificer.townstead.chronicle.model.Account;
import com.aetherianartificer.townstead.chronicle.model.ChronicleEvent;
import com.aetherianartificer.townstead.chronicle.model.ChronicleRef;
import com.aetherianartificer.townstead.chronicle.model.VillageHistory;
import com.aetherianartificer.townstead.root.RootRegistry;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import com.aetherianartificer.townstead.chronicle.store.ChronicleSavedData;
import com.aetherianartificer.townstead.chronicle.template.ChronicleEventRegistry;
import com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.Names;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The live binding of {@link ChronicleWorld}: everything routed at the server. */
public record ServerChronicleWorld(MinecraftServer server) implements ChronicleWorld {

    private static final int FALLBACK_DAYS_PER_YEAR = 360;

    /** Adapts live villagers into the subjects role binding reads. */
    public static List<ChronicleSubject> subjects(List<VillagerEntityMCA> villagers) {
        List<ChronicleSubject> subjects = new ArrayList<>(villagers.size());
        for (VillagerEntityMCA villager : villagers) {
            subjects.add(new ChronicleSubject(villager.getUUID(), villager.getName().getString(),
                    ChronicleRef.Kind.VILLAGER, villager.isBaby(), professionId(villager),
                    ageBands(villager)));
        }
        return subjects;
    }

    /** The subject's own Root decides when they were a child and when an adult. */
    private static AgeBands ageBands(VillagerEntityMCA villager) {
        try {
            String rootId = TownsteadVillagers.get(villager).life().rootId();
            ResourceLocation id = ResourceLocation.tryParse(rootId);
            return AgeBands.of(RootRegistry.effectiveLifeCycle(
                    id == null ? RootRegistry.DEFAULT_ID : id));
        } catch (Throwable t) {
            return AgeBands.DEFAULT;
        }
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

    @Override
    public long today() {
        return TownsteadCalendar.worldDay(server);
    }

    @Override
    public int daysPerYear() {
        CalendarProfile profile = TownsteadCalendar.activeProfile(server);
        return profile != null && profile.daysPerYear() > 0
                ? profile.daysPerYear() : FALLBACK_DAYS_PER_YEAR;
    }

    @Override
    public Map<ResourceLocation, ChronicleEventTemplate> templates() {
        return ChronicleEventRegistry.all();
    }

    @Override
    public @Nullable ChronicleEventTemplate template(ResourceLocation id) {
        return ChronicleEventRegistry.byId(id);
    }

    @Override
    public boolean hasHistory(VillageKey village) {
        VillageHistory existing = ChronicleSavedData.get(server).historyIfPresent(village);
        return existing != null && !existing.entries().isEmpty();
    }

    @Override
    public long record(ChronicleEvent draft) {
        return Chronicles.record(server, draft);
    }

    @Override
    public void recordDigestEntry(VillageKey village, VillageHistory.Entry entry) {
        Chronicles.recordDigestEntry(server, village, entry);
    }

    @Override
    public long openArc(String type, int villageId, long startDay, Map<String, String> params) {
        return ArcManager.open(server, type, villageId, startDay, params).arcId();
    }

    @Override
    public void closeArc(long arcId, long endDay) {
        ArcManager.close(server, arcId, endDay);
    }

    @Override
    public void putConcept(ConceptLedger.ConceptEntry entry) {
        ConceptLedger.get(server).put(entry);
    }

    @Override
    public List<ResourceLocation> itemsInTag(ResourceLocation tag) {
        try {
            var key = net.minecraft.tags.TagKey.create(
                    net.minecraft.core.registries.Registries.ITEM, tag);
            var holders = net.minecraft.core.registries.BuiltInRegistries.ITEM.getTag(key);
            if (holders.isEmpty()) return List.of();
            List<ResourceLocation> ids = new ArrayList<>();
            for (var holder : holders.get()) {
                holder.unwrapKey().ifPresent(itemKey -> ids.add(itemKey.location()));
            }
            return ids;
        } catch (Throwable t) {
            return List.of();
        }
    }

    @Override
    public void addCounter(UUID subject, String key, int amount) {
        Chronicles.addCounter(server, subject, key, amount);
    }

    @Override
    public String itemName(ResourceLocation itemId) {
        return ChronicleTaps.itemName(itemId);
    }

    @Override
    public String fabricateName(RandomSource rng, UUID identity) {
        Gender gender = rng.nextBoolean() ? Gender.FEMALE : Gender.MALE;
        try {
            return Names.pickCitizenName(gender);
        } catch (Throwable t) {
            return "Elder " + Integer.toHexString(identity.hashCode() & 0xFFFF);
        }
    }

    @Override
    public long assignAccountId() {
        return ChronicleSavedData.get(server).assignAccountId();
    }

    @Override
    public void appendAccount(Account account) {
        Chronicles.appendAccount(server, account);
    }

    @Override
    public void noteKnownStory(UUID knower, KnownStoriesCache.Entry entry) {
        KnownStoriesCache.add(knower, entry);
    }

    @Override
    public void addMoodImpact(UUID knower, float amount) {
        ChronicleSavedData.get(server).addMoodImpact(knower, amount);
    }

    @Override
    public void adjustSentiment(UUID from, UUID toward, float delta, long day, long accountId) {
        ChronicleSavedData.get(server).adjustSentiment(from, toward, delta, day, accountId);
    }

    @Override
    public void addOrReinforceMemory(UUID knower, String memoryKey, @Nullable UUID otherParty,
                                     long day, float strength, float valence,
                                     Map<String, String> params) {
        ChronicleSavedData.get(server).addOrReinforceMemory(knower, memoryKey, otherParty,
                day, strength, valence, params);
    }
}
