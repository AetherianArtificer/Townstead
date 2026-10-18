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
        // A founding altar founds its order where it stands, so it has to stand in a village.
        if (context.getLevel() instanceof ServerLevel level && claimFor(context.getItemInHand(), player, asItem()) != null
                && VillageManager.get(level).findNearestVillage(context.getClickedPos(), Village.MERGE_MARGIN).isEmpty()) {
            if (player != null) player.displayClientMessage(Component.translatable("block.townstead.oath_altar.no_village"), true);
            return null;
        }
        if (context.getLevel() instanceof ServerLevel level && (player == null || !player.isCreative())
                && claimFor(context.getItemInHand(), player, asItem()) == null
                && orderAt(level, context.getClickedPos(), net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(asItem())) == null) {
            if (player != null) player.displayClientMessage(Component.translatable("block.townstead.oath_altar.no_order"), true);
            return null;
        }
        return super.getStateForPlacement(context);
    }

    @Override
    public void setPlacedBy(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
                            @Nullable net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel server)) return;
        var order = orderAt(server, pos, net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(asItem()));
        if (order == null) order = foundFromClaim(server, pos, placer, stack);
        if (order != null) OrderAltars.get(server.getServer()).put(order.id(), net.minecraft.core.GlobalPos.of(server.dimension(), pos));
    }

    /** The claim this altar founds with: its own mark, else the placing player's pending founding. */
    private static @Nullable FoundingClaim claimFor(net.minecraft.world.item.ItemStack stack, @Nullable Player player,
                                                    net.minecraft.world.item.Item item) {
        FoundingClaim claim = FoundingClaim.of(stack);
        if (claim != null || player == null) return claim;
        return FoundingClaim.pending(player, net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item));
    }

    /** An altar handed over to found an order founds it where it is first placed, in a village. */
    private static @Nullable com.aetherianartificer.townstead.politics.state.Faction foundFromClaim(
            ServerLevel level, BlockPos pos, @Nullable net.minecraft.world.entity.LivingEntity placer,
            net.minecraft.world.item.ItemStack stack) {
        FoundingClaim claim = claimFor(stack, placer instanceof Player p ? p : null, stack.getItem());
        if (claim == null) return null;
        Village village = VillageManager.get(level).findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
        if (village == null) return null;
        // Founded already, elsewhere: a spare altar does not found a second order.
        if (Orders.leads(PoliticalSavedData.get(level.getServer()), claim.kind(), claim.leader())) {
            if (placer instanceof Player founder) FoundingClaim.forget(founder, claim.kind());
            return null;
        }
        var order = Orders.found(level, village, claim.kind(), claim.leader());
        if (order != null && placer instanceof Player founder) FoundingClaim.forget(founder, claim.kind());
        if (order != null && placer instanceof Player player) {
            player.displayClientMessage(Component.translatable("command.townstead.order.founded", order.name(),
                    village.getName(), claim.leaderName()), false);
        }
        return order;
    }

    /** Using an altar, such as one found in a village, teaches its recipe. */
    @Override
    //? if >=1.21 {
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
                                                                  Player player, net.minecraft.world.phys.BlockHitResult hit) {
    //?} else {
    /*public net.minecraft.world.InteractionResult use(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
                                                     Player player, net.minecraft.world.InteractionHand hand,
                                                     net.minecraft.world.phys.BlockHitResult hit) {
    *///?}
        if (player instanceof net.minecraft.server.level.ServerPlayer server) {
            com.aetherianartificer.townstead.item.LearnedRecipes.teach(server, asItem());
        }
        return net.minecraft.world.InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (level instanceof ServerLevel server && !state.is(newState.getBlock())) {
            OrderAltars.get(server.getServer()).removeAt(net.minecraft.core.GlobalPos.of(server.dimension(), pos));
        }
        super.onRemove(state, level, pos, newState, moved);
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
