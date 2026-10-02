package com.aetherianartificer.townstead.root.rig;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import java.util.Locale;

/** Read-only report usable on either logical side; never teleports or edits the world. */
public final class BodySizeDiagnostics {
    private BodySizeDiagnostics() {}

    public static String describe(LivingEntity entity) {
        var in = RigHitboxes.inputs(entity);
        var requested = RigHitboxes.desired(entity, entity.getPose());
        var query = entity.getDimensions(entity.getPose());
        //? if neoforge {
        float queryWidth = query.width(), queryHeight = query.height();
        //?} else {
        /*float queryWidth = query.width, queryHeight = query.height;
        *///?}
        StringBuilder out = new StringBuilder(String.format(Locale.ROOT,
                "%s %s #%d pose=%s\ncached=%.3fx%.3f query=%.3fx%.3f eye=%.3f step=%.3f grounded=%s horizontalCollision=%s",
                entity.level().isClientSide ? "CLIENT" : "SERVER", entity.getName().getString(), entity.getId(),
                entity.getPose(), entity.getBbWidth(), entity.getBbHeight(), queryWidth, queryHeight,
                entity.getEyeHeight(), entity.maxUpStep(), entity.onGround(), entity.horizontalCollision));
        var bounds = entity.getBoundingBox();
        out.append(String.format(Locale.ROOT, "\nactualAABB=%.3fx%.3fx%.3f at=%.2f/%.2f/%.2f",
                bounds.getXsize(), bounds.getYsize(), bounds.getZsize(), entity.getX(), entity.getY(), entity.getZ()));
        if (in != null) out.append(String.format(Locale.ROOT,
                "\nbase=%.3fx%.3f proportions=%.3f/%.3f growth=%.3f rig=%.3f attribute=%.3f dynamic=%s",
                in.base().width(), in.base().height(), in.widthScale(), in.heightScale(), in.growth(),
                in.rigScale(), in.entityScale(), in.base().scaleWithEntity()));
        if (requested != null) out.append(String.format(Locale.ROOT, "\nrequested=%.3fx%.3f growthPending=%s",
                requested.width(), requested.height(), requested.width() > entity.getBbWidth() + .001f
                        || requested.height() > entity.getBbHeight() + .001f));
        out.append("\nblockClear=").append(RigHitboxes.noBlockCollision(entity, entity.getBoundingBox().deflate(1e-7)));
        for (Pose pose : new Pose[]{Pose.STANDING, Pose.CROUCHING, Pose.SWIMMING}) {
            var size = entity.getDimensions(pose);
            out.append(' ').append(pose).append("Fits=")
                    .append(RigHitboxes.noBlockCollision(entity, size.makeBoundingBox(entity.position()).deflate(1e-7)));
        }
        if (entity instanceof Mob mob) {
            var path = mob.getNavigation().getPath();
            out.append("\nnav=").append(mob.getNavigation().getClass().getSimpleName())
                    .append(" path=").append(path == null ? "none" : path.getNextNodeIndex() + "/" + path.getNodeCount()
                            + " canReach=" + path.canReach() + " target=" + path.getTarget());
        }
        return out.toString();
    }
}
