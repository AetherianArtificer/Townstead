package com.aetherianartificer.townstead.naming;

import org.jetbrains.annotations.Nullable;

/**
 * Supplies family names for one namespace, which is how another mod's surnames reach a naming
 * tradition without the schema knowing that mod exists.
 *
 * <p>MCA Capitals is the worked example: it owns a surname corpus and no given names, so it
 * registers here and nowhere else. Registered through
 * {@link NameLists#addFamilyProvider(String, FamilyNameProvider)} at setup, gated on its own mod.</p>
 */
@FunctionalInterface
public interface FamilyNameProvider {

    /** The family names this provider offers for a path, or null. Must not throw. */
    @Nullable FamilyNames get(String path);
}
