package com.aetherianartificer.townstead.dialogue.conversation.generative;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/** How often a culture's marked phrasing appears. Parts and frames name the voice they belong to. */
public record DialogueVoice(ResourceLocation id, Set<String> cultures, @Nullable ResourceLocation extendsVoice,
                            Map<String, Double> flavor, double defaultFlavor, Map<String, Double> stageFlavor,
                            List<Flatten> flatten, Children children) {
    public enum Children { SIMPLE, ALL, NONE }
    public record Flatten(Set<String> context, double max) {}
    public static final String SCHEMA = "townstead:dialogue_voice/v1";

    /** Target share of lines with a marked part for this speaker, before the flavor balance. */
    public double target(String register, String stage, String stageId, Set<String> facts) {
        double share = stageFlavor.containsKey(stageId) ? stageFlavor.get(stageId)
                : stageFlavor.containsKey(stage) ? stageFlavor.get(stage)
                : flavor.getOrDefault(register, defaultFlavor);
        for (Flatten rule : flatten) {
            if (rule.context().stream().anyMatch(facts::contains)) share = Math.min(share, rule.max());
        }
        return share;
    }

    static DialogueVoice parse(ResourceLocation id, JsonObject json) {
        Json.only(json, "schema", "cultures", "extends", "flavor", "default_flavor", "stage_flavor", "flatten", "children", "mods");
        List<Flatten> flatten = new ArrayList<>();
        if (json.has("flatten")) for (var e : GsonHelper.getAsJsonArray(json, "flatten")) {
            JsonObject rule = e.getAsJsonObject();
            Json.only(rule, "context", "max");
            flatten.add(new Flatten(Json.stringSet(rule, "context"), Json.number(rule, "max", 0, 0, 1)));
        }
        ResourceLocation parent = json.has("extends") ? Json.requiredId(json, "extends")
                : id.equals(GenerativeDialogue.COMMON_VOICE) ? null : GenerativeDialogue.COMMON_VOICE;
        return new DialogueVoice(id, Set.copyOf(Json.strings(json, "cultures")), parent, Json.numbers(json, "flavor", 0, 1),
                Json.number(json, "default_flavor", 0.2, 0, 1), Json.numbers(json, "stage_flavor", 0, 1), List.copyOf(flatten),
                ConversationMove.enumValue(Children.class, GsonHelper.getAsString(json, "children", "simple"), "children"));
    }
}
