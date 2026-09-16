package com.aetherianartificer.townstead.compat.wholecloth;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.data.DataPackLang;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Whole Cloth drops cloth from the mobs it lists in its own {@code cloth_drops} entity tag, but
 * only for a player's kill. A guard's kill gives the village the same scrap at the same odds,
 * straight into the guard's pockets so the store task shelves it. The tag is read from Whole
 * Cloth's data, never copied, so a pack that extends it extends this too.
 */
public final class WholeClothDrops {

    public static final String MOD_ID = "wholecloth";
    /** The mod's own base chance for its cloth drop. */
    public static final float DROP_CHANCE = 0.75f;

    private static final ResourceLocation CLOTH_DROPS = DataPackLang.parseId("wholecloth:cloth_drops");
    private static final ResourceLocation CLOTH_SCRAP = DataPackLang.parseId("wholecloth:cloth_scrap");

    private WholeClothDrops() {}

    public static void onDeath(@Nullable LivingEntity victim, @Nullable DamageSource source) {
        if (victim == null || source == null || victim.level().isClientSide) return;
        if (!(source.getEntity() instanceof VillagerEntityMCA killer)) return;
        if (!ModCompat.isLoaded(MOD_ID) || !dropsCloth(victim.getType())) return;
        if (!rolls(victim.getRandom())) return;
        Item scrap = BuiltInRegistries.ITEM.get(CLOTH_SCRAP);
        if (scrap == Items.AIR) return;
        ItemStack leftover = killer.getInventory().addItem(new ItemStack(scrap));
        if (!leftover.isEmpty()) killer.spawnAtLocation(leftover);
    }

    static boolean dropsCloth(EntityType<?> type) {
        return type != null && type.is(TagKey.create(Registries.ENTITY_TYPE, CLOTH_DROPS));
    }

    static boolean rolls(RandomSource random) {
        return random.nextFloat() < DROP_CHANCE;
    }
}
