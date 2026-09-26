package com.aetherianartificer.townstead.culture;

import com.aetherianartificer.townstead.politics.charter.CharterIdentityService;
import com.aetherianartificer.townstead.politics.definition.PoliticalDefinitions;
import com.aetherianartificer.townstead.politics.state.*;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Culture supplies a base name; government supplies grammar. Neither follows later demographics. */
public final class FactionNaming {
    private static volatile Map<ResourceLocation, SettlementNamePool> pools = Map.of();
    private FactionNaming() {}
    public static void replace(Map<ResourceLocation, SettlementNamePool> values) { pools = Map.copyOf(values); }
    public static List<String> parsePatterns(JsonObject json) {
        if (!json.has("faction_name_patterns")) return List.of("{name}");
        var result = new ArrayList<String>();
        for (var value : json.getAsJsonArray("faction_name_patterns")) {
            String pattern = value.getAsString().strip();
            if (!pattern.contains("{name}") || pattern.replace("{name}", "").matches(".*[{}].*")
                    || CharterIdentityService.normalize(pattern.replace("{name}", "Test")) == null)
                throw new IllegalArgumentException("Invalid faction naming pattern: " + pattern);
            result.add(pattern);
        }
        if (result.isEmpty() || result.size() > 64) throw new IllegalArgumentException("Expected 1–64 faction naming patterns");
        return List.copyOf(result);
    }
    public static List<String> patterns(ResourceLocation governmentKind) {
        var kind = governmentKind == null ? null : PoliticalDefinitions.snapshot().organizationKind(governmentKind);
        return kind == null || kind.presentation() == null ? List.of("{name}") : kind.presentation().factionNamePatterns();
    }
    public static List<String> suggestions(ResourceLocation cultureId) {
        var culture = Cultures.get(cultureId);
        var pool = culture == null || culture.factionNames() == null ? null : pools.get(culture.factionNames());
        if (pool == null) return List.of();
        var result = new LinkedHashSet<String>();
        for (int i = 0; i < 32 && result.size() < 12; i++) {
            String value = CharterIdentityService.normalize(pool.pick());
            if (value != null) result.add(value);
        }
        return List.copyOf(result);
    }
    public static Name generate(ResourceLocation culture, ResourceLocation kind, String fallback) {
        var names = suggestions(culture);
        String base = names.isEmpty() ? fallback : pick(names);
        String pattern = pick(patterns(kind));
        if (CharterIdentityService.normalize(pattern.replace("{name}", base)) == null) pattern = "{name}";
        return new Name(base, pattern, culture == null ? "" : culture.toString(), kind == null ? "" : kind.toString(), false);
    }
    public static Name review(ResourceLocation cultureId, ResourceLocation kind, String base, String pattern, String display) {
        String normalized = CharterIdentityService.normalize(display);
        if (normalized == null) return null;
        var culture = Cultures.get(cultureId);
        var pool = culture == null || culture.factionNames() == null ? null : pools.get(culture.factionNames());
        if (pool != null && pool.contains(base) && patterns(kind).contains(pattern)
                && pattern.replace("{name}", base).equals(normalized))
            return new Name(base, pattern, cultureId.toString(), kind == null ? "" : kind.toString(), false);
        return Name.custom(normalized);
    }
    private static String pick(List<String> list) { return list.get(ThreadLocalRandom.current().nextInt(list.size())); }
    /** Only called for a newly founded identity. Existing saves and government changes retain their name. */
    public static void initialize(PoliticalSavedData data, ResourceLocation polityId, Name name) {
        if (data.factionName(polityId) != null) return;
        var polity = data.polity(polityId);
        if (polity == null) return;
        data.putPolity(new PolityInstance(polity.id(), name.display(), polity.color(), polity.emblem(), polity.createdAt(),
                polity.provenance(), polity.status(), polity.settlements(), polity.governmentOrganization()));
        data.putFactionName(polityId, name);
    }
    public record Name(String base, String pattern, String culture, String government, boolean custom) {
        public String display() { return pattern.replace("{name}", base); }
        public static Name custom(String value) { return new Name(value, "{name}", "", "", true); }
        public CompoundTag save() {
            var tag = new CompoundTag(); tag.putString("base", base); tag.putString("pattern", pattern);
            tag.putString("culture", culture); tag.putString("government", government); tag.putBoolean("custom", custom); return tag;
        }
        public static Name load(CompoundTag tag) {
            return new Name(tag.getString("base"), tag.getString("pattern"), tag.getString("culture"), tag.getString("government"), tag.getBoolean("custom"));
        }
    }
}
