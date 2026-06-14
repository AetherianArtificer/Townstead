package com.aetherianartificer.townstead.origin.attachment;

import org.jetbrains.annotations.Nullable;

/**
 * The synced form of an attachment: render placement plus the SHA-1 hashes of its
 * geometry and texture blobs (the bytes are content-addressed and pulled/cached
 * separately). Authored as {@code data/<ns>/attachment/<id>.json}; the server
 * resolves the geometry/texture file references to hashes and syncs this view.
 *
 * <p>Targeting precedence: {@code targetTag} (every point carrying the tag, so one
 * "ears" attachment fits any rig that exposes an {@code ear} point) -> {@code targetPoint}
 * (one named point) -> {@code bone} (anchor straight to a model bone).</p>
 */
public record AttachmentDef(
        String id,
        String geoSha1,
        String textureSha1,
        @Nullable String targetTag,
        @Nullable String targetPoint,
        String bone,
        float[] offset,
        float[] rotation,
        float scale,
        int tint
) {}
