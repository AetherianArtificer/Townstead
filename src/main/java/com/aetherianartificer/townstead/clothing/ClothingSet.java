package com.aetherianartificer.townstead.clothing;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.ModGate;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A named list of clothing that belongs together: a culture's dress, a uniform, a season's
 * outfits. Sets are what policies and cultures name; entries are what one piece is.
 *
 * <p>A member is one of an inline entry, a {@code ref} to an entry in any loaded document, or a
 * {@code select} query over every loaded entry. Members carry their own {@code mods} gate and
 * drop out silently when unmet, so one set can list garments from several mods and load against
 * any subset of them. Membership is resolved after every document has loaded, in
 * {@link ClothingDefs}.</p>
 */
public record ClothingSet(ResourceLocation id,
                          Component displayName,
                          List<ResourceLocation> includes,
                          List<Member> members,
                          float weight) {

    public static final String SCHEMA = "townstead:clothing_set/v1";

    public ClothingSet {
        includes = includes == null ? List.of() : List.copyOf(includes);
        members = members == null ? List.of() : List.copyOf(members);
        if (weight <= 0f) weight = 1f;
    }

    /** One unresolved member. Exactly one of the three fields is set. */
    public record Member(@Nullable ClothingEntry inline,
                         @Nullable ResourceLocation ref,
                         @Nullable ClothingQuery select) {}

    public static @Nullable ClothingSet parse(ResourceLocation id, JsonObject json, Map<String, String> lang) {
        if (json == null) return null;
        Component name = json.has("display_name")
                ? DataPackLang.parseComponent(json.get("display_name"), id.toString(), lang)
                : Component.literal(id.getPath());

        List<ResourceLocation> includes = new ArrayList<>();
        JsonElement includeElement = json.get("include");
        if (includeElement != null) {
            for (String raw : ClothingQuery.strings(includeElement)) {
                ResourceLocation included = DataPackLang.parseId(raw);
                if (included != null) includes.add(included);
            }
        }

        List<Member> members = new ArrayList<>();
        JsonElement membersElement = json.get("members");
        if (membersElement != null && membersElement.isJsonArray()) {
            JsonArray array = membersElement.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                JsonElement element = array.get(i);
                if (element == null || !element.isJsonObject()) continue;
                JsonObject member = element.getAsJsonObject();
                if (member.has("mods") && !Boolean.TRUE.equals(ModGate.evaluate(member.get("mods")))) continue;
                Member parsed = parseMember(id, i, member);
                if (parsed != null) members.add(parsed);
            }
        }
        if (includes.isEmpty() && members.isEmpty()) return null;
        return new ClothingSet(id, name, includes, members, GsonHelper.getAsFloat(json, "weight", 1f));
    }

    private static @Nullable Member parseMember(ResourceLocation owner, int index, JsonObject json) {
        String ref = GsonHelper.getAsString(json, "ref", "").trim();
        if (!ref.isEmpty()) {
            ResourceLocation target = DataPackLang.parseId(ref);
            return target == null ? null : new Member(null, target, null);
        }
        if (json.has("select")) {
            return new Member(null, null, ClothingQuery.parse(json.get("select")));
        }
        ClothingEntry inline = ClothingEntry.parse(owner, index, json);
        return inline == null ? null : new Member(inline, null, null);
    }
}
