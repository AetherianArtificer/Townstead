package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The entity can be sheared right now: a sheep with its wool grown, a mooshroom, a snow golem
 * with its pumpkin, or a modded mob that declares itself shearable to the loader.
 *
 * <p>JSON: {@code { "type":"pheno:shearable" }}</p>
 */
public final class ShearableConditionType implements ConditionType {

    public static final String KEY = "pheno:shearable";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        return ctx -> ready(ctx.entity());
    }

    public static boolean ready(LivingEntity entity) {
        if (entity == null || !entity.isAlive()) return false;
        if (entity instanceof Shearable shearable) return shearable.readyForShearing();
        ItemStack shears = new ItemStack(Items.SHEARS);
        //? if neoforge {
        return entity instanceof net.neoforged.neoforge.common.IShearable shearable
                && shearable.isShearable(null, shears, entity.level(), entity.blockPosition());
        //?} else if forge {
        /*return entity instanceof net.minecraftforge.common.IForgeShearable shearable
                && shearable.isShearable(shears, entity.level(), entity.blockPosition());
        *///?}
    }
}
