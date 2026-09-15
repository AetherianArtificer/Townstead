package com.aetherianartificer.townstead.naming;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Family names and cultures the server has told this client about, keyed by entity id.
 *
 * <p>Every surface that draws a name is on the client and the name itself lives on the server, so
 * this is what the screens read. An entity nobody has synced yet simply has no family name, which
 * renders as the villager's given name alone rather than as a gap.</p>
 */
public final class NameClientStore {

    private static final Map<Integer, String> FAMILY = new ConcurrentHashMap<>();
    private static final Map<Integer, String> CULTURE = new ConcurrentHashMap<>();

    private static final Map<Integer, NamingTradition.Order> ORDER = new ConcurrentHashMap<>();
    private static final Map<Integer, NamingTradition.FamilyType> RULE = new ConcurrentHashMap<>();
    private static final Map<Integer, String> TRADITION = new ConcurrentHashMap<>();

    private NameClientStore() {}

    public static void set(int entityId, String familyName, String culture,
                           NamingTradition.Order order, NamingTradition.FamilyType rule,
                           String tradition) {
        ORDER.put(entityId, order == null ? NamingTradition.Order.GIVEN_FIRST : order);
        RULE.put(entityId, rule == null ? NamingTradition.FamilyType.NONE : rule);
        put(FAMILY, entityId, familyName);
        put(CULTURE, entityId, culture);
        put(TRADITION, entityId, tradition);
    }

    /** The id of the naming tradition this entity is named by, or empty. */
    public static String tradition(int entityId) {
        String value = TRADITION.get(entityId);
        return value == null ? "" : value;
    }

    /**
     * Where this entity's family name comes from, which is what tells a screen whether editing one
     * reaches anybody else. {@code NONE} both for a tradition that gives no family names and for an
     * entity nobody has synced, so a screen treats an unknown villager as having nothing to edit.
     */
    public static NamingTradition.FamilyType rule(int entityId) {
        return RULE.getOrDefault(entityId, NamingTradition.FamilyType.NONE);
    }

    /** The family name for this entity, or empty when it has none or none has arrived. */
    public static String family(int entityId) {
        String value = FAMILY.get(entityId);
        return value == null ? "" : value;
    }

    /** The culture id for this entity, or empty. */
    public static String culture(int entityId) {
        String value = CULTURE.get(entityId);
        return value == null ? "" : value;
    }

    /** Composes names in the server-resolved order without repeating an existing surname. */
    public static String fullName(int entityId, String givenName) {
        return styled(entityId, givenName, NameStyle.FULL);
    }

    /** The same, in a named style, for a surface that wants less than the whole name. */
    public static String styled(int entityId, String givenName, NameStyle style) {
        return (style == null ? NameStyle.FULL : style).apply(givenName, family(entityId),
                ORDER.getOrDefault(entityId, NamingTradition.Order.GIVEN_FIRST));
    }

    public static void clear() {
        FAMILY.clear();
        CULTURE.clear();
        ORDER.clear();
        RULE.clear();
        TRADITION.clear();
    }

    private static void put(Map<Integer, String> into, int entityId, String value) {
        if (value == null || value.isEmpty()) {
            into.remove(entityId);
        } else {
            into.put(entityId, value);
        }
    }
}
