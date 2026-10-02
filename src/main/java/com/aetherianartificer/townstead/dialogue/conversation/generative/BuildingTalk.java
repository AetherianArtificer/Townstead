package com.aetherianartificer.townstead.dialogue.conversation.generative;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * The {@code talk} block of an {@code extended_building} file: how conversation names the building,
 * which talk tags it provides, and which interests it pulls.
 */
public record BuildingTalk(String buildingType, @Nullable String nameKey, Set<String> tags, Map<String, Double> interests) {
    static BuildingTalk parse(String buildingType, JsonObject talk) {
        Json.only(talk, "name", "tags", "interests");
        String name = null;
        if (talk.has("name")) {
            var raw = talk.get("name");
            name = raw.isJsonObject() ? GsonHelper.getAsString(raw.getAsJsonObject(), "translate") : raw.getAsString();
        }
        return new BuildingTalk(buildingType, name, Json.stringSet(talk, "tags"), Json.numbers(talk, "interests", 0, 1));
    }
}
