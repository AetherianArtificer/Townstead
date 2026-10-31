package com.aetherianartificer.townstead.client.species;

import com.aetherianartificer.townstead.root.rig.BodySizeDiagnostics;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
//? if neoforge {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
@EventBusSubscriber(modid = "townstead", value = Dist.CLIENT)
//?} else {
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
@Mod.EventBusSubscriber(modid = "townstead", value = Dist.CLIENT)
*///?}
public final class BodySizeClientCommands {
    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("townstead-size-client")
                .executes(c -> report(-1))
                .then(Commands.argument("entityId", IntegerArgumentType.integer(0))
                        .executes(c -> report(IntegerArgumentType.getInteger(c, "entityId")))));
    }
    private static int report(int id) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return 0;
        var entity = id < 0 ? mc.player : mc.level.getEntity(id);
        if (!(entity instanceof LivingEntity living)) return 0;
        String report = BodySizeDiagnostics.describe(living);
        mc.player.displayClientMessage(Component.literal(report), false);
        org.slf4j.LoggerFactory.getLogger("townstead/body-size").info(report);
        return 1;
    }
}
