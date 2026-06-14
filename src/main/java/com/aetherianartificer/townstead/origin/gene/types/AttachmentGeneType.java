package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * A cosmetic model attachment a race wears (elf ears, tusks, horns, …). The
 * {@code attachment} id points at a client-side attachment definition (geometry
 * + texture + anchor part + tint), rendered as an extra layer anchored to the
 * villager's model so it follows animation. Display-only data here; the render
 * layer and Bedrock-geometry loader are the client half.
 *
 * <p>JSON: {@code { "type":"townstead_origins:attachment",
 * "attachment":"townstead_origins:elf_ears" }}</p>
 */
public final class AttachmentGeneType implements GeneType {

    public static final String KEY = "townstead_origins:attachment";

    public record Instance(String attachmentId) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.attachment(attachmentId); }
    }

    @Override
    public String key() { return KEY; }

    @Override
    public GeneInstance parse(JsonObject json) {
        String id = GsonHelper.getAsString(json, "attachment", "");
        if (id.isBlank()) return null;
        return new Instance(id);
    }
}
