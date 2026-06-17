package com.aetherianartificer.townstead.pheno.action;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The actor an {@link Action} runs on. {@code entity()} is the primary target;
 * {@code other()} is an optional counterpart for bi-entity triggers (e.g. the attacker
 * when a {@code when_hurt} action runs on the victim, or the victim when a
 * {@code when_attack} action runs on the attacker). Uniform for villagers and players.
 */
public final class ActionContext {

    private final LivingEntity entity;
    private final LivingEntity other;
    private final LivingEntity origin;

    public ActionContext(LivingEntity entity) {
        this(entity, null, entity);
    }

    public ActionContext(LivingEntity entity, @Nullable LivingEntity other) {
        this(entity, other, entity);
    }

    public ActionContext(LivingEntity entity, @Nullable LivingEntity other, LivingEntity origin) {
        this.entity = entity;
        this.other = other;
        this.origin = origin;
    }

    public LivingEntity entity() {
        return entity;
    }

    /** The counterpart in a bi-entity trigger, or {@code null} for self-only actions. */
    @Nullable
    public LivingEntity other() {
        return other;
    }

    /** The power-bearer, preserved as the focus shifts under {@code on} (the {@code origin} role). */
    public LivingEntity origin() {
        return origin;
    }

    public Level level() {
        return entity.level();
    }
}
