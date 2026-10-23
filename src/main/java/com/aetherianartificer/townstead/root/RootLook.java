package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.root.outfit.RootOutfits;

import java.util.List;

/**
 * How a root's body looks beyond its rig, synced with the catalog: the outfits it wears by
 * circumstance and the skin patterns that fit a non-MCA body (from {@code body_clothing}), so the
 * client draws only a skin authored for that body.
 */
public record RootLook(String rootId, List<RootOutfits.Outfit> outfits, List<String> skins) {

    /** Whether a worn MCA skin id fits this body (exact id, or a pattern ending in {@code *}). */
    public boolean fits(String skinId) {
        if (skinId == null || skinId.isEmpty()) return false;
        for (String skin : skins) {
            if (skin.endsWith("*") ? skinId.startsWith(skin.substring(0, skin.length() - 1)) : skin.equals(skinId)) {
                return true;
            }
        }
        return false;
    }
}
