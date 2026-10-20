package com.aetherianartificer.townstead.climate;

import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * How a biome with no climate tag gets a zone. Rules run step by step: season-mod tags, then
 * conventional tags, then words in the biome id, then the biome's temperature and downfall. Inside
 * a step, rules run in file order and the first match wins.
 */
public record ZoneDerivation(List<Rule> rules, @Nullable ResourceLocation fallback) {
    public static final String SCHEMA = "townstead:climate_derivation/v1";
    /** A rule zone of "none" leaves the biome without a zone; its dimension decides instead. */
    public static final String UNZONED = "none";

    public enum Step { PROVIDER, CONVENTIONAL, KEYWORD, CLIMATE }

    public static final ZoneDerivation EMPTY = new ZoneDerivation(List.of(), null);

    /** What a biome is, without Minecraft types, so the rules can be tested. */
    public record BiomeFacts(ResourceLocation id, Set<ResourceLocation> tags, float temperature, float downfall) {}

    /** The zone a biome resolved to (null when unzoned), the step that chose it, and why. */
    public record Resolution(@Nullable ResourceLocation zone, String step, String detail) {}

    public record Rule(Step step, List<ResourceLocation> anyTags, List<ResourceLocation> allTags,
                       @Nullable String keyword, float minTemperature, float maxTemperature,
                       float minDownfall, float maxDownfall, @Nullable ResourceLocation zone) {
        boolean matches(BiomeFacts facts) {
            if (!anyTags.isEmpty() && anyTags.stream().noneMatch(facts.tags()::contains)) return false;
            if (!allTags.stream().allMatch(facts.tags()::contains)) return false;
            if (keyword != null && !facts.id().getPath().toLowerCase(Locale.ROOT).contains(keyword)) return false;
            return facts.temperature() >= minTemperature && facts.temperature() < maxTemperature
                    && facts.downfall() >= minDownfall && facts.downfall() < maxDownfall;
        }

        String describe() {
            if (keyword != null) return "id contains '" + keyword + "'";
            List<String> parts = new ArrayList<>();
            if (!anyTags.isEmpty()) parts.add("any of #" + String.join(", #", anyTags.stream().map(ResourceLocation::toString).toList()));
            if (!allTags.isEmpty()) parts.add("all of #" + String.join(", #", allTags.stream().map(ResourceLocation::toString).toList()));
            if (minTemperature > -Float.MAX_VALUE) parts.add("temperature >= " + minTemperature);
            if (maxTemperature < Float.MAX_VALUE) parts.add("temperature < " + maxTemperature);
            if (minDownfall > -Float.MAX_VALUE) parts.add("downfall >= " + minDownfall);
            if (maxDownfall < Float.MAX_VALUE) parts.add("downfall < " + maxDownfall);
            return parts.isEmpty() ? "always" : String.join("; ", parts);
        }
    }

    /**
     * Picks a zone: a {@code <ns>:climate/<zone>} tag on the biome wins, then the rules by step,
     * then the fallback. Rules naming a zone that is not loaded are skipped.
     */
    public Resolution resolve(BiomeFacts facts, Collection<ResourceLocation> zones) {
        for (ResourceLocation zone : zones.stream().sorted().toList()) {
            ResourceLocation tag = zoneTag(zone);
            if (tag != null && facts.tags().contains(tag)) return new Resolution(zone, "tag", "#" + tag);
        }
        for (Step step : Step.values()) {
            for (Rule rule : rules) {
                if (rule.step() != step || (rule.zone() != null && !zones.contains(rule.zone()))) continue;
                if (rule.matches(facts)) return new Resolution(rule.zone(), step.name().toLowerCase(Locale.ROOT), rule.describe());
            }
        }
        if (fallback != null && zones.contains(fallback)) return new Resolution(fallback, "fallback", "no rule matched");
        return new Resolution(null, "none", "no rule matched and no fallback zone");
    }

    /** The biome tag that places biomes in a zone: {@code examplepack:highland} uses {@code #examplepack:climate/highland}. */
    @Nullable
    public static ResourceLocation zoneTag(ResourceLocation zone) {
        return ResourceLocation.tryParse(zone.getNamespace() + ":climate/" + zone.getPath());
    }

    /** A pack's rules run before the ones after it; {@code replace} drops everything loaded so far. */
    public ZoneDerivation layeredUnder(ZoneDerivation pack, boolean replace) {
        List<Rule> merged = new ArrayList<>(pack.rules);
        if (!replace) merged.addAll(rules);
        return new ZoneDerivation(merged, pack.fallback != null ? pack.fallback : replace ? null : fallback);
    }

    static ZoneDerivation parse(JsonObject json) {
        TownsteadSchema.validate(json, SCHEMA);
        List<Rule> rules = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "rules", new JsonArray())) {
            JsonObject rule = GsonHelper.convertToJsonObject(element, "rule");
            String stepName = GsonHelper.getAsString(rule, "step");
            Step step;
            try {
                step = Step.valueOf(stepName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown step '" + stepName + "'");
            }
            String zone = GsonHelper.getAsString(rule, "zone");
            String keyword = rule.has("keyword") ? GsonHelper.getAsString(rule, "keyword").toLowerCase(Locale.ROOT) : null;
            rules.add(new Rule(step, tags(rule, "any_tags"), tags(rule, "all_tags"), keyword,
                    GsonHelper.getAsFloat(rule, "min_temperature", -Float.MAX_VALUE),
                    GsonHelper.getAsFloat(rule, "max_temperature", Float.MAX_VALUE),
                    GsonHelper.getAsFloat(rule, "min_downfall", -Float.MAX_VALUE),
                    GsonHelper.getAsFloat(rule, "max_downfall", Float.MAX_VALUE),
                    UNZONED.equals(zone) ? null : id(zone)));
        }
        String fallback = GsonHelper.getAsString(json, "fallback", "");
        return new ZoneDerivation(rules, fallback.isEmpty() ? null : id(fallback));
    }

    private static List<ResourceLocation> tags(JsonObject json, String key) {
        List<ResourceLocation> tags = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, key, new JsonArray())) {
            String raw = element.getAsString();
            tags.add(id(raw.startsWith("#") ? raw.substring(1) : raw));
        }
        return tags;
    }

    private static ResourceLocation id(String raw) {
        ResourceLocation id = ResourceLocation.tryParse(raw.contains(":") ? raw : "townstead:" + raw);
        if (id == null) throw new IllegalArgumentException("Invalid id '" + raw + "'");
        return id;
    }
}
