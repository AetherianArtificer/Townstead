package com.aetherianartificer.townstead.origin.personality;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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

/** Loads {@link PersonalityDef} from {@code data/<ns>/personality/*.json}. */
public final class PersonalityJsonLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/PersonalityJsonLoader");
    private static final Gson GSON = new Gson();

    public PersonalityJsonLoader() {
        super(GSON, "personality");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<String, String> lang = DataPackLang.loadLangIndex(resourceManager);
        Map<ResourceLocation, PersonalityDef> parsed = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation file = entry.getKey();
            try {
                JsonObject obj = GsonHelper.convertToJsonObject(entry.getValue(), file.toString());
                com.aetherianartificer.townstead.data.TownsteadSchema.validate(obj, "townstead:personality/v1");
                String base = GsonHelper.getAsString(obj, "extends", "");
                if (base.isBlank()) {
                    LOGGER.warn("Personality {} has no 'extends' base, skipping", file);
                    continue;
                }
                Component displayName = DataPackLang.parseComponent(obj.get("display_name"), file.toString(), lang);
                Component description = obj.has("description")
                        ? DataPackLang.parseComponent(obj.get("description"), file.toString(), lang)
                        : Component.empty();
                parsed.put(file, new PersonalityDef(file, base, displayName, description));
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse personality {}: {}", file, ex.getMessage());
            }
        }
        PersonalityRegistry.replaceAll(parsed);
        LOGGER.info("Loaded {} personalities", parsed.size());
    }
}
