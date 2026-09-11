package com.aetherianartificer.townstead.naming;

import org.jetbrains.annotations.Nullable;

/**
 * Supplies name lists for one namespace, so another mod's names are referenced the same way
 * Townstead's own are.
 *
 * <p>A reference like {@code mcacapitals:elven} names no file. The namespace says who resolves it
 * and the path says what to resolve, which is how any computed registry works and means a mod joins
 * the naming system by registering a namespace rather than by changing the schema. A provider is
 * registered through {@link NameLists#addProvider(String, NameListProvider)} at setup and gates
 * itself on its own mod being present.</p>
 */
@FunctionalInterface
public interface NameListProvider {

    /**
     * The names this provider offers for a path, or null when it has none. Must not throw, and must
     * tolerate a path it does not recognise.
     */
    @Nullable NameList get(String path);
}
