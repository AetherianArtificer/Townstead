package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.origin.CanonicalStage;
import com.aetherianartificer.townstead.origin.LifeCycle;
import com.aetherianartificer.townstead.origin.LifeStage;
import com.aetherianartificer.townstead.origin.StageEndAction;
import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.util.GsonHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A race's whole life cycle, carried as a single heritable gene: the ordered
 * {@link LifeStage} list (id, label, {@code presents_as}, base days) plus the
 * spawn-time {@code variance}. The gene <em>is</em> the cycle — inheriting it is
 * how a villager comes to age like a human, a butterfly, or anything else.
 *
 * <p>Cycle genes share a {@code locus} (e.g. {@code townstead_origins:life_cycle})
 * so a villager expresses exactly one body plan; a hybrid's rival alleles are
 * resolved by dominance/weight in {@code OriginRegistry.effectiveCycleGene}.</p>
 *
 * <p>JSON:</p>
 * <pre>{@code
 * {
 *   "type": "townstead_origins:life_cycle",
 *   "locus": "townstead_origins:life_cycle",
 *   "variance": 0.15,
 *   "stages": [
 *     { "id": "baby", "label": { "translate": "..." }, "presents_as": "baby", "days": 4 },
 *     ...
 *     { "id": "senior", ..., "presents_as": "senior", "days": 60, "on_end": "stay" }
 *   ]
 * }
 * }</pre>
 *
 * <p>{@code days} is the absolute base duration in Townstead calendar days;
 * {@code variance} is the per-stage random spread applied at spawn (rolled
 * independently per stage, so the same villager can have a fast childhood and a
 * long senior).</p>
 */
public final class LifeCycleGeneType implements GeneType {

    public static final String KEY = "townstead_origins:life_cycle";

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/LifeCycleGene");

    public record Instance(LifeCycle cycle, float variance) implements GeneInstance {
        public Instance {
            if (cycle == null) cycle = LifeCycle.EMPTY;
            if (variance < 0f) variance = 0f;
        }

        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() { return KEY; }

    @Override
    public GeneInstance parse(JsonObject json) {
        return parse(json, Map.of());
    }

    @Override
    public GeneInstance parse(JsonObject json, Map<String, String> lang) {
        if (!json.has("stages") || !json.get("stages").isJsonArray()) {
            LOGGER.warn("Life-cycle gene missing 'stages' array");
            return null;
        }
        List<LifeStage> stages = parseStages(json.getAsJsonArray("stages"), KEY, lang);
        if (stages.isEmpty()) return null;
        float variance = GsonHelper.getAsFloat(json, "variance", 0.0f);
        // Ageless: the cycle names its stages but never progresses by the calendar; the villager
        // freezes at its resolved stage (skeletons and other non-aging species).
        boolean ageless = GsonHelper.getAsBoolean(json, "ageless", false);
        return new Instance(new LifeCycle(stages, ageless), variance);
    }

    private static List<LifeStage> parseStages(JsonArray arr, String context, Map<String, String> lang) {
        List<LifeStage> stages = new ArrayList<>(arr.size());
        for (int i = 0; i < arr.size(); i++) {
            JsonElement el = arr.get(i);
            if (!el.isJsonObject()) {
                LOGGER.warn("{} — stage at index {} is not an object, skipping", context, i);
                continue;
            }
            JsonObject s = el.getAsJsonObject();
            String id = GsonHelper.getAsString(s, "id", "");
            if (id.isBlank()) {
                LOGGER.warn("{} — stage at index {} missing 'id', skipping", context, i);
                continue;
            }
            CanonicalStage presentsAs = CanonicalStage.parse(GsonHelper.getAsString(s, "presents_as", ""));
            if (presentsAs == null) {
                LOGGER.warn("{} — stage '{}' missing/invalid 'presents_as', skipping", context, id);
                continue;
            }
            int days = GsonHelper.getAsInt(s, "days", 1);
            Component label = s.has("label")
                    ? DataPackLang.parseComponent(s.get("label"), context + "." + id, lang)
                    : Component.literal(id);
            StageEndAction onEnd = s.has("on_end")
                    ? StageEndAction.parse(GsonHelper.getAsString(s, "on_end", ""))
                    : null;
            // Model size for this stage, defaulting from the canonical kind.
            float scale = GsonHelper.getAsFloat(s, "scale", presentsAs.defaultScale());
            float narrStart;
            float narrEnd;
            boolean explicitNarrative = s.has("narrative_age") && s.get("narrative_age").isJsonArray();
            if (explicitNarrative) {
                // Explicit override for off-human-scale creatures whose apparent-age
                // curve shouldn't derive linearly from days (e.g. a mayfly or dragon).
                JsonArray na = s.getAsJsonArray("narrative_age");
                narrStart = na.size() >= 1 ? na.get(0).getAsFloat() : 0f;
                narrEnd = na.size() >= 2 ? na.get(1).getAsFloat() : narrStart;
            } else {
                // Normal path: apparent age derives linearly from days alive at spawn
                // (see LifeStageProgression). The canonical band is only a fallback.
                narrStart = presentsAs.defaultNarrativeStart();
                narrEnd = presentsAs.defaultNarrativeEnd();
            }
            stages.add(new LifeStage(id, label, presentsAs, days, narrStart, narrEnd, onEnd, scale, explicitNarrative));
        }
        return stages;
    }
}
