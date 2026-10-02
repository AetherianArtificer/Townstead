package com.aetherianartificer.townstead.compat.temperature;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ColdSweatTemperatureBridgeTest {
    /** Matches Cold Sweat's reflective effect API, including symmetric accumulation limits. */
    public static final class Effect {
        private final boolean active;
        private final double temperature;
        public Effect(boolean active, double temperature) { this.active = active; this.temperature = temperature; }
        public double maxEffect() { throw new AssertionError("Accumulation limits are not block temperatures"); }
        public double minEffect() { throw new AssertionError("Accumulation limits are not block temperatures"); }
        public boolean isValid(Level level, BlockPos pos, BlockState state) { return active; }
        public double getTemperature(Level level, LivingEntity entity, BlockState state, BlockPos pos, double distance) {
            return temperature;
        }
    }

    private float sample(List<Effect> effects, Object fallback) throws Exception {
        return ColdSweatTemperatureBridge.evaluateBlockEffects(effects, fallback,
                Effect.class.getMethod("isValid", Level.class, BlockPos.class, BlockState.class),
                Effect.class.getMethod("getTemperature", Level.class, LivingEntity.class, BlockState.class, BlockPos.class, double.class),
                null, null, null, () -> null);
    }

    @Test
    void coolingUsesItsActualNegativeEffectNotThePositiveAccumulationLimit() throws Exception {
        assertEquals(-10f, sample(List.of(new Effect(true, -0.4)), new Object()));
    }

    @Test
    void invalidStatesAndNonFiniteEffectsCannotBecomeHeatSources() throws Exception {
        assertEquals(0f, sample(List.of(new Effect(false, 1)), new Object()));
        assertEquals(-5f, sample(List.of(new Effect(true, Double.POSITIVE_INFINITY), new Effect(true, -0.2)), new Object()));
    }

    @Test
    void unknownBlocksAllowTagFallbackWhileInactiveKnownBlocksDoNot() throws Exception {
        Effect fallback = new Effect(true, 0);
        assertTrue(Float.isNaN(sample(List.of(fallback), fallback)));
        assertEquals(0f, sample(List.of(new Effect(false, 1)), fallback));
    }
}
