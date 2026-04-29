package com.aetherianartificer.townstead.compat.farmersdelight;

import com.aetherianartificer.townstead.compat.ModCompat;
import org.apache.maven.artifact.versioning.ArtifactVersion;
//? if neoforge {
import net.neoforged.fml.ModList;
//?} else if forge {
/*import net.minecraftforge.fml.ModList;
*///?}

public final class FarmersDelightCompat {
    public static final String MOD_ID = "farmersdelight";

    private static volatile Boolean atLeast13;

    private FarmersDelightCompat() {}

    public static boolean isLoaded() {
        return ModCompat.isLoaded(MOD_ID);
    }

    /**
     * FD 1.3 introduced breaking changes that require Townstead to branch behavior:
     * tomato vines split into `tomatoes` (ground) + `tomatoes_on_rope` (hanging),
     * Cutting Board lost all off-hand interaction (main-hand only), Comfort effect
     * was retired in favor of Nourishment, and several tags were removed.
     */
    public static boolean isAtLeast13() {
        Boolean v = atLeast13;
        if (v != null) return v;
        return atLeast13 = computeAtLeast13();
    }

    private static boolean computeAtLeast13() {
        if (!isLoaded()) return false;
        try {
            ArtifactVersion version = ModList.get().getModContainerById(MOD_ID)
                    .map(c -> c.getModInfo().getVersion())
                    .orElse(null);
            if (version == null) return false;
            int major = version.getMajorVersion();
            int minor = version.getMinorVersion();
            return major > 1 || (major == 1 && minor >= 3);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
