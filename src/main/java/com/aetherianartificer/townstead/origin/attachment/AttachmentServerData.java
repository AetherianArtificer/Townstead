package com.aetherianartificer.townstead.origin.attachment;

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

    public record Blob(byte[] bytes, int kind) {}

    private static volatile List<AttachmentDef> definitions = List.of();
    private static volatile List<AttachmentPointDef> slots = List.of();
    private static volatile Map<String, Blob> blobs = Map.of();
    // Named datapack textures: logical id ("ns:textures/...") -> SHA-1 of the PNG, so rig/face
    // textures ship over the same blob sync instead of needing a resource pack.
    private static volatile Map<String, String> namedTextures = Map.of();

    private AttachmentServerData() {}

    public static void set(List<AttachmentDef> defs, List<AttachmentPointDef> slotDefs, Map<String, Blob> blobStore,
                           Map<String, String> textureIds) {
        definitions = List.copyOf(defs);
        slots = List.copyOf(slotDefs);
        blobs = Map.copyOf(blobStore);
        namedTextures = Map.copyOf(textureIds);
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
}
