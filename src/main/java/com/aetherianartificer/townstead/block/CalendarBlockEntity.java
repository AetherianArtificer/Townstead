package com.aetherianartificer.townstead.block;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Marker block entity for {@link CalendarBlock}. Carries no state — the
 * displayed date is read live from {@code CalendarClientStore} during render.
 * Exists only so a {@link net.minecraft.client.renderer.blockentity.BlockEntityRenderer}
 * can attach and draw the month/day overlay on the block face.
 */
public class CalendarBlockEntity extends BlockEntity {
    public CalendarBlockEntity(BlockPos pos, BlockState state) {
        super(Townstead.CALENDAR_BE.get(), pos, state);
    }
}
