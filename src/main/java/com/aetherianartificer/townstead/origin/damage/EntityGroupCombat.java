package com.aetherianartificer.townstead.origin.damage;

/**
 * 1.21.1 combat parity for {@code entity_group}: vanilla can't read a per-entity mob
 * type there, so the Smite/Bane bonus undead/arthropod victims would normally take is
 * re-added here from the attacker's weapon enchantments. On 1.20.1 the
 * {@code getMobType} mixin makes vanilla apply this, so this class is empty there.
 *
 * <p>Potion heal/harm inversion is handled separately on 1.21.1 by the
 * {@code LivingEntityInvertedHealMixin}. Mob targeting is not creature-type gated in
 * vanilla on either version, so there is nothing extra to layer in for it.</p>
 */
public final class EntityGroupCombat {

    private EntityGroupCombat() {}

    //? if >=1.21 {
    public static float enchantBonus(net.minecraft.world.entity.LivingEntity victim,
                                     net.minecraft.world.damagesource.DamageSource source) {
        com.aetherianartificer.townstead.origin.gene.types.EntityGroupGeneType.Group group =
                com.aetherianartificer.townstead.origin.EntityGroups.of(victim);
        boolean undead = group == com.aetherianartificer.townstead.origin.gene.types.EntityGroupGeneType.Group.UNDEAD;
        boolean arthropod = group == com.aetherianartificer.townstead.origin.gene.types.EntityGroupGeneType.Group.ARTHROPOD;
        if (!undead && !arthropod) return 0f;
        if (!(source.getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker)) return 0f;
        net.minecraft.world.item.ItemStack weapon = attacker.getMainHandItem();
        if (weapon.isEmpty()) return 0f;

        net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> key =
                undead ? net.minecraft.world.item.enchantment.Enchantments.SMITE
                        : net.minecraft.world.item.enchantment.Enchantments.BANE_OF_ARTHROPODS;
        net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> enchantment =
                victim.level().registryAccess()
                        .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                        .getOrThrow(key);
        int level = net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(enchantment, weapon);
        return level * 2.5f;
    }
    //?}
}
