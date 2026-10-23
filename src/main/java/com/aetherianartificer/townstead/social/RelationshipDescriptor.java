package com.aetherianartificer.townstead.social;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.pheno.lang.normalize.PhenoNormalizer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.*;

/** An overlapping, data-authored interpretation of qualities and bonds. */
public record RelationshipDescriptor(ResourceLocation id, String displayLangKey, String displayLiteral,
                                     int priority, Set<String> tags, Condition when) {
    public static final String SCHEMA = "townstead:relationship_descriptor/v1";

    public boolean matches(ConditionContext context) { return when.test(context); }

    public static RelationshipDescriptor parse(ResourceLocation id, JsonObject json, Map<String, String> lang) {
        only(json, "schema", "display", "priority", "tags", "when");
        TownsteadSchema.validateRequired(json, SCHEMA);
        String langKey = "relationship_descriptor." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        String literal = id.getPath().replace('_', ' ');
        if (json.has("display")) {
            JsonObject display = GsonHelper.getAsJsonObject(json, "display"); only(display, "translate", "text");
            if (display.has("translate")) langKey = GsonHelper.getAsString(display, "translate");
            else literal = GsonHelper.getAsString(display, "text");
        }
        literal = lang.getOrDefault(langKey, DataPackLang.resolveFallback(langKey, "en_us", literal));
        Set<String> tags = new LinkedHashSet<>();
        if (json.has("tags")) for (JsonElement element : GsonHelper.getAsJsonArray(json, "tags")) {
            String tag = element.getAsString();
            if (tag.isBlank() || tag.chars().anyMatch(Character::isWhitespace)) throw new IllegalArgumentException("tags: invalid tag " + tag);
            tags.add(tag);
        }
        Condition condition = Conditions.parse(PhenoNormalizer.normalizeCondition(GsonHelper.getAsJsonObject(json, "when")));
        if (condition == null) throw new IllegalArgumentException("when: invalid Pheno condition");
        return new RelationshipDescriptor(id, langKey, literal, GsonHelper.getAsInt(json, "priority", 0), Set.copyOf(tags), condition);
    }

    private static void only(JsonObject json, String... fields) {
        Set<String> allowed = Set.of(fields);
        for (String field : json.keySet()) if (!allowed.contains(field)) throw new IllegalArgumentException(field + ": unknown field");
    }
}
