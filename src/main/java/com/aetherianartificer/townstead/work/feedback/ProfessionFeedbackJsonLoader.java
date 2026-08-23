package com.aetherianartificer.townstead.work.feedback;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.ModGate;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Loads profession feedback settings and singular, file-named rules. */
public final class ProfessionFeedbackJsonLoader
        extends SimplePreparableReloadListener<List<ProfessionFeedbackJsonLoader.Raw>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/ProfessionFeedback");

    record Raw(ResourceLocation source, JsonObject json) {}

    @Override
    protected List<Raw> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        List<Raw> out = new ArrayList<>();
        Map<ResourceLocation, Resource> resources = resourceManager.listResources("profession", id -> {
            String path = id.getPath();
            return path.endsWith("/feedback.json")
                    || (path.endsWith(".json") && path.contains("/feedback/"));
        });
        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            try (Reader reader = entry.getValue().openAsReader()) {
                out.add(new Raw(entry.getKey(), JsonParser.parseReader(reader).getAsJsonObject()));
            } catch (Exception exception) {
                LOGGER.warn("Failed to read profession feedback {}: {}", entry.getKey(), exception.getMessage());
            }
        }
        return List.copyOf(out);
    }

    @Override
    protected void apply(List<Raw> prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        List<ProfessionFeedbackDocument.Settings> settings = new ArrayList<>();
        List<ProfessionFeedbackDocument.Rule> rules = new ArrayList<>();
        for (Raw raw : prepared) {
            try {
                if (!modGateActive(raw.json())) continue;
                ResourceLocation profession = inferProfession(raw.source());
                if (profession == null) throw new IllegalArgumentException("profession cannot be inferred from the path");
                String rule = inferRule(raw.source());
                if (rule == null) {
                    settings.add(ProfessionFeedbackDocument.Settings.parse(profession, raw.json()));
                } else {
                    rules.add(ProfessionFeedbackDocument.Rule.parse(raw.source(), profession, rule, raw.json()));
                }
            } catch (Exception exception) {
                LOGGER.warn("Failed to parse profession feedback {}: {}", raw.source(), exception.getMessage());
            }
        }
        ProfessionFeedbackRegistry.replaceAll(settings, rules);
        LOGGER.info("Loaded {} profession feedback rules for {} professions",
                rules.size(), ProfessionFeedbackRegistry.all().size());
    }

    private static boolean modGateActive(JsonObject json) {
        if (!json.has("mods")) return true;
        Boolean active = ModGate.evaluate(json.get("mods"));
        if (active == null) throw new IllegalArgumentException("'mods' is not a valid mod gate");
        return active;
    }

    private static ResourceLocation inferProfession(ResourceLocation location) {
        String path = location.getPath();
        int start = "profession/".length();
        int end = path.indexOf('/', start);
        if (end <= start) return null;
        String name = path.substring(start, end);
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath(location.getNamespace(), name);
        //?} else {
        /*return new ResourceLocation(location.getNamespace(), name);
        *///?}
    }

    /** Null identifies the profession-level feedback.json settings sidecar. */
    private static String inferRule(ResourceLocation location) {
        String path = location.getPath();
        int marker = path.indexOf("/feedback/");
        if (marker < 0) return null;
        String id = path.substring(marker + "/feedback/".length(), path.length() - ".json".length());
        if (id.isBlank()) throw new IllegalArgumentException("feedback rule filename is empty");
        return id;
    }
}
