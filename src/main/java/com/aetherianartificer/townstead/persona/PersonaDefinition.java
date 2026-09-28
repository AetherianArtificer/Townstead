package com.aetherianartificer.townstead.persona;

import com.aetherianartificer.townstead.story.goal.Goal;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A Persona, from {@code data/<ns>/persona/<id>/persona.json} plus the Ink files beside it. The
 * Ink is compiled as a story attached to this Persona's villagers only.
 *
 * @param arrives any one of these goals, read on the player where they stand, brings the Persona
 * @param arrival how they appear: walking in from the road, or already at the village centre
 * @param downed  whether they are downed instead of dying (the world's permadeath setting can still end that)
 * @param rolls   things rolled once per world, such as a hometown; each can pin the profession and set Ink variables
 * @param gifts   how they take particular gifts; any other item is an ordinary MCA gift
 * @param schedule a week plan or shift template id they start with, or null for the usual
 * @param personalities personality refs they may roll, by weight; empty for the usual roll
 */
public record PersonaDefinition(
        ResourceLocation id,
        ResourceLocation story,
        String name,
        List<Goal> arrives,
        Arrival arrival,
        @Nullable ResourceLocation profession,
        @Nullable ResourceLocation root,
        @Nullable String gender,
        boolean downed,
        List<PersonaRoll> rolls,
        List<PersonaGift> gifts,
        @Nullable ResourceLocation schedule,
        java.util.Map<String, Integer> personalities
) {
    public enum Arrival { WALK_IN, APPEAR }
}
