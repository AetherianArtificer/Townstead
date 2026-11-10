package com.aetherianartificer.townstead.client.naming;

import com.aetherianartificer.townstead.naming.NamingCatalogS2CPayload;
import com.aetherianartificer.townstead.naming.NamingTradition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;

/** The naming traditions the server offered, and how they read on screen. */
public final class NamingCatalogClient {

    private static volatile List<NamingCatalogS2CPayload.Entry> entries = List.of();

    private NamingCatalogClient() {}

    public static void set(NamingCatalogS2CPayload payload) {
        entries = payload.entries();
    }

    public static List<NamingCatalogS2CPayload.Entry> entries() {
        return entries;
    }

    public static NamingTradition.FamilyType rule(String id) {
        for (NamingCatalogS2CPayload.Entry entry : entries) {
            if (entry.id().equals(id)) return entry.rule();
        }
        return NamingTradition.FamilyType.NONE;
    }

    /** A tradition's name, which a pack can translate under {@code townstead.naming_tradition.<ns>.<path>}. */
    public static Component label(String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        if (parsed == null) return Component.literal(id);
        // A culture's name stands for its tradition only when no other culture shares that tradition.
        String offered = "";
        int cultures = 0;
        for (NamingCatalogS2CPayload.Entry entry : entries) {
            if (entry.id().equals(id) && !entry.culture().isEmpty()) {
                offered = entry.label();
                cultures++;
            }
        }
        if (cultures != 1) offered = "";
        return Component.translatableWithFallback(
                "townstead.naming_tradition." + parsed.getNamespace() + "." + parsed.getPath().replace('/', '.'),
                offered.isEmpty() ? prettify(parsed.getPath()) : offered);
    }

    /** Whether a search matches what a pick is called or its id. A blank search matches everything. */
    public static boolean matches(String query, String label, String id) {
        String wanted = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return wanted.isEmpty() || label.toLowerCase(Locale.ROOT).contains(wanted)
                || id.toLowerCase(Locale.ROOT).contains(wanted);
    }

    static String prettify(String path) {
        String last = path.substring(path.lastIndexOf('/') + 1);
        StringBuilder out = new StringBuilder();
        for (String word : last.split("_")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return out.toString();
    }
}
