package com.aetherianartificer.townstead.pheno.combat;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A thrown thing that runs actions where it lands. It travels as a vanilla snowball carrying any item,
 * so clients need no new entity type or renderer. It exists only on the server, which is why it isn't
 * saved: a shot still in the air when its chunk unloads is simply gone.
 */
public final class ActionProjectile extends Snowball {

    private static final int MAX_AGE = 200;

    private final float gravity;
    private final float damage;
    private final float knockback;
    @Nullable private final Action onHit;
    @Nullable private final Action onBlock;
    @Nullable private final ParticleOptions trail;

    public ActionProjectile(Level level, LivingEntity owner, ItemStack look, float gravity, float damage,
                            float knockback, @Nullable Action onHit, @Nullable Action onBlock,
                            @Nullable ParticleOptions trail) {
        super(level, owner);
        setItem(look);
        this.gravity = gravity;
        this.damage = damage;
        this.knockback = knockback;
        this.onHit = onHit;
        this.onBlock = onBlock;
        this.trail = trail;
    }

    //? if neoforge {
    @Override
    protected double getDefaultGravity() {
        return gravity;
    }
    //?} else {
    /*@Override
    protected float getGravity() {
        return gravity;
    }
    *///?}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) return;
        if (tickCount > MAX_AGE) {
            discard();
            return;
        }
        if (trail != null) server.sendParticles(trail, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (level().isClientSide || !(getOwner() instanceof LivingEntity owner)) return;
        Entity struck = hit.getEntity();
        if (damage > 0f) struck.hurt(damageSources().thrown(this, owner), damage);
        if (struck instanceof LivingEntity target) {
            if (knockback > 0f) target.knockback(knockback, getX() - target.getX(), getZ() - target.getZ());
            if (onHit != null) onHit.run(new ActionContext(owner, target).withAt(hit.getLocation()));
        } else if (onHit != null) {
            onHit.run(new ActionContext(owner).withAt(hit.getLocation()));
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        if (level().isClientSide || !(getOwner() instanceof LivingEntity owner)) return;
        Action action = onBlock != null ? onBlock : onHit;
        if (action != null) action.run(new ActionContext(owner).withFocusBlock(hit.getBlockPos()).withAt(hit.getLocation()));
    }

}
