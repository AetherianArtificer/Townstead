package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Puts the carrier in a disposition group, ahead of its {@code entity_group} gene and body type,
 * so an aspect can change who counts it as kin or enemy.
 *
 * <p>JSON: {@code { "type":"pheno:disposition_group", "group":"vampire" }}</p>
 */
public final class DispositionGroupGeneType implements GeneType {

    public static final String KEY = "pheno:disposition_group";
    private static final ResourceLocation LOCUS = ResourceLocation.tryParse(KEY);

    public record Instance(String group) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        String group = GsonHelper.getAsString(json, "group", "").trim();
        return group.isEmpty() ? null : new Instance(group);
    }

    @Override
    public ResourceLocation defaultLocus(GeneInstance instance) {
        return LOCUS;
    }
}
