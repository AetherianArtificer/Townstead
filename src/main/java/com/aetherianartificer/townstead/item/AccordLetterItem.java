package com.aetherianartificer.townstead.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A sealed offer of accord from one faction to another. It names whom to bring it to as things
 * stood when it was proclaimed; whoever speaks for that faction when it arrives may answer it.
 */
public class AccordLetterItem extends Item {

    /** What a letter says. {@code addressee}, {@code office} and {@code seat} are empty when unknown. */
    public record Letter(ResourceLocation proposer, String proposerName, ResourceLocation recipient,
                         String recipientName, String addressee, String office, String seat) {}

    public AccordLetterItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(Item item, Letter letter) {
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = new CompoundTag();
        tag.putString("proposer", letter.proposer().toString());
        tag.putString("proposer_name", letter.proposerName());
        tag.putString("recipient", letter.recipient().toString());
        tag.putString("recipient_name", letter.recipientName());
        tag.putString("addressee", letter.addressee());
        tag.putString("office", letter.office());
        tag.putString("seat", letter.seat());
        //? if >=1.21 {
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        //?} else {
        /*stack.getOrCreateTag().merge(tag);
        *///?}
        return stack;
    }

    public static @Nullable Letter read(ItemStack stack) {
        if (!(stack.getItem() instanceof AccordLetterItem)) return null;
        CompoundTag tag;
        //? if >=1.21 {
        net.minecraft.world.item.component.CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        tag = data == null ? new CompoundTag() : data.copyTag();
        //?} else {
        /*tag = stack.getTag() == null ? new CompoundTag() : stack.getTag();
        *///?}
        ResourceLocation proposer = ResourceLocation.tryParse(tag.getString("proposer"));
        ResourceLocation recipient = ResourceLocation.tryParse(tag.getString("recipient"));
        if (proposer == null || recipient == null || tag.getString("proposer").isEmpty()) return null;
        return new Letter(proposer, tag.getString("proposer_name"), recipient, tag.getString("recipient_name"),
                tag.getString("addressee"), tag.getString("office"), tag.getString("seat"));
    }

    @Override
    public Component getName(ItemStack stack) {
        Letter letter = read(stack);
        return letter == null ? super.getName(stack)
                : Component.translatable("item.townstead.accord_letter.to", letter.recipientName());
    }

    //? if >=1.21 {
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        tooltip(stack, lines);
    }
    //?} else {
    /*@Override
    public void appendHoverText(ItemStack stack, @Nullable net.minecraft.world.level.Level level, List<Component> lines, TooltipFlag flag) {
        tooltip(stack, lines);
    }
    *///?}

    private static void tooltip(ItemStack stack, List<Component> lines) {
        Letter letter = read(stack);
        if (letter == null) return;
        lines.add(Component.translatable("item.townstead.accord_letter.from", letter.proposerName()).withStyle(ChatFormatting.GRAY));
        if (letter.addressee().isEmpty()) {
            lines.add(Component.translatable("item.townstead.accord_letter.nobody").withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("item.townstead.accord_letter.addressee", letter.addressee(),
                    Component.translatable(letter.office()), letter.recipientName())
                    .withStyle(ChatFormatting.GRAY));
        }
        if (!letter.seat().isEmpty()) {
            lines.add(Component.translatable("item.townstead.accord_letter.seat", letter.seat()).withStyle(ChatFormatting.GRAY));
        }
    }
}
