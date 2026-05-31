package com.aetherianartificer.townstead.item;

import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Single-use elixir applied by right-clicking an MCA villager. Flips the
 * villager's immortality flag to {@link #freeze}: {@code true} pins their life
 * stage and stops the vanilla age field from advancing, while their calendar
 * date of birth keeps ticking (so a vampire can be "physically 20, born 1000
 * years ago"). The mortality variant clears the flag, returning the villager
 * to normal aging.
 *
 * <p>v1 ships as plain items; the brewing chain (awkward + ghast tear →
 * mortality, mortality + golden apple → immortality) is a follow-up.</p>
 */
public class LifeFreezeElixir extends Item {

    private final boolean freeze;

    public LifeFreezeElixir(boolean freeze, Properties properties) {
        super(properties);
        this.freeze = freeze;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof VillagerEntityMCA villager)) return InteractionResult.PASS;
        if (player.level().isClientSide) return InteractionResult.SUCCESS;

        TownsteadVillager.Life life = TownsteadVillagers.get(villager).life();
        if (life.immortal() == freeze) return InteractionResult.PASS;

        life.setImmortal(freeze);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        villager.level().playSound(null, villager.blockPosition(),
                SoundEvents.BREWING_STAND_BREW, SoundSource.NEUTRAL, 1.0f, freeze ? 1.4f : 0.8f);
        return InteractionResult.CONSUME;
    }
}
