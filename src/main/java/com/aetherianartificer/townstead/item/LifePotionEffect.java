package com.aetherianartificer.townstead.item;

import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * The marker effect behind the agelessness / aging potions. It carries no attributes; its only job is
 * to be applied (instantaneously, like Healing) so a thrown splash potion flips an MCA villager's
 * granted-ageless flag to {@link #freeze}: {@code true} pins the villager's life stage and stops the
 * vanilla age field, while {@code false} returns it to normal aging. This is the agelessness axis, NOT
 * immortality (the immortal trait/gene is untouched). Applying it to a player (or anything that isn't
 * an MCA villager) does nothing.
 *
 * <p>{@code applyInstantenousEffect} is signature-identical on 1.20.1 Forge and
 * 1.21.1 NeoForge, so this class needs no version splits.</p>
 */
public class LifePotionEffect extends MobEffect {

    private final boolean freeze;

    public LifePotionEffect(boolean freeze, int color) {
        super(MobEffectCategory.NEUTRAL, color);
        this.freeze = freeze;
    }

    @Override
    public boolean isInstantenous() {
        return true;
    }

    @Override
    public void applyInstantenousEffect(@Nullable Entity source, @Nullable Entity indirectSource,
                                        LivingEntity target, int amplifier, double health) {
        if (target.level().isClientSide) return;
        if (target instanceof VillagerEntityMCA villager) {
            TownsteadVillagers.get(villager).life().setAgeless(freeze);
        }
    }
}
