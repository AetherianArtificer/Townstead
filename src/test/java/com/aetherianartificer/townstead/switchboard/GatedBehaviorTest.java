package com.aetherianartificer.townstead.switchboard;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class GatedBehaviorTest {
    private static final class Probe implements BehaviorControl<LivingEntity> {
        Behavior.Status status = Behavior.Status.STOPPED;
        int starts;
        int ticks;
        int stops;

        @Override public Behavior.Status getStatus() { return status; }
        @Override public boolean tryStart(ServerLevel level, LivingEntity e, long t) {
            starts++;
            status = Behavior.Status.RUNNING;
            return true;
        }
        @Override public void tickOrStop(ServerLevel level, LivingEntity e, long t) { ticks++; }
        @Override public void doStop(ServerLevel level, LivingEntity e, long t) {
            stops++;
            status = Behavior.Status.STOPPED;
        }
        @Override public String debugString() { return "probe"; }
    }

    @Test void offNeverStartsTheTask() {
        Probe probe = new Probe();
        GatedBehavior<LivingEntity> gated = new GatedBehavior<>(() -> false, probe);
        assertFalse(gated.tryStart(null, null, 0));
        assertEquals(0, probe.starts);
    }

    @Test void switchingOffStopsARunningTask() {
        Probe probe = new Probe();
        AtomicBoolean on = new AtomicBoolean(true);
        GatedBehavior<LivingEntity> gated = new GatedBehavior<>(on::get, probe);
        assertTrue(gated.tryStart(null, null, 0));
        gated.tickOrStop(null, null, 1);
        assertEquals(1, probe.ticks);
        on.set(false);
        gated.tickOrStop(null, null, 2);
        assertEquals(1, probe.ticks);
        assertEquals(1, probe.stops);
        assertEquals(Behavior.Status.STOPPED, gated.getStatus());
    }
}
