package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.root.hook.PhenoHooks;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercept for the {@code anvil_material_repair} and {@code anvil_prior_work} modifier targets:
 * after vanilla builds the anvil result, it is re-shaped by the player's modifiers, with the
 * worked item as the subject. Extends {@link ItemCombinerMenu} to reach the inherited slots, since
 * inherited fields cannot be shadowed. {@code anvil_break_chance} rides the repair event, in
 * {@code PhenoHookEvents}. 1.20.1 SRG: {@code m_6640_} createResult,
 * {@code f_39000_} repairItemCountCost, {@code f_39002_} cost.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuPhenoMixin extends ItemCombinerMenu {

    //? if neoforge {
    @Shadow private int repairItemCountCost;
    @Shadow @Final private DataSlot cost;
    //?} else {
    /*@Shadow(remap = false) private int f_39000_;
    @Shadow(remap = false) @Final private DataSlot f_39002_;
    *///?}

    protected AnvilMenuPhenoMixin(MenuType<?> type, int id, Inventory inventory, ContainerLevelAccess access) {
        super(type, id, inventory, access);
    }

    //? if neoforge {
    @Inject(method = "createResult", at = @At("RETURN"))
    //?} else {
    /*@Inject(method = "m_6640_", at = @At("RETURN"), remap = false)
    *///?}
    private void townstead$anvilCare(CallbackInfo ci) {
        ItemStack left = this.inputSlots.getItem(0);
        ItemStack right = this.inputSlots.getItem(1);
        ItemStack result = this.resultSlots.getItem(0);
        if (left.isEmpty() || result.isEmpty() || this.player.level().isClientSide) return;

        int vanillaUnits = townstead$repairUnits();
        float perUnitFraction = PhenoHooks.anvilMaterialRepair(this.player, left, 0.25f);
        if (perUnitFraction != 0.25f && vanillaUnits > 0 && !right.isEmpty()
                && left.isDamageableItem() && left.getItem().isValidRepairItem(left, right)) {
            int perUnit = Math.max(1, (int) (left.getMaxDamage() * perUnitFraction));
            int damage = left.getDamageValue();
            int units = 0;
            while (damage > 0 && units < right.getCount()) {
                damage -= Math.min(damage, perUnit);
                units++;
            }
            result.setDamageValue(damage);
            townstead$setRepairUnits(units);
            DataSlot levels = townstead$cost();
            levels.set(Math.max(1, levels.get() - vanillaUnits + units));
        }
        float keep = PhenoHooks.anvilPriorWork(this.player, left);
        if (keep != 1f) {
            int before = Math.max(townstead$priorWork(left), townstead$priorWork(right));
            int increase = Math.max(0, townstead$priorWork(result) - before);
            townstead$setPriorWork(result, before + Math.round(increase * keep));
        }
    }

    @Unique
    private int townstead$repairUnits() {
        //? if neoforge {
        return repairItemCountCost;
        //?} else {
        /*return f_39000_;
        *///?}
    }

    @Unique
    private void townstead$setRepairUnits(int units) {
        //? if neoforge {
        repairItemCountCost = units;
        //?} else {
        /*f_39000_ = units;
        *///?}
    }

    @Unique
    private DataSlot townstead$cost() {
        //? if neoforge {
        return cost;
        //?} else {
        /*return f_39002_;
        *///?}
    }

    @Unique
    private static int townstead$priorWork(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        //? if neoforge {
        return stack.getOrDefault(net.minecraft.core.component.DataComponents.REPAIR_COST, 0);
        //?} else {
        /*return stack.getBaseRepairCost();
        *///?}
    }

    @Unique
    private static void townstead$setPriorWork(ItemStack stack, int value) {
        //? if neoforge {
        stack.set(net.minecraft.core.component.DataComponents.REPAIR_COST, value);
        //?} else {
        /*stack.setRepairCost(value);
        *///?}
    }
}
