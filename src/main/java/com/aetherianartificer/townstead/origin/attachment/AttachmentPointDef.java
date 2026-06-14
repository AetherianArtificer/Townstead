package com.aetherianartificer.townstead.origin.attachment;

import java.util.List;

/**
 * A species attachment point: the anchor bone, a base offset, and the tags an
 * attachment can target to fit here ({@code ear}, {@code tail_root}, ...). Synced
 * with the manifest.
 */
public record AttachmentPointDef(String id, String bone, float[] offset, List<String> tags) {
    public AttachmentPointDef {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
