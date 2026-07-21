package com.aetherianartificer.townstead.profession.career;

import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import net.minecraft.resources.ResourceLocation;

/**
 * Career identity vocabulary. A career IS a Minecraft profession, extended: its id is the
 * profession's registry id, and everything Careers adds (history, skills, advanced classes)
 * layers onto that identity through the data-pack {@code profession/*.json} definition.
 * The constants below are the professions Townstead ships work engines for; packs add more
 * by registering a def under the profession id they extend.
 */
public final class Careers {

    public static final ResourceLocation FARMER = id("minecraft", "farmer");
    public static final ResourceLocation BUTCHER = id("minecraft", "butcher");
    public static final ResourceLocation SHEPHERD = id("minecraft", "shepherd");
    public static final ResourceLocation COOK = id("townstead", "cook");
    public static final ResourceLocation BARISTA = id("townstead", "barista");

    private Careers() {}

    /**
     * Canonicalizes a data-authored career reference: a full id passes through; a bare name
     * (legacy saves, terse pack JSON) resolves against the registered defs by path.
     */
    public static String resolve(String raw) {
        if (raw == null || raw.isBlank() || raw.contains(":")) return raw;
        for (ResourceLocation id : ProfessionDefs.all().keySet()) {
            if (id.getPath().equals(raw)) return id.toString();
        }
        return raw;
    }

    private static ResourceLocation id(String namespace, String path) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
        //?} else {
        /*return new ResourceLocation(namespace, path);
        *///?}
    }
}
