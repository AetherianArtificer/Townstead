package com.aetherianartificer.townstead.devlink;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The root author's debug tool. Use it in the air to open the Bench Link screen (start or stop
 * the server, see the connected Blockbench sessions). Use it on a villager or player to make them
 * the preview subject the plugin follows. While it is held, the client draws attachment points and
 * anchors on rendered villagers. It has no recipe, and every action is permission-checked on the
 * server, so a stray copy in survival does nothing.
 */
public class BenchLinkItem extends Item {

    public BenchLinkItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) BenchLinkStatus.open(serverPlayer);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /**
     * Entity-interact hook, called before the target's own interaction (an MCA villager would
     * otherwise open its menu). Returns true when the click was consumed.
     */
    public static boolean onInteract(Player player, Entity target, InteractionHand hand) {
        if (!(player.getItemInHand(hand).getItem() instanceof BenchLinkItem)) return false;
        if (!(target instanceof VillagerEntityMCA) && !(target instanceof Player)) return false;
        if (player instanceof ServerPlayer serverPlayer) {
            if (!BenchLink.mayUse(serverPlayer)) {
                serverPlayer.displayClientMessage(Component.translatable("townstead.bench_link.no_permission"), true);
            } else {
                BenchLink.setSubject(target);
                serverPlayer.displayClientMessage(Component.translatable("townstead.bench_link.subject_set",
                        target.getName()), true);
            }
        }
        return true;
    }

    /** True when {@code player} holds the tool in either hand (the client draws gizmos then). */
    public static boolean holding(Player player) {
        return player.getMainHandItem().getItem() instanceof BenchLinkItem
                || player.getOffhandItem().getItem() instanceof BenchLinkItem;
    }

    //? if >=1.21 {
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        addTooltip(tooltip);
    }
    //?} else {
    /*@Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        addTooltip(tooltip);
    }
    *///?}

    private static void addTooltip(List<Component> tooltip) {
        tooltip.add(Component.translatable("item.townstead.bench_link.tooltip.use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.townstead.bench_link.tooltip.villager").withStyle(ChatFormatting.GRAY));
    }
}
