package com.aetherianartificer.townstead.naming;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
 * Loads family names from {@code data/<ns>/family_name/<id>.json}.
 *
 * <pre>{@code
 * {
 *   "schema": "townstead:family_name/v1",
 *   "names": ["Ashmaw", "Emberhoof"]
 * }
 * }</pre>
 *
 * <p>Surnames, house names and clan names, ungendered. A people whose given names live in
 * {@code given_name} under the same id is one people described in two files, and a tradition
 * references that one id from whichever of its two slots needs it.</p>
 */
public final class FamilyNameJsonLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(Townstead.MOD_ID + "/FamilyNameJsonLoader");
    private static final Gson GSON = new Gson();
    private static final String SCHEMA = "townstead:family_name/v1";

    public FamilyNameJsonLoader() {
        super(GSON, "family_name");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<ResourceLocation, FamilyNames> loaded = new LinkedHashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation file = entry.getKey();
            try {
                JsonObject root = GsonHelper.convertToJsonObject(entry.getValue(), file.toString());
                TownsteadSchema.validate(root, SCHEMA);

                WeightedPool<String> names = NamePools.pool(root, "names", file);
                if (names == null) {
                    LOGGER.warn("Family names {} hold none that are usable, skipping", file);
                    continue;
                }
                loaded.put(file, new FamilyNames(file, names));
            } catch (Exception exception) {
                LOGGER.warn("Could not load family names {}", file, exception);
            }
        }

        NameLists.replaceFamily(loaded);
        LOGGER.info("Loaded {} family-name list(s)", loaded.size());
    }
}
