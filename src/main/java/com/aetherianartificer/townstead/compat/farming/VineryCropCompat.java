package com.aetherianartificer.townstead.compat.farming;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.farming.cellplan.TrellisSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Let's Do Vinery compat. Grape bushes are perennials — breaking them only yields seeds, and the
 * actual grape items come from right-click harvest at max age. This provider maps each grape-seed
 * variant to its grape product (for the palette label) and implements partial harvest (for the
 * farmer to collect grapes without destroying the bush).
 *
 * <p>Vinery has eight grape variants across biomes: overworld red/white, jungle red/white,
 * savanna red/white, taiga red/white.</p>
 */
public final class VineryCropCompat implements FarmerCropCompat {
    private static final String MOD_ID = "vinery";

    /**
     * Seed item path → grape product path. Both are under the "vinery" namespace.
     * The base overworld variants use the pattern {color}_grape_seeds → {color}_grape, while the
     * biome-specific variants flip to {biome}_grape_seeds_{color} → {biome}_grapes_{color}.
     */
    private static final Map<String, String> SEED_TO_PRODUCT = Map.ofEntries(
            Map.entry("red_grape_seeds", "red_grape"),
            Map.entry("white_grape_seeds", "white_grape"),
            Map.entry("jungle_grape_seeds_red", "jungle_grapes_red"),
            Map.entry("jungle_grape_seeds_white", "jungle_grapes_white"),
            Map.entry("savanna_grape_seeds_red", "savanna_grapes_red"),
            Map.entry("savanna_grape_seeds_white", "savanna_grapes_white"),
            Map.entry("taiga_grape_seeds_red", "taiga_grapes_red"),
            Map.entry("taiga_grape_seeds_white", "taiga_grapes_white")
    );

    /** Crop block path → grape product path. Used at harvest time. */
    private static final Map<String, String> BUSH_TO_PRODUCT = Map.ofEntries(
            Map.entry("red_grape_bush", "red_grape"),
            Map.entry("white_grape_bush", "white_grape"),
            Map.entry("jungle_grape_bush_red", "jungle_grapes_red"),
            Map.entry("jungle_grape_bush_white", "jungle_grapes_white"),
            Map.entry("savanna_grape_bush_red", "savanna_grapes_red"),
            Map.entry("savanna_grape_bush_white", "savanna_grapes_white"),
            Map.entry("taiga_grape_bush_red", "taiga_grapes_red"),
            Map.entry("taiga_grape_bush_white", "taiga_grapes_white")
    );

    private static final String STEM_PATH = "grapevine_stem";
    private static final String LATTICE_SUFFIX = "_lattice";

    /**
     * Seed item path → grape type name, the value of the support block's {@code grape} property.
     * Vinery grows jungle grapes on lattices only and every other grape on stems only.
     */
    private static final Map<String, String> SEED_TO_GRAPE_TYPE = Map.ofEntries(
            Map.entry("red_grape_seeds", "red"),
            Map.entry("white_grape_seeds", "white"),
            Map.entry("savanna_grape_seeds_red", "savanna_red"),
            Map.entry("savanna_grape_seeds_white", "savanna_white"),
            Map.entry("taiga_grape_seeds_red", "taiga_red"),
            Map.entry("taiga_grape_seeds_white", "taiga_white"),
            Map.entry("jungle_grape_seeds_red", "jungle_red"),
            Map.entry("jungle_grape_seeds_white", "jungle_white")
    );

    private static final Set<String> LATTICE_GRAPE_TYPES = Set.of("jungle_red", "jungle_white");

    /** Grape type name → grape product path, for picking grapes off a support. */
    private static final Map<String, String> GRAPE_TYPE_TO_PRODUCT = Map.ofEntries(
            Map.entry("red", "red_grape"),
            Map.entry("white", "white_grape"),
            Map.entry("savanna_red", "savanna_grapes_red"),
            Map.entry("savanna_white", "savanna_grapes_white"),
            Map.entry("taiga_red", "taiga_grapes_red"),
            Map.entry("taiga_white", "taiga_grapes_white"),
            Map.entry("jungle_red", "jungle_grapes_red"),
            Map.entry("jungle_white", "jungle_grapes_white")
    );

    @Override
    public String modId() { return MOD_ID; }

    @Override
    public boolean isSeed(ItemStack stack) { return false; }

