package com.aetherianartificer.townstead.client.render;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Hides a sleeper lying in a closed coffin. The lid closes over the sleeper, but a villager lies at
 * bed height and would show on top of it. Vampirism fits its own players inside.
 */
public final class ClosedCoffinRender {
    private static final TagKey<Block> COFFINS = TagKey.create(Registries.BLOCK, ResourceLocation.tryParse("vampirism:coffin"));

    private ClosedCoffinRender() {}

    //? if neoforge {
    public static void onRenderLivingPre(net.neoforged.neoforge.client.event.RenderLivingEvent.Pre<?, ?> event) {
    //?} else {
    /*public static void onRenderLivingPre(net.minecraftforge.client.event.RenderLivingEvent.Pre<?, ?> event) {
    *///?}
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player || !entity.isSleeping()) return;
        entity.getSleepingPos().ifPresent(pos -> {
            if (closed(entity.level().getBlockState(pos))) event.setCanceled(true);
        });
    }

    private static boolean closed(BlockState state) {
        if (!state.is(COFFINS)) return false;
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals("closed")) return Boolean.TRUE.equals(state.getValue(property));
        }
        return false;
    }
}
