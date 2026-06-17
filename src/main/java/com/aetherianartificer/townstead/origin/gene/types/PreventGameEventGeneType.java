package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.HashSet;
import java.util.Set;

/**
 * Stops the bearer from emitting named game events (Origins' {@code prevent_game_event},
 * e.g. Feline velvet paws suppressing {@code minecraft:step} so sculk/wardens don't sense
 * footsteps). {@code event} names one id and/or {@code events} lists several. Enforced by
 * a mixin on the entity's game-event emission.
 *
 * <p>JSON: {@code { "type":"pheno:prevent_game_event", "event":"minecraft:step" }}</p>
 */
public final class PreventGameEventGeneType implements GeneType {

    public static final String KEY = "pheno:prevent_game_event";

    public record Instance(Set<ResourceLocation> events) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        Set<ResourceLocation> events = new HashSet<>();
        if (json.has("event")) {
            ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "event", ""));
            if (id != null) events.add(id);
        }
        if (json.has("events") && json.get("events").isJsonArray()) {
            JsonArray arr = json.getAsJsonArray("events");
            for (var el : arr) {
                ResourceLocation id = DataPackLang.parseId(el.getAsString());
                if (id != null) events.add(id);
            }
        }
        return events.isEmpty() ? null : new Instance(events);
    }
}
