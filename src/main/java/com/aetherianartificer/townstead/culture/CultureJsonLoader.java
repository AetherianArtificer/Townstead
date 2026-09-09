package com.aetherianartificer.townstead.culture;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.aetherianartificer.townstead.naming.NameList;
import com.aetherianartificer.townstead.naming.NameLists;
import com.aetherianartificer.townstead.naming.NamingTradition;
import com.aetherianartificer.townstead.naming.NamingTraditionJsonLoader;
import com.aetherianartificer.townstead.naming.NamingTraditions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads cultures from {@code data/<ns>/culture/<id>.json}.
 *
 * <pre>{@code
 * {
 *   "schema": "townstead:culture/v1",
 *   "name": "townstead_mobs.culture.piglish_ridge",
 *   "naming_tradition": "townstead_mobs:piglish_ridge"
 * }
 * }</pre>
 *
 * <p>A culture that needs no tradition of its own writes one in place instead, so the simple case is
 * a single file:</p>
 *
 * <pre>{@code
 * {
 *   "schema": "townstead:culture/v1",
 *   "name": "townstead_mobs.culture.piglish_ridge",
 *   "naming": {
 *     "given": { "male": ["Grunk"], "female": ["Ashka"] },
 *     "family": { "type": "patronymic", "affix": { "male": { "suffix": "sson" } } }
 *   }
 * }
 * }</pre>
 */
public final class CultureJsonLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(Townstead.MOD_ID + "/CultureJsonLoader");
    private static final Gson GSON = new Gson();
    private static final String SCHEMA = "townstead:culture/v1";

    public CultureJsonLoader() {
        super(GSON, "culture");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<String, String> lang = DataPackLang.loadLangIndex(resourceManager);
        Map<ResourceLocation, Culture> loaded = new LinkedHashMap<>();
        Map<ResourceLocation, NamingTradition> inlineTraditions = new LinkedHashMap<>();
        Map<ResourceLocation, NameList> inlineLists = new LinkedHashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation file = entry.getKey();
            try {
                JsonObject root = GsonHelper.convertToJsonObject(entry.getValue(), file.toString());
                TownsteadSchema.validate(root, SCHEMA);

                Component displayName = root.has("name")
                        ? DataPackLang.parseComponent(root.get("name"), file.toString(), lang)
                        : Component.literal(file.getPath());

                // A tradition written in place belongs to this culture alone and is keyed by its
                // id, so a self-contained culture is one file and shared traditions stay referable.
                ResourceLocation traditionId;
                JsonObject written = GsonHelper.getAsJsonObject(root, "naming", null);
                if (written != null) {
                    NamingTradition inline = NamingTraditionJsonLoader.parse(file, written);
                    if (inline != null) {
                        inlineTraditions.put(file, inline);
                        NameList names = NamingTraditionJsonLoader.inlineListOf(file, written);
                        if (names != null) inlineLists.put(file, names);
                        traditionId = file;
                    } else {
                        LOGGER.warn("Culture {} writes a naming tradition in place but it is unusable", file);
                        traditionId = null;
                    }
                } else {
                    String tradition = GsonHelper.getAsString(root, "naming_tradition", "").trim();
                    traditionId = tradition.isEmpty() ? null : ResourceLocation.tryParse(tradition);
                    if (!tradition.isEmpty() && traditionId == null) {
                        LOGGER.warn("Culture {} names an unreadable naming tradition '{}'", file, tradition);
                    }
                }

                loaded.put(file, new Culture(file, displayName, traditionId));
            } catch (Exception exception) {
                LOGGER.warn("Could not load culture {}", file, exception);
            }
        }

        NameLists.addInline(inlineLists);
        NamingTraditions.addInline(inlineTraditions);
        Cultures.replace(loaded);
        LOGGER.info("Loaded {} culture(s)", loaded.size());
    }

}
