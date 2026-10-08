package com.aetherianartificer.townstead.naming;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.WeightedPool;
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
 * Loads given names from {@code data/<ns>/given_name/<id>.json}.
 *
 * <pre>{@code
 * {
 *   "schema": "townstead:given_name/v1",
 *   "male":    ["Grunk", "Zogg"],
 *   "female":  { "Ashka": 3, "Vurr": 1 },
 *   "neutral": ["Sesh"]
 * }
 * }</pre>
 *
 * <p>First names only. Surnames live in {@code family_name} under the same id, so
 * {@code townstead_classic:highhold} means that people's given names here and their family names
 * there, and neither can be mistaken for the other.</p>
 */
public final class GivenNameJsonLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(Townstead.MOD_ID + "/GivenNameJsonLoader");
    private static final Gson GSON = new Gson();
    private static final String SCHEMA = "townstead:given_name/v1";

    public GivenNameJsonLoader() {
        super(GSON, "given_name");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<ResourceLocation, GivenNames> loaded = new LinkedHashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation file = entry.getKey();
            try {
                JsonObject root = GsonHelper.convertToJsonObject(entry.getValue(), file.toString());
                TownsteadSchema.validate(root, SCHEMA);

                Map<Gender, WeightedPool<String>> byGender = parse(root, file);
                if (byGender.isEmpty()) {
                    LOGGER.warn("Given names {} hold none that are usable, skipping", file);
                    continue;
                }
                loaded.put(file, new GivenNames(file, byGender));
            } catch (Exception exception) {
                LOGGER.warn("Could not load given names {}", file, exception);
            }
        }

        NameLists.replaceGiven(loaded);
        LOGGER.info("Loaded {} given-name list(s)", loaded.size());
    }

    /** The gendered pools a body declares, filled out so no binary gender is ever missing. */
    public static Map<Gender, WeightedPool<String>> parse(JsonObject root, ResourceLocation file) {
        return NameLists.completeGiven(
                NamePools.pool(root, "male", file),
                NamePools.pool(root, "female", file),
                NamePools.pool(root, "neutral", file));
    }
}
