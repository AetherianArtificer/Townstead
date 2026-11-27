package com.aetherianartificer.townstead.switchboard;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;

import java.util.function.BooleanSupplier;

/**
 * An AI task that only runs while its system is on. A running task that loses its switch stops at
 * its next tick instead of finishing.
 */
public final class GatedBehavior<E extends LivingEntity> implements BehaviorControl<E> {
    private final BehaviorControl<? super E> delegate;
    private final BooleanSupplier enabled;
    private final java.util.function.Predicate<E> admits;

    public GatedBehavior(BooleanSupplier enabled, BehaviorControl<? super E> delegate) {
        this(enabled, entity -> true, delegate);
    }

    private GatedBehavior(BooleanSupplier enabled, java.util.function.Predicate<E> admits, BehaviorControl<? super E> delegate) {
        this.enabled = enabled;
        this.admits = admits;
        this.delegate = delegate;
    }

    public static <E extends LivingEntity> GatedBehavior<E> of(String system, BehaviorControl<? super E> delegate) {
        return new GatedBehavior<>(Systems.gate(system), delegate);
    }

    /** As {@link #of}, for work a villager gives way on when something outranks it. */
    public static <E extends net.conczin.mca.entity.VillagerEntityMCA> GatedBehavior<E> work(String system, BehaviorControl<? super E> delegate) {
        return new GatedBehavior<>(Systems.gate(system), villager -> !com.aetherianartificer.townstead.villager.VillagerControl
                .outranked(villager, com.aetherianartificer.townstead.villager.VillagerControl.Owner.WORK), delegate);
    }

    @Override
    public Behavior.Status getStatus() {
        return delegate.getStatus();
    }

    @Override
    public boolean tryStart(ServerLevel level, E entity, long gameTime) {
        return enabled.getAsBoolean() && admits.test(entity) && delegate.tryStart(level, entity, gameTime);
    }

    @Override
    public void tickOrStop(ServerLevel level, E entity, long gameTime) {
        if (!enabled.getAsBoolean() || !admits.test(entity)) {
            delegate.doStop(level, entity, gameTime);
            return;
        }
        delegate.tickOrStop(level, entity, gameTime);
    }

    @Override
    public void doStop(ServerLevel level, E entity, long gameTime) {
        delegate.doStop(level, entity, gameTime);
    }

    @Override
    public String debugString() {
        return delegate.debugString();
    }
}
