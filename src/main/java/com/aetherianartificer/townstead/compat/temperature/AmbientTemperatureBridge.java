package com.aetherianartificer.townstead.compat.temperature;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import com.aetherianartificer.townstead.temperature.ThermalProtection;

/**
 * One temperature mod's view of the world, always in degrees Celsius. Mirrors the thirst bridges:
 * the preferred backend supplies the ambient at a position, the built-in fallback supplies it
 * otherwise, and nothing is ever stacked on a mod's reading.
 *
 * <p>The block and item opinions are different: every installed backend is asked, whichever one
 * drives the climate, so a block or garment a temperature mod knows about (by its tags, its JSON,
 * or its registry) warms or cools villagers with no Townstead data at all. Packs extend the mod,
 * and Townstead follows.</p>
 */
public interface AmbientTemperatureBridge {

    /** Stable id used by the {@code preferredBackend} config value. */
    String id();

    boolean isActive();

    /** Ambient temperature in Celsius at the position, or {@code NaN} when the backend cannot answer. */
    float ambientCelsius(ServerLevel level, BlockPos pos);

    /**
     * The insulation the backend attributes to a worn item, in Celsius, or {@code NaN} when the
     * backend has no opinion.
     */
    default float itemInsulationCelsius(ItemStack stack) {
        return Float.NaN;
    }

    /** Null means no opinion, allowing another backend or Townstead's clothing tags. */
    default ThermalProtection itemProtection(ItemStack stack) {
        float offset = itemInsulationCelsius(stack);
        return Float.isFinite(offset) ? new ThermalProtection(offset, 0, 0, 0) : null;
    }

    /**
     * What the backend says a block does to the air around it, in Celsius: positive warms,
     * negative cools, zero means currently inactive, {@code NaN} means unlisted.
     * Context is required: effects can depend on state, dimension, or a block entity.
     */
    default float blockTemperatureCelsius(Level level, BlockPos pos, BlockState state) {
        return Float.NaN;
    }

    /**
     * Whether {@link #blockTemperatureCelsius} could ever be nonzero for this state, from the state
     * alone. Scans memoize it per state and skip whole chunk sections whose palette rules it out,
     * so any bridge that overrides {@code blockTemperatureCelsius} must override this too.
     */
    default boolean blockMayMatter(BlockState state) {
        return false;
    }

    /**
     * Warms or cools a player by {@code degrees} Celsius, positive to warm, the player-side twin
     * of a villager's {@code adjustBodyTemp}. {@code durationTicks} of zero is an instant nudge
     * that the backend lets decay; above zero it is an influence held for that long and then
     * dropped. Cold Sweat's own food handling makes the same split, choosing its {@code CORE}
     * trait for an instant change and an expiring {@code BASE} modifier for a lasting one.
     *
     * <p>Returns false when the backend cannot express it at all, which is distinct from a nudge
     * of zero.</p>
     */
    default boolean adjustPlayerBodyCelsius(Player player, float degrees, int durationTicks) {
        return false;
    }

    /**
     * Called once when a Townstead influence on this player runs out, so a backend holding its
     * own copy can clear it. Backends whose temperature decays on its own need nothing here.
     */
    default void onThermalInfluenceEnded(Player player) {}
}
