package com.aetherianartificer.townstead.root.attachment;

import java.util.List;
import java.util.Map;

/**
 * Server-side store of loaded attachments: the synced {@link AttachmentDef}s and
 * slots, plus the content-addressed blob bytes (geometry + textures) keyed by SHA-1.
 * Filled by {@code AttachmentServerLoader} on data reload, read by {@code AttachmentSync}
 * to build the manifest and answer chunk requests.
 */
public final class AttachmentServerData {

    public static final int KIND_GEO = 0;
    public static final int KIND_TEXTURE = 1;
    public static final int KIND_ANIMATION = 2;

    public record Blob(byte[] bytes, int kind) {}

    private static volatile List<AttachmentDef> definitions = List.of();
    private static volatile List<AttachmentPointDef> slots = List.of();
    private static volatile Map<String, Blob> blobs = Map.of();
    // Named datapack textures: logical id ("ns:textures/...") -> SHA-1 of the PNG, so rig/face
    // textures ship over the same blob sync instead of needing a resource pack.
    private static volatile Map<String, String> namedTextures = Map.of();
    // Named datapack geometry: logical id ("ns:geo/...") -> SHA-1 of the .geo.json, so a custom-geometry
    // rig model ships over the same blob sync (twin of namedTextures).
    private static volatile Map<String, String> namedGeo = Map.of();
    // Named datapack animations: logical id ("ns:animations/...") -> SHA-1, so a rig's clips ship too.
    private static volatile Map<String, String> namedAnimations = Map.of();
    // The authored JSON of each definition (by id), kept so the live-adjust command can dump a
    // file-ready def (the synced view replaces geometry/texture refs with hashes).
    private static volatile Map<String, com.google.gson.JsonObject> sources = Map.of();

    private AttachmentServerData() {}

    public static void set(List<AttachmentDef> defs, List<AttachmentPointDef> slotDefs, Map<String, Blob> blobStore,
                           Map<String, String> textureIds, Map<String, String> geoIds,
                           Map<String, String> animationIds,
                           Map<String, com.google.gson.JsonObject> sourceJson) {
        definitions = List.copyOf(defs);
        slots = List.copyOf(slotDefs);
        blobs = Map.copyOf(blobStore);
        namedTextures = Map.copyOf(textureIds);
        namedGeo = Map.copyOf(geoIds);
        namedAnimations = Map.copyOf(animationIds);
        sources = Map.copyOf(sourceJson);
    }

    /** Swap one definition in place (the live-adjust command); lasts until the next data reload. */
    public static void replaceDefinition(AttachmentDef def) {
        java.util.List<AttachmentDef> updated = new java.util.ArrayList<>(definitions);
        updated.removeIf(d -> d.id().equals(def.id()));
        updated.add(def);
        definitions = List.copyOf(updated);
    }

    /** The authored JSON a definition was loaded from, or null (used by the dump command). */
    public static com.google.gson.JsonObject sourceJson(String id) {
        return sources.get(id);
    }

    public static List<AttachmentDef> definitions() {
        return definitions;
    }

    public static List<AttachmentPointDef> slots() {
        return slots;
    }

    public static Blob blob(String sha1) {
        return blobs.get(sha1);
    }

    /** Named datapack textures (logical id -> SHA-1), shipped in the manifest. */
    public static Map<String, String> namedTextures() {
        return namedTextures;
    }

    /** Named datapack geometry (logical id -> SHA-1), shipped in the manifest. */
    public static Map<String, String> namedGeo() {
        return namedGeo;
    }

    /** Named datapack animations (logical id -> SHA-1), shipped in the manifest. */
    public static Map<String, String> namedAnimations() {
        return namedAnimations;
    }
}
