package com.aetherianartificer.townstead.naming;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.mca.resources.WeightedPool;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads naming traditions from {@code data/<ns>/naming_tradition/<id>.json}.
 *
 * <pre>{@code
 * {
 *   "schema": "townstead:naming_tradition/v1",
 *   "given": [
 *     { "list": "townstead_mobs:piglin", "rate": 3 },
 *     { "list": "japan",                 "rate": 1 }
 *   ],
 *   "family": {
 *     "type": "patronymic",
 *     "affix": { "male": { "suffix": "sson" }, "female": { "suffix": "sdottir" } }
 *   },
 *   "order": "given_first"
 * }
 * }</pre>
 *
 * <p>A bare {@code list} is one of MCA's name buckets; a namespaced one is a Townstead
 * {@code given_name}. {@code family.type} is {@code none}, {@code inherited} (names from
 * {@code family.list}, passed down by {@code family.descent}), {@code patronymic} or
 * {@code matronymic} (a parent's given name wrapped in the gendered affix).</p>
 */
public final class NamingTraditionJsonLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(Townstead.MOD_ID + "/NamingTraditionJsonLoader");
    private static final Gson GSON = new Gson();
    private static final String SCHEMA = "townstead:naming_tradition/v1";

    public NamingTraditionJsonLoader() {
        super(GSON, "naming_tradition");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<ResourceLocation, NamingTradition> loaded = new LinkedHashMap<>();
        Map<ResourceLocation, GivenNames> inlineGiven = new LinkedHashMap<>();
        Map<ResourceLocation, FamilyNames> inlineFamily = new LinkedHashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation file = entry.getKey();
            try {
                JsonObject root = GsonHelper.convertToJsonObject(entry.getValue(), file.toString());
                TownsteadSchema.validate(root, SCHEMA);

                // An object under 'given' is names written in place rather than a reference to a
                // list elsewhere, so a self-contained tradition needs no second file.
                JsonElement givenElement = root.get("given");
                List<NamingTradition.SourceGroup> given;
                if (givenElement != null && givenElement.isJsonObject()) {
                    GivenNames written = inlineGivenOf(file, givenElement.getAsJsonObject());
                    if (written == null) {
                        LOGGER.warn("Naming tradition {} writes given names in place but none are usable, skipping", file);
                        continue;
                    }
                    inlineGiven.put(file, written);
                    given = List.of(NamingTradition.SourceGroup.of(file.toString()));
                } else {
                    given = given(root, file);
                }
                if (given.isEmpty()) {
                    LOGGER.warn("Naming tradition {} names no given-name lists, skipping", file);
                    continue;
                }

                JsonObject familyBody = GsonHelper.getAsJsonObject(root, "family", null);
                FamilyNames writtenFamily = inlineFamilyOf(file, familyBody);
                if (writtenFamily != null) inlineFamily.put(file, writtenFamily);

                loaded.put(file, new NamingTradition(
                        file,
                        given,
                        family(familyBody, file, writtenFamily != null),
                        NamingTradition.Order.byName(GsonHelper.getAsString(root, "order", ""))));
            } catch (Exception exception) {
                LOGGER.warn("Could not load naming tradition {}", file, exception);
            }
        }

        NameLists.replaceInlineGiven(inlineGiven);
        NameLists.addInlineFamily(inlineFamily);
        NamingTraditions.replace(loaded);
        LOGGER.info("Loaded {} naming tradition(s)", loaded.size());
    }

    /**
     * Reads a tradition body, whether it is a whole {@code naming_tradition} file or a {@code naming}
     * block written in place on a culture. Null when it names no usable given names.
     */
    public static @Nullable NamingTradition parse(ResourceLocation file, JsonObject root) {
        JsonElement givenElement = root.get("given");
        List<NamingTradition.SourceGroup> given;
        if (givenElement != null && givenElement.isJsonObject()) {
            if (inlineGivenOf(file, givenElement.getAsJsonObject()) == null) return null;
            given = List.of(NamingTradition.SourceGroup.of(file.toString()));
        } else {
            given = given(root, file);
        }
        if (given.isEmpty()) return null;
        JsonObject familyBody = GsonHelper.getAsJsonObject(root, "family", null);
        return new NamingTradition(file, given,
                family(familyBody, file, inlineFamilyOf(file, familyBody) != null),
                NamingTradition.Order.byName(GsonHelper.getAsString(root, "order", "")));
    }

    /** The given names a tradition body writes in place, or null when it references lists instead. */
    public static @Nullable GivenNames inlineGivenOf(ResourceLocation file, JsonObject body) {
        JsonElement given = body.get("given");
        if (given == null || !given.isJsonObject()) return null;
        Map<Gender, WeightedPool<String>> byGender =
                GivenNameJsonLoader.parse(given.getAsJsonObject(), file);
        return byGender.isEmpty() ? null : new GivenNames(file, byGender);
    }

    /**
     * The family names a family rule writes in place under {@code names}, or null when it references
     * lists instead. Written where they are used, so a culture whose surnames belong to nobody else
     * stays one file.
     */
    public static @Nullable FamilyNames inlineFamilyOf(ResourceLocation file, @Nullable JsonObject family) {
        if (family == null) return null;
        WeightedPool<String> pool = NamePools.pool(family, "names", file);
        return pool == null ? null : new FamilyNames(file, pool);
    }

    private static List<NamingTradition.SourceGroup> given(JsonObject root, ResourceLocation file) {
        return sources(root.get("given"), file, "given");
    }

    private static NamingTradition.Family family(JsonObject family, ResourceLocation file, boolean inlineNames) {
        if (family == null) return NamingTradition.Family.NONE;

        String rawType = GsonHelper.getAsString(family, "type", "");
        NamingTradition.FamilyType type = NamingTradition.FamilyType.byName(rawType);
        if (type == null) {
            LOGGER.warn("Naming tradition {} has unknown family type '{}', treating it as none", file, rawType);
            return NamingTradition.Family.NONE;
        }
        if (type == NamingTradition.FamilyType.NONE) return NamingTradition.Family.NONE;

        // An inherited family name with no list of its own follows the given names: whatever
        // supplies a villager's given-name list supplies their family names too, which is what
        // makes a culture built on someone else's names work without naming that mod.
        List<NamingTradition.SourceGroup> lists = sources(family.get("list"), file, "list");
        // Names written in place are this rule's own list, keyed by the file that wrote them.
        if (inlineNames) lists = List.of(NamingTradition.SourceGroup.of(file.toString()));

        Map<Gender, NamingTradition.Affix> affixes = new EnumMap<>(Gender.class);
        JsonObject affix = GsonHelper.getAsJsonObject(family, "affix", null);
        if (affix != null) {
            for (Gender gender : Gender.values()) {
                JsonObject byGender = GsonHelper.getAsJsonObject(affix, gender.name().toLowerCase(java.util.Locale.ROOT), null);
                if (byGender == null) continue;
                NamingTradition.Affix parsed = new NamingTradition.Affix(
                        GsonHelper.getAsString(byGender, "prefix", ""),
                        GsonHelper.getAsString(byGender, "suffix", ""));
                if (!parsed.isEmpty()) affixes.put(gender, parsed);
            }
        }
        if (type.isDerived() && affixes.isEmpty()) {
            LOGGER.warn("Naming tradition {} derives family names but declares no affix; "
                    + "children will carry a parent's given name unchanged", file);
        }

        return new NamingTradition.Family(
                type, lists,
                NamingTradition.Descent.byName(GsonHelper.getAsString(family, "descent", "")),
                affixes);
    }

    /**
     * List references, read as one or more groups tried in order.
     *
     * <p>Three spellings, and the simplest stays the common one. A bare string is one source. An
     * array of strings or {@code {list, rate}} objects is one group, drawn from together, which is
     * exactly what it has always meant. An array containing any {@code {from: [...]}} object is a
     * list of alternatives instead: each is tried in turn and the first usable one is drawn from,
     * so a group naming a mod that is not installed is passed over whole rather than shrinking to a
     * blend nobody asked for. {@code require: "all"} on a group demands every member.</p>
     *
     * <p>Shared by {@code given} and by a family rule, because a culture that draws given names from
     * several peoples usually draws surnames the same way.</p>
     */
    static List<NamingTradition.SourceGroup> sources(@Nullable JsonElement element,
                                                     ResourceLocation file, String field) {
        List<NamingTradition.SourceGroup> groups = new ArrayList<>();
        if (element == null || element.isJsonNull()) return groups;

        if (element.isJsonPrimitive()) {
            String list = element.getAsString().trim();
            if (!list.isEmpty()) groups.add(NamingTradition.SourceGroup.of(list));
            return groups;
        }
        if (!element.isJsonArray()) {
            LOGGER.warn("Naming tradition {} field '{}' must be a list reference or an array", file, field);
            return groups;
        }

        JsonArray array = element.getAsJsonArray();
        boolean grouped = false;
        for (JsonElement value : array) {
            if (value != null && value.isJsonObject() && value.getAsJsonObject().has("from")) {
                grouped = true;
                break;
            }
        }

        if (!grouped) {
            List<NamingTradition.GivenSource> flat = groupMembers(array, file, field);
            if (!flat.isEmpty()) {
                groups.add(new NamingTradition.SourceGroup(
                        flat, NamingTradition.SourceGroup.Requirement.ANY));
            }
            return groups;
        }

        for (JsonElement value : array) {
            if (value == null) continue;
            if (value.isJsonPrimitive()) {
                String list = value.getAsString().trim();
                if (!list.isEmpty()) groups.add(NamingTradition.SourceGroup.of(list));
                continue;
            }
            if (!value.isJsonObject()) continue;
            JsonObject entry = value.getAsJsonObject();

            if (!entry.has("from")) {
                // A lone source sitting among groups is its own group of one.
                String list = GsonHelper.getAsString(entry, "list", "").trim();
                if (!list.isEmpty()) groups.add(NamingTradition.SourceGroup.of(list));
                continue;
            }
            JsonElement from = entry.get("from");
            if (!from.isJsonArray()) {
                LOGGER.warn("Naming tradition {} field '{}' has a group whose 'from' is not an array",
                        file, field);
                continue;
            }
            List<NamingTradition.GivenSource> members = groupMembers(from.getAsJsonArray(), file, field);
            if (members.isEmpty()) continue;
            boolean all = "all".equalsIgnoreCase(GsonHelper.getAsString(entry, "require", "").trim());
            groups.add(new NamingTradition.SourceGroup(members,
                    all ? NamingTradition.SourceGroup.Requirement.ALL
                        : NamingTradition.SourceGroup.Requirement.ANY));
        }
        return groups;
    }

    /** The weighted sources inside one group. */
    private static List<NamingTradition.GivenSource> groupMembers(JsonArray array,
                                                                  ResourceLocation file, String field) {
        List<NamingTradition.GivenSource> sources = new ArrayList<>();
        for (JsonElement value : array) {
            if (value == null) continue;
            if (value.isJsonPrimitive()) {
                String list = value.getAsString().trim();
                if (!list.isEmpty()) sources.add(new NamingTradition.GivenSource(list, 1.0F));
                continue;
            }
            if (!value.isJsonObject()) continue;
            JsonObject source = value.getAsJsonObject();
            String list = GsonHelper.getAsString(source, "list", "").trim();
            if (list.isEmpty()) continue;
            float rate = GsonHelper.getAsFloat(source, "rate", 1.0F);
            if (rate > 0.0F) sources.add(new NamingTradition.GivenSource(list, rate));
        }
        return sources;
    }
}
