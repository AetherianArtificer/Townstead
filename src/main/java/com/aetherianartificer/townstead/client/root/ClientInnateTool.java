package com.aetherianartificer.townstead.client.root;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.root.GeneCatalogEntry;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side mirror of {@code InnateTool}, resolved from the synced expressed-gene set
 * ({@link RootClientStore}) and gene catalog ({@link RootCatalogClient}). Block breaking is
 * client-paced (the client's dig progress drives when the block falls), so a server-only
 * answer would leave an innate-tool bearer punching at vanilla speed; this predicts the same
 * result the server rules. Phantom stacks and parsed condition gates are cached by their
 * catalog spec strings.
 */
public final class ClientInnateTool {

    private static final Map<String, ItemStack> TOOLS = new ConcurrentHashMap<>();
    private static final Map<String, Condition> CONDITIONS = new ConcurrentHashMap<>();
    private static final Condition ALWAYS = ctx -> true;

    private ClientInnateTool() {}

    /** Whether an innate-tool gene harvests this block (mainhand must be empty). */
    public static boolean allowsHarvest(Player player, BlockState state) {
        if (player == null || state == null || !player.getMainHandItem().isEmpty()) return false;
        ConditionContext ctx = new ConditionContext(player);
        for (String geneId : RootClientStore.behaviorGenes(player.getId())) {
            GeneCatalogEntry gene = RootCatalogClient.gene(geneId);
            if (gene == null || !gene.isInnateTool()) continue;
            if (!condition(gene.conditionJson()).test(ctx)) continue;
            if (tool(gene.innateToolItem()).isCorrectToolForDrops(state)) return true;
        }
        return false;
    }

    /** Dig speed after innate tools (max, empty mainhand only) and block-scoped multipliers. */
    public static float breakSpeed(Player player, BlockState state, float current) {
        if (player == null || state == null) return current;
        float speed = current;
        ConditionContext ctx = null;
        boolean bareHand = player.getMainHandItem().isEmpty();
        for (String geneId : RootClientStore.behaviorGenes(player.getId())) {
            GeneCatalogEntry gene = RootCatalogClient.gene(geneId);
            if (gene == null) continue;
            if (gene.isInnateTool() && bareHand) {
                if (ctx == null) ctx = new ConditionContext(player);
                if (!condition(gene.conditionJson()).test(ctx)) continue;
                speed = Math.max(speed, tool(gene.innateToolItem()).getDestroySpeed(state));
            } else if (gene.isBlockBreakSpeed()) {
                if (!matchesFilter(gene.breakSpeedFilter(), state)) continue;
                if (ctx == null) ctx = new ConditionContext(player);
                if (!condition(gene.conditionJson()).test(ctx)) continue;
                speed *= gene.breakSpeedValue();
            }
        }
        return speed;
    }

    private static boolean matchesFilter(String spec, BlockState state) {
        if (spec.isEmpty()) return false;
        if (spec.startsWith("#")) {
            ResourceLocation id = ResourceLocation.tryParse(spec.substring(1));
            return id != null && state.is(TagKey.create(Registries.BLOCK, id));
        }
        ResourceLocation id = ResourceLocation.tryParse(spec);
        return id != null && state.is(BuiltInRegistries.BLOCK.get(id));
    }

    private static ItemStack tool(String itemId) {
        return TOOLS.computeIfAbsent(itemId, key -> {
            ResourceLocation id = ResourceLocation.tryParse(key);
            Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
            return new ItemStack(item);
        });
    }

    /** The parsed gate for a catalog condition spec ({@code ""} or unparseable = always passes). */
    private static Condition condition(String conditionJson) {
        if (conditionJson == null || conditionJson.isEmpty()) return ALWAYS;
        return CONDITIONS.computeIfAbsent(conditionJson, json -> {
            try {
                Condition parsed = Conditions.parse(JsonParser.parseString(json));
                return parsed != null ? parsed : ALWAYS;
            } catch (Exception e) {
                return ALWAYS;
            }
        });
    }
}
