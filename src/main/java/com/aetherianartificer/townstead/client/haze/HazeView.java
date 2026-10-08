package com.aetherianartificer.townstead.client.haze;

import com.aetherianartificer.townstead.block.haze.HazeBlock;
import com.aetherianartificer.townstead.block.haze.HazeKind;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Pulls the fog in around a camera whose eyes are inside a fogging cloud, thinning as it decays. */
public final class HazeView {

    private HazeView() {}

    //? if neoforge {
    public static void onRenderFog(net.neoforged.neoforge.client.event.ViewportEvent.RenderFog event) {
    //?} else {
    /*public static void onRenderFog(net.minecraftforge.client.event.ViewportEvent.RenderFog event) {
    *///?}
        BlockState state = stateAt(event.getCamera());
        HazeKind kind = fogging(state);
        if (kind == null) return;
        // Eases from the kind's fog distance toward normal view as the cell thins, so by its last
        // step the fog is nearly gone and clearing the cell changes almost nothing.
        float normal = event.getFarPlaneDistance();
        float cleared = 1f - (float) state.getValue(HazeBlock.DENSITY) / HazeBlock.MAX_DENSITY;
        float far = kind.fogDistance() + (normal - kind.fogDistance()) * cleared * cleared;
        event.setNearPlaneDistance(0f);
        event.setFarPlaneDistance(Math.min(normal, far));
        event.setCanceled(true);
    }

    //? if neoforge {
    public static void onFogColor(net.neoforged.neoforge.client.event.ViewportEvent.ComputeFogColor event) {
    //?} else {
    /*public static void onFogColor(net.minecraftforge.client.event.ViewportEvent.ComputeFogColor event) {
    *///?}
        HazeKind kind = fogging(stateAt(event.getCamera()));
        if (kind == null) return;
        event.setRed(kind.red());
        event.setGreen(kind.green());
        event.setBlue(kind.blue());
    }

    @Nullable
    private static BlockState stateAt(Camera camera) {
        return Minecraft.getInstance().level == null ? null
                : Minecraft.getInstance().level.getBlockState(camera.getBlockPosition());
    }

    @Nullable
    private static HazeKind fogging(@Nullable BlockState state) {
        if (state == null) return null;
        HazeKind kind = HazeBlock.kindOf(state, true);
        return kind != null && kind.shape() == HazeKind.Shape.CLOUD && kind.fogDistance() > 0f ? kind : null;
    }
}
