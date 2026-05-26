package com.aetherianartificer.townstead.client.gui.calendar;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side index of stamp art from installed resource packs: PNGs under
 * {@code assets/<namespace>/textures/stamps/*.png}. Enumerated with
 * {@code listResources} rather than {@code getNamespaces()} so 1.20.1 Forge
 * doesn't drop data-only namespaces.
 */
public final class StampCatalog {

    private static final String STAMP_DIR = "textures/stamps";

    /** {@code textureId} is the blit location; {@code sourcePack} names the providing pack. */
    public record Entry(String textureId, ResourceLocation texture, String displayName, String sourcePack) {}

    private static List<Entry> cache = null;
    // Presence of arbitrary server-supplied texture ids (for the missing-art placeholder).
    private static final Map<String, Boolean> presence = new ConcurrentHashMap<>();

    private StampCatalog() {}

    /** Rebuild on next access (call on resource reload). */
    public static void refresh() {
        cache = null;
        presence.clear();
    }

    public static List<Entry> list() {
        List<Entry> local = cache;
        if (local != null) return local;
        local = build();
        cache = local;
        return local;
    }

    private static List<Entry> build() {
        List<Entry> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return out;
        try {
            Map<ResourceLocation, Resource> found = mc.getResourceManager()
                    .listResources(STAMP_DIR, loc -> loc.getPath().endsWith(".png"));
            for (Map.Entry<ResourceLocation, Resource> en : found.entrySet()) {
                ResourceLocation rl = en.getKey();
                String pack = cleanPackName(en.getValue().sourcePackId());
                out.add(new Entry(rl.toString(), rl, prettify(rl.getPath()), pack));
            }
            out.sort((a, b) -> a.displayName().compareToIgnoreCase(b.displayName()));
        } catch (Exception ignored) {
            // A pack scan failure must never break the calendar screen.
        }
        return out;
    }

    /** Whether the stamp texture id resolves in the current packs. */
    public static boolean hasTexture(String textureId) {
        Boolean cached = presence.get(textureId);
        if (cached != null) return cached;
        boolean present;
        try {
            ResourceLocation rl = parse(textureId);
            present = Minecraft.getInstance().getResourceManager().getResource(rl).isPresent();
        } catch (Exception ex) {
            present = false;
        }
        presence.put(textureId, present);
        return present;
    }

    /** Tidy a pack id for display ("file/Stamps_Pack_1" -> "Stamps_Pack_1"). */
    private static String cleanPackName(String sourcePackId) {
        if (sourcePackId == null) return "";
        String s = sourcePackId;
        int slash = s.lastIndexOf('/');
        if (slash >= 0 && slash < s.length() - 1) s = s.substring(slash + 1);
        return s;
    }

    static ResourceLocation parse(String s) {
        //? if >=1.21 {
        return ResourceLocation.parse(s);
        //?} else {
        /*return new ResourceLocation(s);
        *///?}
    }

    /** A 1-character mark for a stamp whose art isn't installed. */
    public static String shortLabel(String textureId) {
        String base = baseName(textureId);
        return base.isEmpty() ? "?" : base.substring(0, 1).toUpperCase();
    }

    private static String baseName(String path) {
        int slash = path.lastIndexOf('/');
        String file = slash >= 0 ? path.substring(slash + 1) : path;
        if (file.endsWith(".png")) file = file.substring(0, file.length() - 4);
        return file.replace('_', ' ').replace('-', ' ').trim();
    }

    /** "textures/stamps/red_star.png" -> "Red Star". */
    private static String prettify(String path) {
        String file = baseName(path);
        if (file.isEmpty()) return "?";
        StringBuilder sb = new StringBuilder(file.length());
        boolean cap = true;
        for (char c : file.toCharArray()) {
            if (c == ' ') { cap = true; sb.append(c); }
            else if (cap) { sb.append(Character.toUpperCase(c)); cap = false; }
            else sb.append(c);
        }
        return sb.toString();
    }
}
