package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.compat.mca.BuildingReportReconciler;
import com.aetherianartificer.townstead.client.catalog.CatalogDataLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MCA 7.7.18+ (1.21.1) and 7.6.28+ (1.20.1 backport) can defer a successful
 * building add into a polymorph confirmation packet. The original report-message
 * TAIL hook runs before that deferred commit, so Townstead reconciles again after
 * the confirmation lands. Pseudo keeps this inert on older MCA builds where the
 * packet class does not exist.
 *
 * <p>The packet's shape differs per line: on 1.21.1 it is a record handled via
 * {@code handleServer(ServerPlayer)} with a {@code source()} accessor; the 1.20.1
 * backport declares a plain class handled via {@code receive(ServerPlayerEntity)}
 * whose {@code source} is a private field with no accessor. No @Shadow for it:
 * a shadow member missing on one shape is a fatal apply error that neither Pseudo
 * nor require=0 forgives, so the handler reads accessor-then-field reflectively.</p>
 */
@Pseudo
@Mixin(targets = "net.conczin.mca.network.c2s.ConfirmBuildingPolymorphMessage", remap = false)
public abstract class ConfirmBuildingPolymorphMessageMixin {
    private static final Logger TOWNSTEAD$LOG = LoggerFactory.getLogger("Townstead/ConfirmBuildingPolymorphMessageMixin");

    //? if neoforge {
    @Inject(method = "handleServer", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    //?} else if forge {
    /*@Inject(method = "receive", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    *///?}
    private void townstead$rejectSupersededChoice(ServerPlayer player, CallbackInfo ci) {
        String chosenType = townstead$chosenType();
        if (!CatalogDataLoader.isActiveSupersededBuildingType(chosenType)) return;
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "blueprint.scan.invalid_type"), true);
        ci.cancel();
    }

    //? if neoforge {
    @Inject(method = "handleServer", at = @At("TAIL"), remap = false, require = 0)
    //?} else if forge {
    /*@Inject(method = "receive", at = @At("TAIL"), remap = false, require = 0)
    *///?}
    private void townstead$reconcileAfterPolymorphCommit(ServerPlayer player, CallbackInfo ci) {
        BlockPos source = townstead$source();
        if (source == null) return;
        BuildingReportReconciler.reconcileNearest(player.serverLevel(), source, true, TOWNSTEAD$LOG);
    }

    @Unique
    private BlockPos townstead$source() {
        try {
            return getClass().getMethod("source").invoke(this) instanceof BlockPos pos ? pos : null;
        } catch (NoSuchMethodException e) {
            // 1.20.1-backport shape: no accessor, fall through to the field.
        } catch (ReflectiveOperationException e) {
            TOWNSTEAD$LOG.warn("Could not read polymorph confirmation source; skipping building reconcile", e);
            return null;
        }
        try {
            var field = getClass().getDeclaredField("source");
            field.setAccessible(true);
            return field.get(this) instanceof BlockPos pos ? pos : null;
        } catch (ReflectiveOperationException e) {
            TOWNSTEAD$LOG.warn("Could not read polymorph confirmation source; skipping building reconcile", e);
            return null;
        }
    }

    @Unique
    private String townstead$chosenType() {
        try {
            Object value = getClass().getMethod("chosenType").invoke(this);
            return value instanceof String text ? text : null;
        } catch (NoSuchMethodException ignored) {
            // Plain-class MCA packet shape: fall through to its private field.
        } catch (ReflectiveOperationException e) {
            TOWNSTEAD$LOG.warn("Could not read polymorph chosen type; skipping supersession guard", e);
            return null;
        }
        try {
            var field = getClass().getDeclaredField("chosenType");
            field.setAccessible(true);
            return field.get(this) instanceof String text ? text : null;
        } catch (ReflectiveOperationException e) {
            TOWNSTEAD$LOG.warn("Could not read polymorph chosen type; skipping supersession guard", e);
            return null;
        }
    }
}
