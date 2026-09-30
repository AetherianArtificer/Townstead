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
 * @param worldUnique  only one of them in the whole world, instead of one per village
 * @param outfit       MCA clothing they always wear, by gender ("female", "male", or "any"); empty for the usual
 * @param names        name pools rolled once per world, or null to take the usual name
 * @param states       entity states they arrive with, by amount
 * @param mainhand     an item they arrive holding, or null
 * @param movesOn      what makes them leave for another village ({@code order_dissolved}, {@code town_fallen})
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
        java.util.Map<String, Integer> personalities,
        boolean worldUnique,
        java.util.Map<String, String> outfit,
        @Nullable PersonaNames names,
        java.util.Map<ResourceLocation, Double> states,
        @Nullable ResourceLocation mainhand,
        java.util.Set<String> movesOn
) {
    public enum Arrival { WALK_IN, APPEAR }
}
