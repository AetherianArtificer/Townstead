package com.aetherianartificer.townstead.politics.order;

import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The heart of an order's hall, such as a hunters' lodge. It stands only in a village where an order
 * that gives it at founding is based, and the hall is recognized only around one. Oaths are sworn at it.
 */
public class OathAltarBlock extends Block {

    public OathAltarBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Player player = context.getPlayer();
        if (context.getLevel() instanceof ServerLevel level && (player == null || !player.isCreative())
                && orderAt(level, context.getClickedPos(), net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(asItem())) == null) {
            if (player != null) player.displayClientMessage(Component.translatable("block.townstead.oath_altar.no_order"), true);
            return null;
        }
        return super.getStateForPlacement(context);
    }

    /** The order based at the village around {@code pos} whose founding gives {@code gift}, or null. */
    public static @Nullable com.aetherianartificer.townstead.politics.state.Faction orderAt(ServerLevel level, BlockPos pos,
                                                                                     net.minecraft.resources.ResourceLocation gift) {
        Village village = VillageManager.get(level).findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
        if (village == null) return null;
        SettlementRef settlement = new SettlementRef(level.dimension().location(), village.getId());
        PoliticalSavedData data = PoliticalSavedData.get(level.getServer());
        for (var faction : data.factions()) {
            var kind = com.aetherianartificer.townstead.politics.definition.PoliticalDefinitions.snapshot().kind(faction.kind());
            if (faction.active() && faction.settlements().isEmpty() && settlement.equals(faction.home())
                    && kind != null && kind.founding().gifts().contains(gift)) {
                return faction;
            }
        }
        return null;
    }
}
