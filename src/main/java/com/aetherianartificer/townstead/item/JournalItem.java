package com.aetherianartificer.townstead.item;

import com.aetherianartificer.townstead.rebirth.Relearning;
import com.aetherianartificer.townstead.switchboard.ContentGates;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The journal a past life leaves behind. Whoever reads it relearns that life's careers faster, so
 * it can be kept, lost, or handed to someone else. Reading uses it up.
 */
public class JournalItem extends Item {
    private static final String LIFE = "life";
    private static final String NAME = "name";

    public JournalItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(Item item, UUID memorialId, String name) {
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = new CompoundTag();
        tag.putUUID(LIFE, memorialId);
        tag.putString(NAME, name);
        //? if >=1.21 {
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        //?} else {
        /*stack.getOrCreateTag().merge(tag);
        *///?}
        return stack;
    }

    private static CompoundTag data(ItemStack stack) {
        //? if >=1.21 {
        net.minecraft.world.item.component.CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
        //?} else {
        /*CompoundTag tag = stack.getTag();
        return tag == null ? new CompoundTag() : tag;
        *///?}
    }

    private static @Nullable String owner(ItemStack stack) {
        CompoundTag tag = data(stack);
        return tag.contains(NAME) ? tag.getString(NAME) : null;
    }

    @Override
    public Component getName(ItemStack stack) {
        String owner = owner(stack);
        return owner == null ? super.getName(stack) : Component.translatable("item.townstead.journal.of", owner);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer reader)) return InteractionResultHolder.success(stack);
        CompoundTag tag = data(stack);
        if (!ContentGates.enabled(stack) || !tag.hasUUID(LIFE) || !Relearning.read(reader, tag.getUUID(LIFE))) {
            reader.displayClientMessage(Component.translatable("townstead.journal.unreadable"), true);
            return InteractionResultHolder.fail(stack);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 1.0f);
        reader.displayClientMessage(Component.translatable("townstead.journal.read", owner(stack),
                Relearning.speed()), false);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResultHolder.consume(stack);
    }

    //? if >=1.21 {
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        tooltip(stack, lines);
    }
    //?} else {
    /*@Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        tooltip(stack, lines);
    }
    *///?}

    private static void tooltip(ItemStack stack, List<Component> lines) {
        if (owner(stack) == null) return;
        lines.add(Component.translatable("townstead.journal.tooltip", Relearning.speed()).withStyle(ChatFormatting.GRAY));
    }
}
