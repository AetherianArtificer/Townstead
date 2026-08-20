package com.aetherianartificer.townstead.work.recipe;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.work.station.WorkstationV2Def;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
//? if >=1.21 {
import net.minecraft.world.ItemInteractionResult;
//?}
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
//? if >=1.21 {
import net.minecraft.world.item.crafting.RecipeHolder;
//?}
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
//? if neoforge {
import net.neoforged.neoforge.common.util.FakePlayerFactory;
//?} else if forge {
/*import net.minecraftforge.common.util.FakePlayerFactory;
*///?}
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Discovers recipes which describe a sequence of interactions rather than returning an item from
 * {@link Recipe#getResultItem}. The reader recognizes the public shape of a transition recipe:
 * a stage, one ingredient, and a result which either selects the next state or a terminal block.
 * It deliberately knows no mod ids or recipe classes.
 *
 * <p>Terminal blocks are asked how empty-hand collection behaves at a disposable air position.
 * The resulting item entities are captured and removed in the same call. This matters for blocks
 * which have no item or loot table but dispense several items when used. The probe is cached by
 * normal recipe discovery, so it runs once per terminal block per datapack generation.</p>
 */
public final class InteractionRecipeGraphs {

    private static final GameProfile PROFILE = new GameProfile(
            UUID.fromString("af364786-51a0-4fd0-b74c-f3cf9bfbb3b8"), "[TownsteadRecipeProbe]");
    private static final int MAX_PATH = 24;

    private InteractionRecipeGraphs() {}

    public static List<DiscoveredRecipe> discover(ServerLevel level, ResourceLocation typeId,
                                                   StationType role, WorkstationV2Def def) {
        List<Edge> edges = readEdges(level, typeId);
        if (edges.isEmpty()) return List.of();

        Map<String, List<Edge>> byStage = new LinkedHashMap<>();
        Set<String> destinations = new HashSet<>();
        for (Edge edge : edges) {
            byStage.computeIfAbsent(edge.stage(), ignored -> new ArrayList<>()).add(edge);
            if (edge.nextStage() != null) destinations.add(edge.nextStage());
        }
        List<String> roots = byStage.keySet().stream()
                .filter(stage -> !destinations.contains(stage)).toList();
        if (roots.isEmpty()) return List.of();

        Map<ResourceLocation, ItemStack> observed = new HashMap<>();
        List<DiscoveredRecipe> out = new ArrayList<>();
        for (String root : roots) {
            walk(level, role, def, byStage, root, new ArrayList<>(), new LinkedHashSet<>(),
                    observed, out);
        }
        return List.copyOf(out);
    }

    private static List<Edge> readEdges(ServerLevel level, ResourceLocation typeId) {
        List<Edge> edges = new ArrayList<>();
        //? if >=1.21 {
        for (var holder : WorkRecipeRegistry.getRecipesForType(level, typeId)) {
            Recipe<?> recipe = holder.value();
            ResourceLocation id = holder.id();
            RecipeHolder<?> source = holder;
        //?} else {
        /*for (Recipe<?> recipe : WorkRecipeRegistry.getRecipesForType(level, typeId)) {
            ResourceLocation id = recipe.getId();
            Recipe<?> source = recipe;
        *///?}
            Object stageValue = invoke(recipe, "stage", "getStage");
            Object result = invoke(recipe, "result", "getResult");
            if (stageValue == null || result == null) continue;
            List<RecipeIngredient> ingredients = WorkRecipeRegistry.extractIngredients(recipe);
            if (ingredients.size() != 1) continue;

            String stage = stageName(stageValue);
            ResourceLocation terminal = resourceLocation(invoke(result, "setBlock", "getSetBlock"));
            String next = nextStage(invoke(result, "setState", "getSetState"));
            if (stage == null || (terminal == null) == (next == null)) continue;
            edges.add(new Edge(id, stage, ingredients.get(0), next, terminal, source));
        }
        return List.copyOf(edges);
    }

    private static void walk(ServerLevel level, StationType role, WorkstationV2Def def,
                             Map<String, List<Edge>> byStage, String stage,
                             List<RecipeIngredient> inputs, Set<ResourceLocation> path,
                             Map<ResourceLocation, ItemStack> observed,
                             List<DiscoveredRecipe> out) {
        if (path.size() >= MAX_PATH) return;
        for (Edge edge : byStage.getOrDefault(stage, List.of())) {
            if (!path.add(edge.id())) continue;
            List<RecipeIngredient> nextInputs = new ArrayList<>(inputs);
            nextInputs.add(edge.ingredient());
            if (edge.terminalBlock() != null) {
                ItemStack product = observed.computeIfAbsent(edge.terminalBlock(), block ->
                        observeCollection(level, block));
                if (!product.isEmpty()) {
                    ResourceLocation output = BuiltInRegistries.ITEM.getKey(product.getItem());
                    if (output != null) {
                        output = def.correctedOutput(edge.id(), output);
                        List<RecipeIngredient> planned = def.withSupplies(nextInputs);
                        if (!planned.isEmpty()) {
                            out.add(new DiscoveredRecipe(edge.id(), role, 1, output,
                                    Math.max(1, product.getCount()), 1, false,
                                    null, 0, planned, false,
                                    output.getPath().contains("coffee") || output.getPath().contains("tea"),
                                    edge.source()));
                        }
                    }
                }
            } else if (edge.nextStage() != null) {
                walk(level, role, def, byStage, edge.nextStage(), nextInputs, path, observed, out);
            }
            path.remove(edge.id());
        }
    }

    /** Runs the terminal block's real empty-hand collection and records its one product stack. */
    private static ItemStack observeCollection(ServerLevel level, ResourceLocation blockId) {
        Block block = BuiltInRegistries.BLOCK.get(blockId);
        if (block == null || block == net.minecraft.world.level.block.Blocks.AIR) return ItemStack.EMPTY;
        BlockPos pos = probePosition(level);
        if (pos == null) return fallbackBlockItem(block);

        AABB area = new AABB(pos).inflate(2.0);
        Set<Integer> before = new HashSet<>();
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, area)) {
            before.add(entity.getId());
        }

        ServerPlayer actor;
        try {
            actor = FakePlayerFactory.get(level, PROFILE);
        } catch (Throwable failure) {
            return fallbackBlockItem(block);
        }

        try {
            actor.getInventory().clearContent();
            actor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            actor.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            actor.setShiftKeyDown(false);
            actor.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            actor.setPos(Vec3.atCenterOf(pos));
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos),
                    net.minecraft.core.Direction.UP, pos, false);
            BlockState state = block.defaultBlockState();
            //? if >=1.21 {
            ItemInteractionResult itemResult = state.useItemOn(ItemStack.EMPTY, level, actor,
                    InteractionHand.MAIN_HAND, hit);
            if (!itemResult.consumesAction()
                    && itemResult == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
                state.useWithoutItem(level, actor, hit);
            }
            //?} else {
            /*state.use(level, actor, InteractionHand.MAIN_HAND, hit);
            *///?}

            Map<net.minecraft.world.item.Item, Integer> counts = new LinkedHashMap<>();
            for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, area)) {
                if (before.contains(entity.getId())) continue;
                ItemStack stack = entity.getItem().copy();
                entity.discard();
                if (!stack.isEmpty()) counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
            for (int slot = 0; slot < actor.getInventory().getContainerSize(); slot++) {
                ItemStack stack = actor.getInventory().removeItemNoUpdate(slot);
                if (!stack.isEmpty()) counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
            if (counts.size() != 1) return fallbackBlockItem(block);
            var product = counts.entrySet().iterator().next();
            return new ItemStack(product.getKey(), Math.max(1, product.getValue()));
        } catch (Throwable failure) {
            Townstead.LOGGER.debug("Could not observe collection output for terminal block {}: {}",
                    blockId, failure.toString());
            return fallbackBlockItem(block);
        } finally {
            actor.setShiftKeyDown(false);
            actor.getInventory().clearContent();
            actor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            actor.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }
    }

    private static ItemStack fallbackBlockItem(Block block) {
        return block.asItem() == Items.AIR ? ItemStack.EMPTY : new ItemStack(block.asItem());
    }

    /** An unused cell at world ceiling in an already-loaded player or spawn chunk. */
    private static @Nullable BlockPos probePosition(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            BlockPos candidate = probePositionAround(level, player.blockPosition());
            if (candidate != null) return candidate;
        }
        return probePositionAround(level, level.getSharedSpawnPos());
    }

    private static @Nullable BlockPos probePositionAround(ServerLevel level, BlockPos origin) {
        int y = level.getMaxBuildHeight() - 2;
        for (int radius = 0; radius <= 4; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos candidate = new BlockPos(origin.getX() + dx, y, origin.getZ() + dz);
                    if (level.isLoaded(candidate) && level.getBlockState(candidate).isAir()) return candidate;
                }
            }
        }
        return null;
    }

    private static @Nullable Object invoke(Object target, String... names) {
        if (target == null) return null;
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name);
                if (method.getParameterCount() == 0) return method.invoke(target);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static @Nullable ResourceLocation resourceLocation(@Nullable Object value) {
        if (value instanceof ResourceLocation id) return id;
        if (value instanceof java.util.Optional<?> optional) return resourceLocation(optional.orElse(null));
        return null;
    }

    private static @Nullable String stageName(@Nullable Object value) {
        if (value instanceof Enum<?> enumeration) return enumeration.name();
        if (value instanceof String string && !string.isBlank()) return string;
        return null;
    }

    /** A state patch names its selected state through one true, public boolean component. */
    private static @Nullable String nextStage(@Nullable Object patch) {
        if (patch == null) return null;
        String selected = null;
        for (Method method : patch.getClass().getMethods()) {
            if (method.getDeclaringClass() != patch.getClass() || method.getParameterCount() != 0
                    || (method.getReturnType() != boolean.class
                    && method.getReturnType() != Boolean.class)) continue;
            try {
                if (!Boolean.TRUE.equals(method.invoke(patch))) continue;
                if (selected != null) return null;
                selected = method.getName().toUpperCase(java.util.Locale.ROOT);
            } catch (Throwable ignored) {}
        }
        return selected;
    }

    private record Edge(ResourceLocation id, String stage, RecipeIngredient ingredient,
                        @Nullable String nextStage, @Nullable ResourceLocation terminalBlock,
                        //? if >=1.21 {
                        RecipeHolder<?> source
                        //?} else {
                        /*Recipe<?> source
                        *///?}
    ) {}
}
