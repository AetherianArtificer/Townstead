package com.aetherianartificer.townstead.story.reward;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
//? if >=1.21 {
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
//?}
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * One quest reward, built from data by {@link Rewards}: fixed item stacks, a loot table rolled
 * when it is given, or a Pheno action run on the player with the teller as {@code other}. Item
 * rewards show in the Quest Ledger as the items themselves; the others show their {@code text}.
 */
public final class Reward {
    private static final Logger LOGGER = LoggerFactory.getLogger("Townstead/Stories");

    public record Stack(ResourceLocation item, int count) {}

    /** What the ledger shows: an item with a count, or a line of text. */
    public record Preview(String text, String itemId, int count) {}

    private final @Nullable String text;
    private final List<Stack> items;
    private final @Nullable ResourceLocation lootTable;
    private final @Nullable Action action;

    Reward(@Nullable String text, List<Stack> items, @Nullable ResourceLocation lootTable, @Nullable Action action) {
        this.text = text;
        this.items = List.copyOf(items);
        this.lootTable = lootTable;
        this.action = action;
    }

    public List<Preview> preview(String teller, String player) {
        if (!items.isEmpty() && text == null) {
            return items.stream().map(s -> new Preview("", s.item().toString(), s.count())).toList();
        }
        String line = text == null ? "" : text.replace("{teller}", teller).replace("{player}", player);
        String icon = items.isEmpty() ? "" : items.get(0).item().toString();
        return List.of(new Preview(line, icon, 0));
    }

    /** Hands the reward to the player. Anything that does not fit in their inventory drops at their feet. */
    public void give(ServerPlayer player, @Nullable LivingEntity teller) {
        for (Stack stack : items) {
            var item = BuiltInRegistries.ITEM.get(stack.item());
            int left = stack.count();
            while (left > 0) {
                ItemStack give = new ItemStack(item);
                give.setCount(Math.min(left, give.getMaxStackSize()));
                left -= give.getCount();
                handOver(player, give);
            }
        }
        if (lootTable != null) {
            LootTable table = table(player.serverLevel(), lootTable);
            if (table == null) {
                LOGGER.warn("Story reward: no loot table {}", lootTable);
            } else {
                LootParams params = new LootParams.Builder(player.serverLevel())
                        .withParameter(LootContextParams.ORIGIN, player.position())
                        .withParameter(LootContextParams.THIS_ENTITY, player)
                        .create(LootContextParamSets.GIFT);
                table.getRandomItems(params).forEach(stack -> handOver(player, stack));
            }
        }
        if (action != null) action.run(new ActionContext(player, teller));
    }

    private static void handOver(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack) && !stack.isEmpty()) player.drop(stack, false);
    }

    private static @Nullable LootTable table(ServerLevel level, ResourceLocation id) {
        //? if >=1.21 {
        LootTable table = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id));
        //?} else {
        /*LootTable table = level.getServer().getLootData().getLootTable(id);
        *///?}
        return table == null || table == LootTable.EMPTY ? null : table;
    }
}
