package com.aetherianartificer.townstead.naming;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.Gson;
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
 * {@code name_list}. {@code family.type} is {@code none}, {@code inherited} (names from
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
        Map<ResourceLocation, NameList> inlineLists = new LinkedHashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation file = entry.getKey();
            try {
                JsonObject root = GsonHelper.convertToJsonObject(entry.getValue(), file.toString());
                TownsteadSchema.validate(root, SCHEMA);

                // An object under 'given' is names written in place rather than a reference to a
                // list elsewhere, so a self-contained tradition needs no second file.
                JsonElement givenElement = root.get("given");
                List<NamingTradition.GivenSource> given;
                if (givenElement != null && givenElement.isJsonObject()) {
                    NameList written = inlineList(file, givenElement.getAsJsonObject());
                    if (written == null) {
                        LOGGER.warn("Naming tradition {} writes given names in place but none are usable, skipping", file);
                        continue;
                    }
                    inlineLists.put(file, written);
                    given = List.of(new NamingTradition.GivenSource(file.toString(), 1.0F));
                } else {
                    given = given(root, file);
                }
                if (given.isEmpty()) {
                    LOGGER.warn("Naming tradition {} names no given-name lists, skipping", file);
                    continue;
                }

                loaded.put(file, new NamingTradition(
                        file,
                        given,
                        family(GsonHelper.getAsJsonObject(root, "family", null), file),
                        NamingTradition.Order.byName(GsonHelper.getAsString(root, "order", ""))));
            } catch (Exception exception) {
                LOGGER.warn("Could not load naming tradition {}", file, exception);
            }
        }

        NameLists.replaceInline(inlineLists);
        NamingTraditions.replace(loaded);
        LOGGER.info("Loaded {} naming tradition(s)", loaded.size());
    }

    /**
     * Reads a tradition body, whether it is a whole {@code naming_tradition} file or a {@code naming}
     * block written in place on a culture. Null when it names no usable given names.
     */
    public static @Nullable NamingTradition parse(ResourceLocation file, JsonObject root) {
        JsonElement givenElement = root.get("given");
        List<NamingTradition.GivenSource> given;
        if (givenElement != null && givenElement.isJsonObject()) {
            if (inlineList(file, givenElement.getAsJsonObject()) == null) return null;
            given = List.of(new NamingTradition.GivenSource(file.toString(), 1.0F));
        } else {
            given = given(root, file);
        }
        if (given.isEmpty()) return null;
        return new NamingTradition(file, given,
                family(GsonHelper.getAsJsonObject(root, "family", null), file),
                NamingTradition.Order.byName(GsonHelper.getAsString(root, "order", "")));
    }

    /** The names a tradition body writes in place, or null when it references lists instead. */
    public static @Nullable NameList inlineListOf(ResourceLocation file, JsonObject root) {
        JsonElement given = root.get("given");
        return given != null && given.isJsonObject() ? inlineList(file, given.getAsJsonObject()) : null;
    }

    /** Names written in place on a tradition or culture, keyed by that file's own id. */
    static @Nullable NameList inlineList(ResourceLocation file, JsonObject given) {
        Map<Gender, WeightedPool<String>> byGender = NameLists.completeGiven(
                NameListJsonLoader.pool(given, "male", file),
                NameListJsonLoader.pool(given, "female", file),
                NameListJsonLoader.pool(given, "neutral", file));
        if (byGender.isEmpty()) return null;
        return new NameList(file, byGender, NameListJsonLoader.pool(given, "names", file));
    }

    private static List<NamingTradition.GivenSource> given(JsonObject root, ResourceLocation file) {
        List<NamingTradition.GivenSource> sources = new ArrayList<>();
        JsonElement element = root.get("given");
        if (element == null) return sources;

        // A bare string is the common single-list case; the array form carries rates.
        if (element.isJsonPrimitive()) {
            sources.add(new NamingTradition.GivenSource(element.getAsString().trim(), 1.0F));
            return sources;
        }
        if (!element.isJsonArray()) {
            LOGGER.warn("Naming tradition {} field 'given' must be a list reference or an array", file);
            return sources;
        }

        for (JsonElement value : element.getAsJsonArray()) {
            if (value == null) continue;
            if (value.isJsonPrimitive()) {
                sources.add(new NamingTradition.GivenSource(value.getAsString().trim(), 1.0F));
                continue;
            }
            if (!value.isJsonObject()) continue;
            JsonObject source = value.getAsJsonObject();
            String list = GsonHelper.getAsString(source, "list", "").trim();
            if (list.isEmpty()) continue;
            float rate = GsonHelper.getAsFloat(source, "rate", 1.0F);
            if (rate <= 0.0F) continue;
            sources.add(new NamingTradition.GivenSource(list, rate));
        }
        return sources;
    }

    private static NamingTradition.Family family(JsonObject family, ResourceLocation file) {
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
        String list = GsonHelper.getAsString(family, "list", "").trim();

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
                type, list,
                NamingTradition.Descent.byName(GsonHelper.getAsString(family, "descent", "")),
                affixes);
    }
}
