package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.ModGate;
import com.aetherianartificer.townstead.pheno.power.Power;
import com.aetherianartificer.townstead.pheno.power.PowerComponent;
import com.aetherianartificer.townstead.pheno.power.PowerSource;
import com.aetherianartificer.townstead.root.gene.GeneTypes;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Powers every character carries regardless of Root or career: the third {@link PowerSource}
 * beside genes and skills. It exists for shared substrate — a stamina meter that career
 * abilities all spend from has to be ONE power with ONE stable id, and neither of the other
 * sources can provide that. A skill-granted meter takes the skill's id, so two ability skills
 * would give a character two unrelated bars; a Root-granted one would be missing from every
 * Root a pack author writes without knowing to include it.
 *
 * <p>Declared as {@code data/<ns>/baseline_power/<name>.json}, whose body is an ordinary pheno
 * component, registered under {@code <ns>:<name>}. Replaced each datapack reload.</p>
 */
public final class BaselinePowers {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/BaselinePowers");
    private static final String DIRECTORY = "baseline_power";

    private static volatile Map<ResourceLocation, PowerComponent> POWERS = Map.of();

    private BaselinePowers() {}

    public static void replaceAll(Map<ResourceLocation, PowerComponent> next) {
        POWERS = Map.copyOf(next);
    }

    public static Map<ResourceLocation, PowerComponent> all() {
        return POWERS;
    }

    /** Contributes the baseline set to every living entity. Empty is the free common case. */
    public static final class Source implements PowerSource {
        @Override
        public void collect(LivingEntity entity, List<Power> out) {
            for (Map.Entry<ResourceLocation, PowerComponent> e : POWERS.entrySet()) {
                out.add(new Power(e.getKey(), e.getValue()));
            }
        }
    }

    /** Loads {@code data/<ns>/baseline_power/*.json}; entries behind unmet {@code mods} gates don't exist. */
    public static final class Loader
            extends SimplePreparableReloadListener<Map<ResourceLocation, JsonObject>> {

        @Override
        protected Map<ResourceLocation, JsonObject> prepare(ResourceManager resourceManager,
                                                           ProfilerFiller profiler) {
            Map<ResourceLocation, JsonObject> out = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, Resource> e : resourceManager
                    .listResources(DIRECTORY, loc -> loc.getPath().endsWith(".json")).entrySet()) {
                ResourceLocation file = e.getKey();
                String path = file.getPath();
                ResourceLocation id = ResourceLocation.tryParse(file.getNamespace() + ":"
                        + path.substring(DIRECTORY.length() + 1, path.length() - ".json".length()));
                if (id == null) continue;
                try (Reader reader = e.getValue().openAsReader()) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (parsed.isJsonObject()) out.put(id, parsed.getAsJsonObject());
                } catch (Exception ex) {
                    LOGGER.warn("Failed to read baseline power {}: {}", file, ex.getMessage());
                }
            }
            return out;
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonObject> prepared, ResourceManager resourceManager,
                             ProfilerFiller profiler) {
            Map<ResourceLocation, PowerComponent> powers = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, JsonObject> e : prepared.entrySet()) {
                JsonObject obj = e.getValue();
                if (obj.has("mods") && !Boolean.TRUE.equals(ModGate.evaluate(obj.get("mods")))) {
                    LOGGER.debug("Baseline power {} skipped: mods gate unmet or malformed", e.getKey());
                    continue;
                }
                String type = obj.has("type") ? obj.get("type").getAsString() : "";
                var geneType = GeneTypes.get(type);
                if (geneType.isEmpty()) {
                    LOGGER.warn("Baseline power {} has unknown type '{}'", e.getKey(), type);
                    continue;
                }
                PowerComponent component = geneType.get().parse(obj, Map.of());
                if (component == null) {
                    LOGGER.warn("Baseline power {} has invalid config for type '{}'", e.getKey(), type);
                    continue;
                }
                powers.put(e.getKey(), component);
            }
            replaceAll(powers);
            if (!powers.isEmpty()) LOGGER.info("Loaded {} baseline powers", powers.size());
        }
    }
}
