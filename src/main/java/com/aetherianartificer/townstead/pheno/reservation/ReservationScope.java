package com.aetherianartificer.townstead.pheno.reservation;

import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.UUID;

/**
 * One execution-owned reservation lifetime. The root execution holds one reference; delayed
 * continuations retain another. Targets are released when the last reference closes or when an
 * explicit {@code pheno:release} closes the scope early.
 */
public final class ReservationScope {

    private final UUID id = UUID.randomUUID();
    private int references = 1;
    private boolean released;

    public UUID id() { return id; }

    public boolean reserve(LivingEntity entity) {
        return !released && Reservations.reserve(this, entity);
    }

    public List<LivingEntity> targets(LivingEntity focus) {
        return Reservations.targets(this, focus);
    }

    public boolean retain() {
        if (released) return false;
        references++;
        return true;
    }

    public void closeReference() {
        if (released) return;
        references--;
        if (references <= 0) release();
    }

    public void release() {
        if (released) return;
        released = true;
        references = 0;
        Reservations.release(this);
    }

    public boolean released() { return released; }
}
