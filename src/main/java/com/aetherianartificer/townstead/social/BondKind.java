package com.aetherianartificer.townstead.social;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A kind of tie between two parties, defined in {@code data/<ns>/bond/}. Each named role says who
 * fills it (a person or a faction) and what it gives the other side. One role means a symmetric
 * bond, such as marriage; two roles, such as liege and vassal, each give something.
 *
 * <p>Version 1 files from {@code bond_kind/} still load as person-to-person bonds.</p>
 */
public record BondKind(ResourceLocation id, String displayLangKey, String displayLiteral,
                       Map<String, Role> roles, boolean uniquePerPair, Breaking breaking,
                       int noticeDays, @Nullable String source) {
    public static final String SCHEMA = "townstead:bond/v2";
    public static final String SCHEMA_V1 = "townstead:bond_kind/v1";
    public static final ResourceLocation LAND = id("townstead:land");

    public enum Party { PERSON, FACTION }

    public enum Breaking { CLEAN, GRIEVANCE }

    /**
     * One position in the bond. {@code max} is how many bonds of this kind a party in this role
     * may hold at once, 0 for no limit. {@code requires} names a bond the party must already hold.
     */
    public record Role(String name, Party party, Set<ResourceLocation> gives, int max,
                       @Nullable ResourceLocation requires, boolean membersOnly) {
        public Role {
            gives = Set.copyOf(new LinkedHashSet<>(gives));
            max = Math.max(0, max);
        }

        public boolean unlimited() {
            return max == 0;
        }
    }

    public BondKind {
        displayLangKey = displayLangKey == null ? "" : displayLangKey;
        displayLiteral = displayLiteral == null ? "" : displayLiteral;
        roles = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(roles));
        breaking = breaking == null ? Breaking.CLEAN : breaking;
        noticeDays = Math.max(0, noticeDays);
    }

    /** What an unregistered id behaves like: an unlimited, repeatable, symmetric tie between people. */
    public static BondKind fallback(ResourceLocation id) {
        return personal(id, id.getPath(), 0, false, true, null);
    }

    /** A person-to-person kind in the version 1 shape. */
    public static BondKind personal(ResourceLocation id, String literal, int maxActive, boolean uniquePerPair,
                                    boolean symmetric, @Nullable String source) {
        Map<String, Role> roles = new LinkedHashMap<>();
        if (symmetric) {
            roles.put("party", new Role("party", Party.PERSON, Set.of(), maxActive, null, false));
        } else {
            roles.put("first", new Role("first", Party.PERSON, Set.of(), maxActive, null, false));
            roles.put("second", new Role("second", Party.PERSON, Set.of(), maxActive, null, false));
        }
        return new BondKind(id, "", literal, roles, uniquePerPair, Breaking.CLEAN, 0, source);
    }

    /** How many a person may hold at once in this kind's first role, 0 for no limit. */
    public int maxActive() {
        return roles.values().iterator().next().max();
    }

    public boolean unlimited() {
        return maxActive() == 0;
    }

    public boolean symmetric() {
        return roles.size() == 1;
    }

    public @Nullable Role role(String name) {
        return roles.get(name);
    }

    /** The first role a party of this type fills, or null. */
    public @Nullable Role roleFor(Party party) {
        for (Role role : roles.values()) if (role.party() == party) return role;
        return null;
    }

    /** True for a kind with exactly one faction role and one person role, the shape of an office. */
    public boolean officeShaped() {
        return roles.size() == 2 && roleFor(Party.FACTION) != null && roleFor(Party.PERSON) != null;
    }

    public boolean personal() {
        return roles.values().stream().allMatch(role -> role.party() == Party.PERSON);
    }

    public Component displayName() {
        return displayLangKey.isEmpty() ? Component.literal(displayLiteral)
                : Component.translatableWithFallback(displayLangKey, displayLiteral);
    }

    /** "Vassal", from {@code bond.<ns>.<kind>.<role>}. */
    public Component roleName(String role) {
        return Component.translatableWithFallback(roleKey(role), title(role));
    }

    /** "Vassals". */
    public Component rolePlural(String role) {
        return Component.translatableWithFallback(roleKey(role) + ".plural", title(role));
    }

    /** "Vassal of the Kingdom of Blocks". */
    public Component roleOf(String role, Object other) {
        return Component.translatableWithFallback(roleKey(role) + ".of", title(role) + " of %s", other);
    }

    private String roleKey(String role) {
        return "bond." + id.getNamespace() + "." + id.getPath().replace('/', '.') + "." + role;
    }

    public static BondKind parse(ResourceLocation id, JsonObject json, Map<String, String> lang) {
        String schema = json.has("schema") ? GsonHelper.getAsString(json, "schema") : "";
        String defaultKey = "bond." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        String langKey = defaultKey;
        String literal = id.getPath();
        if (json.has("display")) {
            JsonObject display = GsonHelper.getAsJsonObject(json, "display");
            if (display.has("translate")) {
                langKey = GsonHelper.getAsString(display, "translate");
            } else if (display.has("text")) {
                literal = GsonHelper.getAsString(display, "text");
                langKey = "";
            }
        }
        if (!langKey.isEmpty()) {
            String indexed = lang.get(langKey);
            literal = indexed != null ? indexed : DataPackLang.resolveFallback(langKey, "en_us", literal);
        }
        String source = json.has("source") ? GsonHelper.getAsString(json, "source") : null;
        if (schema.equals(SCHEMA_V1) || !json.has("roles")) {
            TownsteadSchema.validate(json, SCHEMA_V1);
            BondKind v1 = personal(id, literal, GsonHelper.getAsInt(json, "max_active", 0),
                    GsonHelper.getAsBoolean(json, "unique_per_pair", false),
                    GsonHelper.getAsBoolean(json, "symmetric", true), source);
            return new BondKind(id, langKey, literal, v1.roles(), v1.uniquePerPair(), Breaking.CLEAN, 0, source);
        }
        TownsteadSchema.validate(json, SCHEMA);
        JsonObject rolesJson = GsonHelper.getAsJsonObject(json, "roles");
        if (rolesJson.size() < 1 || rolesJson.size() > 2) {
            throw new IllegalArgumentException("A bond has one role (symmetric) or two roles");
        }
        Map<String, Role> roles = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : rolesJson.entrySet()) {
            String name = entry.getKey();
            if (!name.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Role name '" + name + "' must be lowercase letters, digits or _");
            if (!entry.getValue().isJsonObject()) throw new IllegalArgumentException("Role '" + name + "' must be an object");
            roles.put(name, parseRole(name, entry.getValue().getAsJsonObject()));
        }
        Breaking breaking;
        try {
            breaking = Breaking.valueOf(GsonHelper.getAsString(json, "breaking", "clean").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("'breaking' must be clean or grievance");
        }
        int notice = GsonHelper.getAsInt(json, "notice_days", 0);
        if (notice < 0) throw new IllegalArgumentException("'notice_days' cannot be negative");
        return new BondKind(id, langKey, literal, roles, GsonHelper.getAsBoolean(json, "unique_per_pair", true),
                breaking, notice, source);
    }

    private static Role parseRole(String name, JsonObject json) {
        Party party;
        try {
            party = Party.valueOf(GsonHelper.getAsString(json, "party", "person").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Role '" + name + "' party must be person or faction");
        }
        Set<ResourceLocation> gives = new LinkedHashSet<>();
        if (json.has("gives")) {
            for (JsonElement element : GsonHelper.getAsJsonArray(json, "gives")) {
                ResourceLocation value = DataPackLang.parseId(element.getAsString());
                if (value == null) throw new IllegalArgumentException("Role '" + name + "' gives an invalid id");
                if (!gives.add(value)) throw new IllegalArgumentException("Role '" + name + "' gives " + value + " twice");
            }
        }
        int max = GsonHelper.getAsInt(json, "max", 0);
        if (max < 0) throw new IllegalArgumentException("Role '" + name + "' max cannot be negative");
        // The land-carrying bond is a faction's one parent.
        if (gives.contains(LAND) && (max != 1 || party != Party.FACTION)) {
            throw new IllegalArgumentException("Role '" + name + "' gives land, so it must be a faction role with \"max\": 1");
        }
        ResourceLocation requires = null;
        if (json.has("requires")) {
            requires = DataPackLang.parseId(GsonHelper.getAsString(json, "requires"));
            if (requires == null) throw new IllegalArgumentException("Role '" + name + "' requires an invalid id");
        }
        String visibility = GsonHelper.getAsString(json, "visibility", "public");
        if (!visibility.equals("public") && !visibility.equals("members")) {
            throw new IllegalArgumentException("Role '" + name + "' visibility must be public or members");
        }
        return new Role(name, party, gives, max, requires, visibility.equals("members"));
    }

    private static String title(String value) {
        String spaced = value.replace('_', ' ');
        return spaced.isEmpty() ? spaced : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static ResourceLocation id(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) throw new IllegalStateException(value);
        return id;
    }
}
