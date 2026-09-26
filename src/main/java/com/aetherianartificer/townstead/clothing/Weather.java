package com.aetherianartificer.townstead.clothing;

import com.aetherianartificer.townstead.compat.temperature.RoomHeatBackend;
import com.aetherianartificer.townstead.temperature.RoomHeat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.OptionalDouble;

/**
 * What the day is like outside, and whether a villager is sheltered from it. Dress is decided
 * from the outdoor reading, not from wherever the villager happens to stand: a villager in a
 * warm kitchen in midwinter is not cold, but the walk home will be.
 *
 * <p>The cuts are the ambient tiers the temperature need already uses: cold below 10 degrees,
 * hot from 26. Shelter is an enclosed room the heat model knows, warm enough to take a coat off
 * in.</p>
 */
public final class Weather {

    /** Outdoor ambient at or below this wants warm outerwear. */
    public static final float COLD_CELSIUS = 10f;
    /** Outdoor ambient at or above this sheds warm pieces and wants cool headwear. */
    public static final float HOT_CELSIUS = 26f;
    /** A room at or above this is one to take a coat off in. */
    public static final float SHELTER_CELSIUS = 14f;

    public enum Kind { COLD, MILD, HOT }

    private Weather() {}

    /** The outdoor reading at a position, with rooms ignored. */
    public static float outdoorCelsius(ServerLevel level, BlockPos pos) {
        float value = RoomHeatBackend.outdoor(level, pos);
        return Float.isFinite(value) ? value : 20f;
    }

    public static Kind kind(float outdoorCelsius) {
        if (outdoorCelsius <= COLD_CELSIUS) return Kind.COLD;
        if (outdoorCelsius >= HOT_CELSIUS) return Kind.HOT;
        return Kind.MILD;
    }

    public static Kind outdoorKind(ServerLevel level, BlockPos pos) {
        return kind(outdoorCelsius(level, pos));
    }

    /** Inside a modelled room that is warm enough to shed a coat in. */
    public static boolean sheltered(ServerLevel level, BlockPos pos) {
        OptionalDouble room = RoomHeat.at(level, pos);
        return room.isPresent() && room.getAsDouble() >= SHELTER_CELSIUS;
    }
}
