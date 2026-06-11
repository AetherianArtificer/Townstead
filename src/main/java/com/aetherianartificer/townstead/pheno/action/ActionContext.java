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

    public ActionContext(LivingEntity entity) {
        this(entity, null);
    }

    public ActionContext(LivingEntity entity, @Nullable LivingEntity other) {
        this.entity = entity;
        this.other = other;
    }

    public LivingEntity entity() {
        return entity;
    }

    /** The counterpart in a bi-entity trigger, or {@code null} for self-only actions. */
    @Nullable
    public LivingEntity other() {
        return other;
    }

    public Level level() {
        return entity.level();
    }
}
