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
 * Stops named sounds from being emitted for the bearer (Apugli's {@code prevent_sound}).
 * {@code sound} names one id and/or {@code sounds} lists several. Enforced server-side off
 * the level-sound event, so it cancels the bearer's broadcast sounds (hurt, death, ambient,
 * server-side footsteps) for everyone. (A bearer's own client-predicted sounds in
 * multiplayer ride the client, which has no gene data, so those aren't covered; in
 * singleplayer everything is.)
 *
 * <p>JSON: {@code { "type":"pheno:prevent_sound", "sound":"minecraft:entity.player.hurt" }}</p>
 */
public final class PreventSoundGeneType implements GeneType {

    public static final String KEY = "pheno:prevent_sound";

    public record Instance(Set<ResourceLocation> sounds) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        Set<ResourceLocation> sounds = new HashSet<>();
        if (json.has("sound")) {
            ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "sound", ""));
            if (id != null) sounds.add(id);
        }
        if (json.has("sounds") && json.get("sounds").isJsonArray()) {
            JsonArray arr = json.getAsJsonArray("sounds");
            for (var el : arr) {
                ResourceLocation id = DataPackLang.parseId(el.getAsString());
                if (id != null) sounds.add(id);
            }
        }
        return sounds.isEmpty() ? null : new Instance(sounds);
    }
}
