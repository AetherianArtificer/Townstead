package com.aetherianartificer.townstead.ritual;

import com.aetherianartificer.townstead.root.disposition.DispositionGroups;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * A weapon blessed at an altar strikes harder against the groups it was blessed against. The mark
 * lives in the stack's custom data under {@code townstead:blessing}.
 */
public final class Blessings {
    private static final String KEY = "townstead:blessing";
    private static final float BONUS = 1.25f;

    private Blessings() {}

    static boolean accepts(ItemStack stack, RitualDefinition.Offering offering) {
        if (stack.isEmpty()) return false;
        if (offering.tag()) return stack.is(TagKey.create(Registries.ITEM, offering.accepts()));
        return offering.accepts().equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    static ItemStack bless(ItemStack stack, List<String> against, String by) {
        ItemStack blessed = stack.copy();
        CompoundTag mark = new CompoundTag();
        ListTag groups = new ListTag();
        for (String group : against) groups.add(StringTag.valueOf(group));
        mark.put("against", groups);
        mark.putString("by", by);
        CompoundTag root = customData(blessed);
        root.put(KEY, mark);
        //? if >=1.21 {
        blessed.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(root));
        //?} else {
        /*blessed.getOrCreateTag().put(KEY, mark);
        *///?}
        return blessed;
    }

    /** Damage from a blessed weapon, raised against the groups it was blessed against. */
    public static float modify(LivingEntity target, DamageSource source, float amount) {
        if (!(source.getEntity() instanceof LivingEntity attacker)) return amount;
        CompoundTag mark = mark(attacker.getMainHandItem());
        if (mark == null) return amount;
        String group = DispositionGroups.of(target);
        ListTag against = mark.getList("against", 8);
        for (int i = 0; i < against.size(); i++) {
            if (against.getString(i).equals(group)) return amount * BONUS;
        }
        return amount;
    }

    public static void tooltip(ItemStack stack, List<Component> lines) {
        CompoundTag mark = mark(stack);
        if (mark == null) return;
        ListTag against = mark.getList("against", 8);
        java.util.Set<String> named = new java.util.LinkedHashSet<>();
        for (int i = 0; i < against.size(); i++) {
            named.add(Component.translatable("disposition.townstead." + against.getString(i)).getString());
        }
        Component groups = Component.literal(String.join(", ", named));
        lines.add(Component.translatable("item.townstead.blessed", mark.getString("by"), groups)
                .withStyle(ChatFormatting.GOLD));
    }

    private static CompoundTag mark(ItemStack stack) {
        if (stack.isEmpty()) return null;
        CompoundTag root = customData(stack);
        return root.contains(KEY, 10) ? root.getCompound(KEY) : null;
    }

    private static CompoundTag customData(ItemStack stack) {
        //? if >=1.21 {
        net.minecraft.world.item.component.CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
        //?} else {
        /*return stack.getTag() == null ? new CompoundTag() : stack.getTag().copy();
        *///?}
    }
}
