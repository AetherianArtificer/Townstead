package com.aetherianartificer.townstead.naming;

import org.jetbrains.annotations.Nullable;

/**
 * Supplies given names for one namespace, so another mod's first names are referenced the same way
 * Townstead's own are.
 *
 * <p>A reference like {@code somemod:elven} names no file: the namespace says who resolves it and
 * the path says what to resolve, so a mod joins the naming system by registering a namespace rather
 * than by anyone changing the schema. Registered through
 * {@link NameLists#addGivenProvider(String, GivenNameProvider)} at setup, gated on its own mod.</p>
 */
@FunctionalInterface
public interface GivenNameProvider {

    /** The given names this provider offers for a path, or null. Must not throw. */
    @Nullable GivenNames get(String path);
}
