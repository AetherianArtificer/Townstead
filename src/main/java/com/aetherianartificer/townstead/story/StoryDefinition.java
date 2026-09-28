package com.aetherianartificer.townstead.story;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.story.goal.Goal;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.Map;

/**
 * One compiled story: every {@code .ink} file in {@code data/<ns>/story/<id>/} plus the optional
 * {@code story.json} beside them. The compiled Ink is kept as JSON and loaded fresh for each
 * conversation, so sessions never share runtime state.
 */
public record StoryDefinition(
        ResourceLocation id,
        String compiledJson,
        String hash,
        StoryAttach attach,
        Bind bind,
        int priority,
        String label,
        Map<String, Quest> quests,
        Map<String, Condition> conditions,
        Map<String, Action> actions,
        Map<String, com.google.gson.JsonObject> goals,
        List<Demeanor.Band> demeanor
) {
    /** Who a story's saved state belongs to, besides the player. */
    public enum Bind {
        /** Each attached villager tells their own copy. */
        VILLAGER,
        /** One copy per player, told by the first attached villager they talk to. */
        PLAYER
    }

    /**
     * A knot tagged {@code # quest:}. It starts the first time the story enters the knot. Its
     * optional stitches: {@code = done} plays once every goal is met, {@code = skipped} plays
     * instead when {@code # skip if:} already held at the start, and {@code = waiting} plays
     * while the quest is open.
     */
    public record Quest(String knot, String title, String about, List<Goal> goals, @Nullable Goal skipIf,
                        boolean hasDone, boolean hasSkipped, boolean hasWaiting, @Nullable String label,
                        List<com.aetherianartificer.townstead.story.reward.Reward> rewards) {
    }
}
