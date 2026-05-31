package com.aetherianartificer.townstead.item;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Feeds a drinkable potion to an MCA villager when the player right-clicks it
 * (wired from the EntityInteract event in {@code Townstead.java}). The potion's
 * effects are applied to the villager exactly as drinking would — instant effects
 * fire at once (so the immortality marker flips the flag), others are added — with
 * a drink sound, potion particles, and a returned glass bottle. Splash/lingering
 * bottles are excluded (those are thrown); players gain nothing from being fed.
 */
public final class VillagerPotionFeeding {

    private VillagerPotionFeeding() {}

    /** A regular potion bottle carrying at least one effect (splash/lingering excluded). */
    public static boolean isFeedable(ItemStack stack) {
        return stack.getItem() == Items.POTION && !effects(stack).isEmpty();
    }

    public static void feed(Player player, VillagerEntityMCA villager, ItemStack stack, InteractionHand hand) {
        List<MobEffectInstance> effects = effects(stack);
        if (effects.isEmpty()) return;
        for (MobEffectInstance instance : effects) {
            apply(player, villager, instance);
        }
        villager.level().playSound(null, villager.getX(), villager.getY(), villager.getZ(),
                SoundEvents.GENERIC_DRINK, SoundSource.NEUTRAL, 0.8f, 1.0f);
        if (villager.level() instanceof ServerLevel server) {
            server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stack),
                    villager.getX(), villager.getEyeY() - 0.1, villager.getZ(),
                    14, 0.2, 0.15, 0.2, 0.05);
        }
        if (!player.getAbilities().instabuild) {
            ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
            stack.shrink(1);
            if (stack.isEmpty()) {
                player.setItemInHand(hand, bottle);
            } else if (!player.getInventory().add(bottle)) {
                player.drop(bottle, false);
            }
        }
    }

    private static void apply(Player player, VillagerEntityMCA villager, MobEffectInstance instance) {
        //? if neoforge {
        if (instance.getEffect().value().isInstantenous()) {
            instance.getEffect().value().applyInstantenousEffect(player, player, villager, instance.getAmplifier(), 1.0);
        } else {
            villager.addEffect(new MobEffectInstance(instance));
        }
        //?} else {
        /*if (instance.getEffect().isInstantenous()) {
            instance.getEffect().applyInstantenousEffect(player, player, villager, instance.getAmplifier(), 1.0);
        } else {
            villager.addEffect(new MobEffectInstance(instance));
        }
        *///?}
    }

    private static List<MobEffectInstance> effects(ItemStack stack) {
        //? if neoforge {
        net.minecraft.world.item.alchemy.PotionContents contents =
                stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        if (contents == null) return List.of();
        List<MobEffectInstance> out = new ArrayList<>();
        contents.getAllEffects().forEach(out::add);
        return out;
        //?} else {
        /*return net.minecraft.world.item.alchemy.PotionUtils.getMobEffects(stack);
        *///?}
    }
}
