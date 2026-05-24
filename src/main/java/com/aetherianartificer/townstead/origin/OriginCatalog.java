package com.aetherianartificer.townstead.origin;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side: flattens the loaded {@link OriginRegistry} into
 * {@link OriginCatalogEntry}s for the picker, so a remote client (whose
 * datapack-driven registry is empty) can render and label origins.
 */
public final class OriginCatalog {

    private OriginCatalog() {}

    public static List<OriginCatalogEntry> build() {
        List<OriginCatalogEntry> out = new ArrayList<>();
        for (Origin origin : OriginRegistry.all()) {
            String name = origin.displayName().getString();
            Demonym demonym = OriginRegistry.resolveDemonym(origin);
            String singular = demonym != null ? demonym.singular().getString() : name;
            String plural = demonym != null ? demonym.plural().getString() : name;
            Component backstory = OriginRegistry.resolveBackstory(origin);
            out.add(new OriginCatalogEntry(
                    origin.id().toString(), name, singular, plural,
                    backstory != null ? backstory.getString() : ""));
        }
        return out;
    }
}
