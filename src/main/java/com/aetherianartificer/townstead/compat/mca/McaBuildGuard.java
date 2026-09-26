package com.aetherianartificer.townstead.compat.mca;

//? if neoforge {
import net.neoforged.fml.ModList;
//?}

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Enforces the two MCA version identities supported by this Townstead build. */
public final class McaBuildGuard {
    private static final String TARGET_RESOURCE = "/META-INF/townstead-mca.properties";

    private McaBuildGuard() {
    }

    public static void verify() {
        // The legacy Forge line has its own independently versioned MCA dependency.
        //? if neoforge {
        Target target = loadTarget();
        var modFile = ModList.get().getModFileById("mca");
        if (modFile == null) {
            throw incompatible("MCA is not installed", target);
        }

        String actualVersion = modFile.versionString();
        if (accepts(actualVersion, target.targetVersion(), target.developmentVersion())) {
            return;
        }
        throw incompatible("MCA reports version " + actualVersion, target);
        //?}
    }

    static boolean accepts(String actualVersion, String targetVersion, String developmentVersion) {
        return targetVersion.equals(actualVersion) || developmentVersion.equals(actualVersion);
    }

    private static Target loadTarget() {
        Properties properties = new Properties();
        try (InputStream stream = McaBuildGuard.class.getResourceAsStream(TARGET_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Townstead is missing " + TARGET_RESOURCE);
            }
            properties.load(stream);
        } catch (IOException error) {
            throw new IllegalStateException("Townstead could not read its MCA build pin", error);
        }
        return new Target(
                required(properties, "targetVersion"),
                required(properties, "developmentVersion"));
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Townstead's MCA build pin is missing " + key);
        }
        return value.trim();
    }

    private static IllegalStateException incompatible(String detail, Target target) {
        return new IllegalStateException(detail + ". This Townstead build supports only MCA "
                + target.developmentVersion() + " or " + target.targetVersion() + ".");
    }

    private record Target(String targetVersion, String developmentVersion) {
    }
}
