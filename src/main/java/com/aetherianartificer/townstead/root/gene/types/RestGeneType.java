package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneType;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * How the bearer sleeps: {@code recovery} scales how much a night in a bed restores,
 * {@code prefers} names rest spots it seeks out over others, and {@code prevent} keeps it from
 * sleeping while a condition holds, with an optional {@code message} for a player (Apoli's
 * {@code prevent_sleep}).
 *
 * <p>JSON: {@code { "type":"pheno:rest", "recovery":0.8, "prefers":["examplepack:nest"],
 * "prevent":{ "type":"pheno:hungry" }, "message":"message.examplepack.too_hungry" }}</p>
 */
public final class RestGeneType implements GeneType {
    public static final String KEY = "pheno:rest";

    public record Instance(float recovery, List<ResourceLocation> prefers, @Nullable Condition prevent,
                           String message) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        List<ResourceLocation> prefers = new ArrayList<>();
        if (json.has("prefers")) {
            JsonElement raw = json.get("prefers");
            if (raw.isJsonArray()) {
                for (JsonElement element : raw.getAsJsonArray()) {
                    ResourceLocation id = DataPackLang.parseId(element.getAsString());
                    if (id != null) prefers.add(id);
                }
            } else {
                ResourceLocation id = DataPackLang.parseId(raw.getAsString());
                if (id != null) prefers.add(id);
            }
        }
        return new Instance(GsonHelper.getAsFloat(json, "recovery", 1f), List.copyOf(prefers),
                json.has("prevent") ? Conditions.parse(json.get("prevent")) : null,
                GsonHelper.getAsString(json, "message", ""));
    }
}