    @Override
    public ResourceLocation cropProductFor(ResourceLocation seedId) {
        if (!MOD_ID.equals(seedId.getNamespace())) return null;
        String productPath = SEED_TO_PRODUCT.get(seedId.getPath());
        if (productPath == null) return null;
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, productPath);
        //?} else {
        /*return new ResourceLocation(MOD_ID, productPath);
        *///?}
    }

    @Override
    public boolean shouldPartialHarvest(BlockState state) {
        ResourceLocation key = state.getBlock().builtInRegistryHolder().key().location();
        if (!MOD_ID.equals(key.getNamespace())) return false;
        if (isSupport(key)) {
            IntegerProperty supportAge = findAgeProperty(state);
            return supportAge != null && state.getValue(supportAge) >= 4 && supportProductPath(state) != null;
        }
        if (!BUSH_TO_PRODUCT.containsKey(key.getPath())) return false;
        IntegerProperty ageProp = findAgeProperty(state);
        if (ageProp == null) return false;
        int value = state.getValue(ageProp);
        int max = ageProp.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(value);
        return value >= max;
    }

    @Override
    public List<ItemStack> doPartialHarvest(ServerLevel level, BlockPos pos, BlockState state) {
        if (!shouldPartialHarvest(state)) return List.of();
        ResourceLocation key = state.getBlock().builtInRegistryHolder().key().location();
        if (isSupport(key)) return pickSupport(level, pos, state);
        String productPath = BUSH_TO_PRODUCT.get(key.getPath());
        if (productPath == null) return List.of();

        IntegerProperty ageProp = findAgeProperty(state);
        if (ageProp == null) return List.of();

        // Reset to age 1 — matches Vinery's own right-click harvest behavior, which leaves the
        // bush at a partially-grown state so it regrows into fresh grapes rather than restarting.
        BlockState reset = state.setValue(ageProp, 1);
        level.setBlock(pos, reset, Block.UPDATE_ALL);

        List<ItemStack> drops = new ArrayList<>();
        //? if >=1.21 {
        ResourceLocation productId = ResourceLocation.fromNamespaceAndPath(MOD_ID, productPath);
        //?} else {
        /*ResourceLocation productId = new ResourceLocation(MOD_ID, productPath);
        *///?}
        BuiltInRegistries.ITEM.getOptional(productId).ifPresent(product -> {
            int count = 2 + level.random.nextInt(2);
            drops.add(new ItemStack(product, count));
        });
        return drops;
    }

    /**
     * Mirrors Vinery's own pick on a ripe support: 3-4 grapes, then a stem goes back to age 2 and
     * a lattice to age 1.
     */
    private List<ItemStack> pickSupport(ServerLevel level, BlockPos pos, BlockState state) {
        String productPath = supportProductPath(state);
        IntegerProperty ageProp = findAgeProperty(state);
        if (productPath == null || ageProp == null) return List.of();
        boolean lattice = isLattice(state);
        int resetAge = lattice ? 1 : 2;
        BlockState reset = state.setValue(ageProp, resetAge);
        level.setBlock(pos, reset, lattice ? Block.UPDATE_ALL : Block.UPDATE_CLIENTS);
        if (lattice) syncLatticeEntity(level, pos, reset, resetAge, null);

        List<ItemStack> drops = new ArrayList<>();
        BuiltInRegistries.ITEM.getOptional(vineryId(productPath)).ifPresent(product ->
                drops.add(new ItemStack(product, 3 + level.random.nextInt(2))));
        return drops;
    }

    @Override
    public boolean isColumnBlock(BlockState state) {
        return isSupport(state.getBlock().builtInRegistryHolder().key().location());
    }

    @Override
    public Item columnProduct(BlockState state) {
        if (!isColumnBlock(state)) return null;
        String productPath = supportProductPath(state);
        return productPath == null ? null : BuiltInRegistries.ITEM.getOptional(vineryId(productPath)).orElse(null);
    }

    @Override
    public boolean isBareSupport(ServerLevel level, BlockPos pos, BlockState state) {
        if (!isColumnBlock(state)) return false;
        IntegerProperty ageProp = findAgeProperty(state);
        if (ageProp == null || state.getValue(ageProp) != 0) return false;
        if (isLattice(state)) return true;
        // The bottom stem of a pole is the trunk. Vinery only plants a stem that has a stem below.
        return level.getBlockState(pos.below()).getBlock() == state.getBlock();
    }

    @Override
    public boolean canPlantOnSupport(ServerLevel level, BlockPos pos, BlockState state, ItemStack seed) {
        return isBareSupport(level, pos, state) && plantedSupport(state, seed) != null;
    }

    @Override
    public boolean plantOnSupport(ServerLevel level, BlockPos pos, BlockState state, ItemStack seed) {
        if (!isBareSupport(level, pos, state)) return false;
        BlockState planted = plantedSupport(state, seed);
        if (planted == null || !level.setBlock(pos, planted, Block.UPDATE_ALL)) return false;
        if (isLattice(planted)) syncLatticeEntity(level, pos, planted, 1, grapeTypeOf(seed));
        return true;
    }

    @Override
    public boolean providesTrellis() { return true; }

    @Override
    public Item trellisIcon() {
        return BuiltInRegistries.ITEM.getOptional(vineryId(STEM_PATH)).orElse(null);
    }

    @Override
    public boolean growsOnTrellis(ItemStack seed) { return grapeTypeOf(seed) != null; }

    @Override
    public boolean isTrellisSupportItem(ItemStack stack, ItemStack seed) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
        return isTrellisSupportBlock(blockItem.getBlock().defaultBlockState(), seed);
    }

    /** Jungle grape seeds need a lattice. Every other seed, and a cell with no specific seed, gets a stem pole. */
    @Override
    public boolean isTrellisSupportBlock(BlockState state, ItemStack seed) {
        if (!isColumnBlock(state)) return false;
        return isLattice(state) == LATTICE_GRAPE_TYPES.contains(String.valueOf(grapeTypeOf(seed)));
    }

    @Override
    public boolean placeTrellisSupport(ServerLevel level, BlockPos pos, ItemStack supportItem, TrellisSpec spec) {
        if (!(supportItem.getItem() instanceof BlockItem blockItem)) return false;
        BlockState state = blockItem.getBlock().defaultBlockState();
        if (!isColumnBlock(state)) return false;
        if (isLattice(state)) {
            StateDefinition<?, ?> definition = state.getBlock().getStateDefinition();
            if (definition.getProperty("facing") instanceof DirectionProperty facing
                    && facing.getPossibleValues().contains(spec.facing().direction())) {
                state = state.setValue(facing, spec.facing().direction());
            }
            if (definition.getProperty("bottom") instanceof BooleanProperty bottom) {
                state = state.setValue(bottom, spec.flat());
            }
            // Lets the panel join the panels beside it the way a player placement does.
            state = Block.updateFromNeighbourShapes(state, level, pos);
        }
        if (!state.canSurvive(level, pos)) return false;
        return level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    @Nullable
    private static BlockState plantedSupport(BlockState state, ItemStack seed) {
        String grapeType = grapeTypeOf(seed);
        IntegerProperty ageProp = findAgeProperty(state);
        Property<?> grapeProp = state.getBlock().getStateDefinition().getProperty("grape");
        if (grapeType == null || ageProp == null || grapeProp == null) return null;
        if (isLattice(state) != LATTICE_GRAPE_TYPES.contains(grapeType)) return null;
        BlockState withGrape = withNamedValue(state, grapeProp, grapeType);
        return withGrape == null ? null : withGrape.setValue(ageProp, 1);
    }

    /**
     * A lattice keeps a copy of its age and grape in a block entity, which Vinery's renderer reads.
     * Written through NBT because the entity class belongs to Vinery.
     */
    private static void syncLatticeEntity(ServerLevel level, BlockPos pos, BlockState state, int age, @Nullable String grapeType) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null) return;
        //? if >=1.21 {
        CompoundTag tag = entity.saveWithoutMetadata(level.registryAccess());
        //?} else {
        /*CompoundTag tag = entity.saveWithoutMetadata();
        *///?}
        tag.putInt("Age", age);
        if (grapeType != null) tag.putString("Grape", grapeType);
        //? if >=1.21 {
        entity.loadWithComponents(tag, level.registryAccess());
        //?} else {
        /*entity.load(tag);
        *///?}
        entity.setChanged();
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
    }

    @Nullable
    private static String grapeTypeOf(ItemStack seed) {
        if (seed == null || seed.isEmpty()) return null;
        ResourceLocation seedKey = seed.getItem().builtInRegistryHolder().key().location();
        return MOD_ID.equals(seedKey.getNamespace()) ? SEED_TO_GRAPE_TYPE.get(seedKey.getPath()) : null;
    }

    private static boolean isSupport(ResourceLocation blockKey) {
        return MOD_ID.equals(blockKey.getNamespace())
                && (STEM_PATH.equals(blockKey.getPath()) || blockKey.getPath().endsWith(LATTICE_SUFFIX));
    }

    private static boolean isLattice(BlockState state) {
        ResourceLocation key = state.getBlock().builtInRegistryHolder().key().location();
        return MOD_ID.equals(key.getNamespace()) && key.getPath().endsWith(LATTICE_SUFFIX);
    }

    @Nullable
    private static String supportProductPath(BlockState state) {
        Property<?> grapeProp = state.getBlock().getStateDefinition().getProperty("grape");
        if (grapeProp == null) return null;
        return GRAPE_TYPE_TO_PRODUCT.get(namedValue(state, grapeProp));
    }

    private static <T extends Comparable<T>> String namedValue(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    @Nullable
    private static <T extends Comparable<T>> BlockState withNamedValue(BlockState state, Property<T> property, String name) {
        return property.getValue(name).map(value -> state.setValue(property, value)).orElse(null);
    }

    private static ResourceLocation vineryId(String path) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
        //?} else {
        /*return new ResourceLocation(MOD_ID, path);
        *///?}
    }

    @Override
    public boolean isExistingFarmSoil(ServerLevel level, BlockPos pos) { return false; }

    @Override
    public boolean isPlantableSpot(ServerLevel level, BlockPos pos) { return false; }

    private static IntegerProperty findAgeProperty(BlockState state) {
        StateDefinition<?, ?> definition = state.getBlock().getStateDefinition();
        Property<?> property = definition.getProperty("age");
        if (property instanceof IntegerProperty integerProperty) return integerProperty;
        return null;
    }
}
