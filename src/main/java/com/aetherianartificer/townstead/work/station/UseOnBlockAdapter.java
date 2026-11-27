package com.aetherianartificer.townstead.work.station;

import com.aetherianartificer.townstead.pheno.action.block.BlockActionContext;
import com.aetherianartificer.townstead.pheno.action.block.BlockActions;
import com.aetherianartificer.townstead.work.order.OrderProducts;
import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.RecipeIngredient;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A block that changes a thing used on it and hands it straight back: a heated cauldron that simmers a draught, a fire
 * that chars a herb, a cauldron that washes the dye out of leather. Nothing stays in the block, so it works like a
 * crafting surface: the worker carries the input, and at the end of the recipe's time uses it on the block through the
 * player's own interaction, keeping what comes back when it is the recipe's product. Anything else that comes back,
 * and the input when the block refuses it, stays with the worker.
 *
 * <p>{@code townstead:use_on_block} uses it plainly; {@code townstead:use_on_block_sneaking} as a sneaking player
 * does, for blocks whose plain use does something else (a campfire cooks what it is given).</p>
 */
public final class UseOnBlockAdapter implements StationAdapters.Adapter {

    public static final String NAME = "townstead:use_on_block";
    public static final String SNEAKING = "townstead:use_on_block_sneaking";

    private final boolean sneaking;

    private UseOnBlockAdapter(boolean sneaking) {
        this.sneaking = sneaking;
    }

    public static void bootstrap() {
        StationAdapters.register(NAME, new UseOnBlockAdapter(false));
        StationAdapters.register(SNEAKING, new UseOnBlockAdapter(true));
    }

    @Override
    public StationAdapters.StationPhase phase(ServerLevel level, BlockPos anchor, WorkstationDef def, @Nullable DiscoveredRecipe recipe) {
        return StationAdapters.StationPhase.IDLE;
    }

    @Override
    public boolean insert(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor, WorkstationDef def,
                          DiscoveredRecipe recipe) {
        for (RecipeIngredient input : RecipeIngredient.merge(recipe.inputs())) {
            int have = 0;
            for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
                ItemStack stack = villager.getInventory().getItem(i);
                if (input.matches(stack)) have += stack.getCount();
            }
            if (have < input.count()) return false;
        }
        return true;
    }

    @Override
    public boolean collect(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor, WorkstationDef def,
                           DiscoveredRecipe recipe) {
        List<RecipeIngredient> inputs = RecipeIngredient.merge(recipe.inputs());
        if (inputs.size() != 1 || inputs.get(0).count() < 1) return false;
        RecipeIngredient input = inputs.get(0);
        ResourceLocation product = OrderProducts.key(recipe);
        boolean made = false;
        for (int n = 0; n < input.count(); n++) {
            ItemStack one = StationProtocols.takeMatchingIngredient(villager, input);
            if (one.isEmpty()) break;
            List<ItemStack> back = use(level, villager, anchor, one);
            for (ItemStack stack : back) {
                if (OrderProducts.matches(product, stack)) made = true;
                StationProtocols.giveBack(villager, stack);
            }
        }
        return made;
    }

    /** Uses {@code held} on the block as the worker would; everything that comes back. */
    private List<ItemStack> use(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor, ItemStack held) {
        JsonObject action = new JsonObject();
        action.addProperty("type", "pheno:use_block");
        action.addProperty("item", "ingredient");
        if (sneaking) action.addProperty("secondary_use", true);
        List<ItemStack> back = new ArrayList<>();
        var parsed = BlockActions.parse(action);
        if (parsed == null) {
            back.add(held);
            return back;
        }
        BlockActionContext context = new BlockActionContext(level, anchor, villager).withItemRole("ingredient", held.copy());
        parsed.run(context);
        ItemStack remainder = context.itemRole("ingredient");
        if (!remainder.isEmpty()) back.add(remainder);
        back.addAll(context.returnedItems());
        return back;
    }
}
