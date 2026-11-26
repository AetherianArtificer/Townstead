package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.root.attachment.HitShapes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

/** What a person wears is part of them: shots and aim land on it as they do on the body. Parity: SRG names on Forge. */
@Mixin(ProjectileUtil.class)
public abstract class ProjectileAttachmentHitMixin {

    // Someone looking or shooting from inside their own attachments sees past them: the ray from your eyes down to
    // the ground passes through your own sac, and stopping there would show (or hit) yourself.
    //? if neoforge {
    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;",
            at = @At("HEAD"), argsOnly = true)
    //?} else {
    /*@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "m_150175_", at = @At("HEAD"), argsOnly = true, remap = false)
    *///?}
    private static Predicate<Entity> townstead$notOwnPartsShot(Predicate<Entity> filter, Level level, Entity projectile,
                                                              Vec3 start, Vec3 end, AABB area, Predicate<Entity> given, float inflate) {
        return com.aetherianartificer.townstead.root.attachment.HitShapes.notOwnParts(projectile, filter);
    }

    //? if neoforge {
    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",
            at = @At("HEAD"), argsOnly = true)
    //?} else {
    /*@org.spongepowered.asm.mixin.injection.ModifyVariable(method = "m_37287_", at = @At("HEAD"), argsOnly = true, remap = false)
    *///?}
    private static Predicate<Entity> townstead$notOwnPartsAim(Predicate<Entity> filter, Entity aiming, Vec3 start, Vec3 end,
                                                             AABB area, Predicate<Entity> given, double reachSqr) {
        return com.aetherianartificer.townstead.root.attachment.HitShapes.notOwnParts(aiming, filter);
    }

    //? if neoforge {
    @Inject(method = "getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;",
            at = @At("RETURN"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_150175_", at = @At("RETURN"), cancellable = true, remap = false)
    *///?}
    private static void townstead$attachments(Level level, Entity projectile, Vec3 start, Vec3 end, AABB area,
                                              Predicate<Entity> filter, float inflate,
                                              CallbackInfoReturnable<EntityHitResult> cir) {
        EntityHitResult found = cir.getReturnValue();
        EntityHitResult result = HitShapes.intercept(level, projectile, start, end, filter, inflate, found);
        if (result != found) cir.setReturnValue(result);
    }

    //? if neoforge {
    @Inject(method = "getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;",
            at = @At("RETURN"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_37287_", at = @At("RETURN"), cancellable = true, remap = false)
    *///?}
    private static void townstead$aimAtAttachments(Entity aiming, Vec3 start, Vec3 end, AABB area, Predicate<Entity> filter,
                                                   double reachSqr, CallbackInfoReturnable<EntityHitResult> cir) {
        EntityHitResult found = cir.getReturnValue();
        EntityHitResult result = HitShapes.aim(aiming, start, end, filter, reachSqr, found);
        if (result != found) cir.setReturnValue(result);
    }
}
