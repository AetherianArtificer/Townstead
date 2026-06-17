package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Draws a full-screen texture over the HUD for the bearing player (Apoli {@code overlay}):
 * a racial vignette, tunnel-vision, eye-shape mask, etc. Player-only (it is a HUD effect).
 * The server evaluates the optional {@link Condition} each tick and syncs the active set;
 * the texture id rides the gene catalog so the client can blit it without a resource pack.
 *
 * <p>JSON: {@code { "type":"pheno:overlay",
 * "texture":"pheno:textures/overlay/dark_vision.png", "alpha":0.6,
 * "condition":{ "type":"pheno:in_rain" } }}</p>
 */
public final class OverlayGeneType implements GeneType {

    public static final String KEY = "pheno:overlay";

    public record Instance(ResourceLocation texture, float alpha, @Nullable Condition condition)
            implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.overlay(texture.toString(), alpha); }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        ResourceLocation texture = DataPackLang.parseId(GsonHelper.getAsString(json, "texture", ""));
        if (texture == null) return null;
        float alpha = Math.max(0f, Math.min(1f, GsonHelper.getAsFloat(json, "alpha", 1f)));
        Condition condition = json.has("condition") ? Conditions.parse(json.get("condition")) : null;
        return new Instance(texture, alpha, condition);
    }
}
