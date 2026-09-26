package com.aetherianartificer.townstead.client.haze;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.block.haze.HazeBlock;
import com.aetherianartificer.townstead.block.haze.HazeKind;
import net.minecraft.client.renderer.block.BlockModelShaper;
//? if neoforge {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
//?} else if forge {
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
*///?}

//? if neoforge {
@EventBusSubscriber(modid = Townstead.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
//?} else if forge {
/*@Mod.EventBusSubscriber(modid = Townstead.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
*///?}
public final class HazeModelEvents {
    private HazeModelEvents() {}

    /** Every haze state renders through one model that reads the cell's kind. */
    @SubscribeEvent
    public static void wrap(ModelEvent.ModifyBakingResult event) {
        HazeModel model = new HazeModel();
        HazeModel.invalidate();
        for (var state : Townstead.HAZE.get().getStateDefinition().getPossibleStates()) {
            event.getModels().put(BlockModelShaper.stateToModelLocation(state), model);
        }
    }

    /** Kinds without their own texture tint the grayscale default to their colour. */
    @SubscribeEvent
    public static void tint(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            HazeKind kind = HazeBlock.kindOf(state, true);
            return tintIndex == 0 && kind != null ? kind.color() : -1;
        }, Townstead.HAZE.get());
    }
}
