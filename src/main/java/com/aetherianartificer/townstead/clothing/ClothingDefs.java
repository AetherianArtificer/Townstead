package com.aetherianartificer.townstead.clothing;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.ModGate;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every clothing entry and set the pack declares, and the questions the rest of the engine asks
 * of them.
 *
 * <p>Entries come from {@code data/<ns>/clothing/*.json}, sets from
 * {@code data/<ns>/clothing_set/*.json}. One listener loads both, because a set's {@code ref} and
 * {@code select} members can only resolve once every entry is known.</p>
 */
public final class ClothingDefs {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/ClothingDefs");
    public static final String SCHEMA = "townstead:clothing/v1";

    private static volatile List<ClothingEntry> ENTRIES = List.of();
    private static volatile Map<ResourceLocation, ClothingEntry> BY_ID = Map.of();
    private static volatile Map<ResourceLocation, ClothingSet> SETS = Map.of();
    private static volatile Map<ResourceLocation, List<ClothingEntry>> MEMBERS = Map.of();

    private ClothingDefs() {}

    public static void replaceAll(Collection<ClothingEntry> entries, Collection<ClothingSet> sets) {
        Map<ResourceLocation, ClothingEntry> byId = new LinkedHashMap<>();
        for (ClothingEntry entry : entries) byId.put(entry.id(), entry);
        Map<ResourceLocation, ClothingSet> setsById = new LinkedHashMap<>();
        for (ClothingSet set : sets) setsById.put(set.id(), set);
        ENTRIES = List.copyOf(byId.values());
        BY_ID = Map.copyOf(byId);
        SETS = Map.copyOf(setsById);
        Map<ResourceLocation, List<ClothingEntry>> members = new LinkedHashMap<>();
        for (ClothingSet set : setsById.values()) {
            members.put(set.id(), List.copyOf(resolve(set, setsById, byId, new HashSet<>())));
        }
        MEMBERS = Map.copyOf(members);
        ClothingThermal.invalidate();
    }

    public static List<ClothingEntry> entries() {
        return ENTRIES;
    }

    public static @Nullable ClothingEntry entry(@Nullable ResourceLocation id) {
        return id == null ? null : BY_ID.get(id);
    }

    public static @Nullable ClothingSet set(@Nullable ResourceLocation id) {
        return id == null ? null : SETS.get(id);
    }

    public static Collection<ClothingSet> sets() {
        return SETS.values();
    }

    /** The resolved members of a set, includes expanded, refs looked up, selects evaluated. */
    public static List<ClothingEntry> members(@Nullable ResourceLocation setId) {
        if (setId == null) return List.of();
        List<ClothingEntry> out = MEMBERS.get(setId);
        return out == null ? List.of() : out;
    }

    public static List<ClothingEntry> query(ClothingQuery query) {
        if (query == null || query.isEmpty()) return ENTRIES;
        List<ClothingEntry> out = new ArrayList<>();
        for (ClothingEntry entry : ENTRIES) {
            if (query.test(entry)) out.add(entry);
        }
        return out;
    }

    /** The first entry describing this stack, in document order. */
    /** The first document entry describing this stack, never a synthetic one. */
    public static @Nullable ClothingEntry documented(@Nullable Level level, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (ClothingEntry entry : ENTRIES) {
            if (entry.matches(level, stack)) return entry;
        }
        return null;
    }

    /**
     * The first entry describing this stack, in document order; failing that, a synthetic entry
     * from provider warmth data, so a garment a mod wrote up for a temperature mod is dressed in
     * without a Townstead document.
     */
    public static @Nullable ClothingEntry forStack(@Nullable Level level, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (ClothingEntry entry : ENTRIES) {
            if (entry.matches(level, stack)) return entry.withThermal(
                    com.aetherianartificer.townstead.temperature.Insulation.resolve(stack, entry.thermal()));
        }
        return ClothingThermal.syntheticStackEntry(stack);
    }

    /** The first entry describing this MCA skin, in document order. */
    public static @Nullable ClothingEntry forSkin(@Nullable String skinId) {
        if (skinId == null || skinId.isEmpty()) return null;
        for (ClothingEntry entry : ENTRIES) {
            if (entry.matchesSkin(skinId)) return entry;
        }
        return null;
    }

    private static LinkedHashSet<ClothingEntry> resolve(ClothingSet set,
                                                        Map<ResourceLocation, ClothingSet> sets,
                                                        Map<ResourceLocation, ClothingEntry> entries,
                                                        Set<ResourceLocation> visiting) {
        LinkedHashSet<ClothingEntry> out = new LinkedHashSet<>();
        if (!visiting.add(set.id())) {
            LOGGER.warn("Clothing set {} includes itself; the cycle is cut here", set.id());
            return out;
        }
        for (ResourceLocation included : set.includes()) {
            ClothingSet other = sets.get(included);
            if (other == null) {
                LOGGER.warn("Clothing set {} includes unknown set {}", set.id(), included);
                continue;
            }
            out.addAll(resolve(other, sets, entries, visiting));
        }
        for (ClothingSet.Member member : set.members()) {
            if (member.inline() != null) {
                out.add(member.inline());
            } else if (member.ref() != null) {
                ClothingEntry target = entries.get(member.ref());
                if (target == null) LOGGER.warn("Clothing set {} refers to unknown entry {}", set.id(), member.ref());
                else out.add(target);
            } else if (member.select() != null) {
                for (ClothingEntry entry : entries.values()) {
                    if (member.select().test(entry)) out.add(entry);
                }
            }
        }
        visiting.remove(set.id());
        return out;
    }

    /** Both directories in one pass, so sets resolve against the same reload's entries. */
    public static final class Loader extends SimplePreparableReloadListener<Loader.Prepared> {

        public record Prepared(Map<ResourceLocation, JsonObject> entries,
                              Map<ResourceLocation, JsonObject> sets) {}

        @Override
        protected Prepared prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
            return new Prepared(read(resourceManager, "clothing"), read(resourceManager, "clothing_set"));
        }

        private static Map<ResourceLocation, JsonObject> read(ResourceManager resourceManager, String dir) {
            Map<ResourceLocation, JsonObject> out = new LinkedHashMap<>();
            String prefix = dir + "/";
            for (Map.Entry<ResourceLocation, Resource> e : resourceManager
                    .listResources(dir, loc -> loc.getPath().endsWith(".json")).entrySet()) {
                ResourceLocation file = e.getKey();
                String path = file.getPath();
                ResourceLocation id = DataPackLang.parseId(file.getNamespace() + ":"
                        + path.substring(prefix.length(), path.length() - ".json".length()));
                if (id == null) continue;
                try (Reader reader = e.getValue().openAsReader()) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (parsed.isJsonObject()) out.put(id, parsed.getAsJsonObject());
                } catch (Exception ex) {
                    LOGGER.warn("Failed to read {} document {}: {}", dir, file, ex.getMessage());
                }
            }
            return out;
        }

        @Override
        protected void apply(Prepared prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
            Map<String, String> lang = DataPackLang.loadLangIndex(resourceManager);
            List<ClothingEntry> entries = new ArrayList<>();
            for (Map.Entry<ResourceLocation, JsonObject> e : prepared.entries().entrySet()) {
                JsonObject obj = e.getValue();
                try {
                    TownsteadSchema.validate(obj, SCHEMA);
                } catch (RuntimeException ex) {
                    LOGGER.warn("Clothing document {} rejected: {}", e.getKey(), ex.getMessage());
                    continue;
                }
                if (obj.has("mods") && !Boolean.TRUE.equals(ModGate.evaluate(obj.get("mods")))) {
                    LOGGER.debug("Clothing document {} skipped: mods gate unmet or malformed", e.getKey());
                    continue;
                }
                JsonArray array = GsonHelper.getAsJsonArray(obj, "entries", null);
                if (array == null) {
                    LOGGER.warn("Clothing document {} has no \"entries\"", e.getKey());
                    continue;
                }
                for (int i = 0; i < array.size(); i++) {
                    JsonElement element = array.get(i);
                    if (element == null || !element.isJsonObject()) continue;
                    JsonObject entryJson = element.getAsJsonObject();
                    if (entryJson.has("mods") && !Boolean.TRUE.equals(ModGate.evaluate(entryJson.get("mods")))) {
                        continue;
                    }
                    try {
                        ClothingEntry entry = ClothingEntry.parse(e.getKey(), i, entryJson);
                        if (entry == null) {
                            LOGGER.warn("Clothing document {} entry {} names nothing wearable (one of item, tag, skin)",
                                    e.getKey(), i);
                            continue;
                        }
                        entries.add(entry);
                    } catch (RuntimeException ex) {
                        LOGGER.warn("Clothing document {} entry {} rejected: {}", e.getKey(), i, ex.getMessage());
                    }
                }
            }

            List<ClothingSet> sets = new ArrayList<>();
            for (Map.Entry<ResourceLocation, JsonObject> e : prepared.sets().entrySet()) {
                JsonObject obj = e.getValue();
                try {
                    TownsteadSchema.validate(obj, ClothingSet.SCHEMA);
                } catch (RuntimeException ex) {
                    LOGGER.warn("Clothing set {} rejected: {}", e.getKey(), ex.getMessage());
                    continue;
                }
                if (obj.has("mods") && !Boolean.TRUE.equals(ModGate.evaluate(obj.get("mods")))) {
                    LOGGER.debug("Clothing set {} skipped: mods gate unmet or malformed", e.getKey());
                    continue;
                }
                try {
                    ClothingSet set = ClothingSet.parse(e.getKey(), obj, lang);
                    if (set == null) {
                        LOGGER.warn("Clothing set {} has no members and no includes", e.getKey());
                        continue;
                    }
                    sets.add(set);
                } catch (RuntimeException ex) {
                    LOGGER.warn("Clothing set {} rejected: {}", e.getKey(), ex.getMessage());
                }
            }

            replaceAll(entries, sets);
            if (!entries.isEmpty() || !sets.isEmpty()) {
                LOGGER.info("Loaded {} clothing entries and {} clothing sets", entries.size(), sets.size());
            }
        }
    }
}
