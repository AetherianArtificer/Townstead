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

    private NameClientStore() {}

    public static void set(int entityId, String familyName, String culture, NamingTradition.Order order) {
        ORDER.put(entityId, order == null ? NamingTradition.Order.GIVEN_FIRST : order);
        put(FAMILY, entityId, familyName);
        put(CULTURE, entityId, culture);
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
        return NameParts.format(givenName, family(entityId),
                ORDER.getOrDefault(entityId, NamingTradition.Order.GIVEN_FIRST));
    }

    public static void clear() {
        FAMILY.clear();
        CULTURE.clear();
        ORDER.clear();
    }

    private static void put(Map<Integer, String> into, int entityId, String value) {
        if (value == null || value.isEmpty()) {
            into.remove(entityId);
        } else {
            into.put(entityId, value);
        }
    }
}
