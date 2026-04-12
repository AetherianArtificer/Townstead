package com.aetherianartificer.townstead.client.gui.fieldpost;

import com.aetherianartificer.townstead.block.CropDetection;
import com.aetherianartificer.townstead.block.FieldPostBlockEntity;
import com.aetherianartificer.townstead.farming.FieldPostConfigSetPayload;
import com.aetherianartificer.townstead.farming.cellplan.CellPlan;
import com.aetherianartificer.townstead.farming.cellplan.FieldPostConfig;
import com.aetherianartificer.townstead.farming.cellplan.SeedAssignment;
import com.aetherianartificer.townstead.farming.cellplan.SoilType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}

import java.util.*;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Field Post Plot Planner — wooden frame editor with textured cells, toolbar modes, and native palette.
 */
public class FieldPostScreen extends Screen {

    // ── Zoom levels (cell size) ──
    private static final int[] ZOOM_LEVELS = {16, 20, 28, 36};
    private int zoomIndex = 1;
    private int cellSize() { return ZOOM_LEVELS[zoomIndex]; }
    private int stride() { return cellSize() + 1; }

    // ── Layout ──
    private static final int SPACING = 10;       // consistent gap between edges, panels, sections
    private static final int FRAME_THICK = 6;
    private static final int PALETTE_W = 136;
    private static final int TITLE_H = 16; // space above the frames for the confirm/cancel buttons
    private static final int SEARCH_H = 16;
    private static final int TOOLBAR_H = 22;
    // STATUS_H removed — status bar was dropped per user request

    // ── Colors ──
    private static final int TEXT_LIGHT = 0xFFF0E6CF;
    private static final int TEXT_DIM = 0xFFA89A80;
    private static final int ACCENT = 0xFF88DD44;
    private static final int PLAN_BORDER = 0xFFFFCC00;
    private static final int CENTER_MARKER = 0xFFFF8844;
    private static final int BG_DARK = 0xFF0F0A05;

    // ── Modes ──
    private enum Mode {
        PAINT("townstead.field_post.mode.paint", "B", Items.WOODEN_HOE),
        PAN("townstead.field_post.mode.pan", "H", Items.COMPASS),
        ERASE("townstead.field_post.mode.erase", "E", Items.BARRIER);
        final String translationKey;
        final String shortcut;
        final Item icon;
        Mode(String k, String s, Item i) { this.translationKey = k; this.shortcut = s; this.icon = i; }
        String label() { return Component.translatable(translationKey).getString(); }
    }
    private Mode mode = Mode.PAINT;

    // ── Cell flags (special case markers) ──
    private static final byte CELL_NORMAL = 0;
    private static final byte CELL_POST = 1;
    private static final byte CELL_CROP_MATURE = 2;
    private static final byte CELL_AIR = 3;

    // ── Palette tabs ──
    private enum PaletteTab { SEEDS, SOIL }
    private PaletteTab activeTab = PaletteTab.SEEDS;
    private ToolPaletteList.ToolEntry lastSeedSelection;
    private ToolPaletteList.ToolEntry lastSoilSelection;

    // ── State ──
    private final BlockPos postPos;
    private final Level level;
    private int gridSize;
    private BlockState[][] renderStates;
    private BlockPos[][] renderPositions;
    private byte[][] cellFlags;
    private ItemStack[][] cropIcons;
    // Two-layer plan: soil type and seed assignment
    private final Map<Integer, SoilType> soilPlan = new HashMap<>();
    private final Map<Integer, String> seedPlan = new HashMap<>();

    // Undo/redo stacks
    private record UndoEntry(int key, PaletteTab tab, SoilType prevSoil, String prevSeed) {}
    private final Deque<UndoEntry> undoStack = new ArrayDeque<>();
    private final Deque<UndoEntry> redoStack = new ArrayDeque<>();
    private static final int MAX_UNDO = 500;

    // Widgets
    private EditBox searchBox;
    private ToolPaletteList paletteList;
    private final List<ToolPaletteList.ToolEntry> allSeedEntries = new ArrayList<>();
    private final List<ToolPaletteList.ToolEntry> allSoilEntries = new ArrayList<>();

    // Viewport
    private int viewCols, viewRows;
    private float scrollX, scrollY;              // current (rendered) position
    private float targetScrollX, targetScrollY;  // target (smooth lerp)
    private int vpLeft, vpTop, vpW, vpH;
    private int toolbarLeft, toolbarTop;

    // Drag
    private boolean panning = false;
    private double panStartX, panStartY;
    private float panStartScrollX, panStartScrollY;

    // Config (loaded from block entity, sent back on apply)
    private FieldPostConfig loadedConfig = FieldPostConfig.defaults();

    public FieldPostScreen(BlockPos pos, Level level) {
        super(Component.translatable("container.townstead.field_post"));
        this.postPos = pos;
        this.level = level;
    }

    @Override
    protected void init() {
        super.init();

        if (level.getBlockEntity(postPos) instanceof FieldPostBlockEntity be) {
            loadedConfig = be.toConfig();
            CellPlan plan = loadedConfig.cellPlan();
            soilPlan.clear();
            soilPlan.putAll(plan.soilPlan());
            seedPlan.clear();
            seedPlan.putAll(plan.seedPlan());
        }

        gridSize = loadedConfig.radius() * 2 + 1;

        // Layout — consistent SPACING gutters everywhere (screen edges + between panels)
        int palLeft = SPACING + FRAME_THICK;
        int palTop = SPACING + FRAME_THICK + TITLE_H;

        // Viewport frame wraps toolbar + grid + status bar (shared full-height frame, matches palette)
        vpLeft = palLeft + PALETTE_W + FRAME_THICK + SPACING + FRAME_THICK;
        vpW = width - vpLeft - SPACING - FRAME_THICK;
        // Full frame matches the palette frame height exactly
        int vpFrameTop = palTop;
        int vpFrameH = height - vpFrameTop - SPACING - FRAME_THICK;
        // Grid sits below the toolbar, inside the frame
        vpTop = vpFrameTop + TOOLBAR_H;
        vpH = vpFrameH - TOOLBAR_H;
        toolbarLeft = vpLeft;
        toolbarTop = vpFrameTop;

        recomputeViewport();

        // Search box — aligned with toolbar top
        int searchPad = 4;
        searchBox = new EditBox(font, palLeft + searchPad, palTop + 3,
                PALETTE_W - searchPad * 2, SEARCH_H - 4,
                Component.literal("Search"));
        searchBox.setMaxLength(64);
        searchBox.setBordered(true);
        searchBox.setHint(Component.translatable("townstead.field_post.search.hint"));
        searchBox.setResponder(text -> filterPalette());
        addRenderableWidget(searchBox);

        buildToolEntries();

        // Tool list (below search box + tab buttons)
        int tabsHeight = SEARCH_H + 4 + 16 + 2; // search + gap + tabs + separator
        int listTop = palTop + tabsHeight;
        int listH = height - listTop - SPACING - FRAME_THICK;
        paletteList = new ToolPaletteList(minecraft, palLeft, PALETTE_W, listH, listTop, entry -> {});
        paletteList.setOnHeaderClick(category -> {
            paletteList.toggleCategory(category);
            filterPalette();
        });
        filterPalette();
        addRenderableWidget(paletteList);

        // Center scroll on post
        targetScrollX = scrollX = Math.max(0, gridSize / 2.0f - viewCols / 2.0f);
        targetScrollY = scrollY = Math.max(0, gridSize / 2.0f - viewRows / 2.0f);
        clampScroll();

        scanWorld();
    }

    private void recomputeViewport() {
        viewCols = vpW / stride();
        viewRows = vpH / stride();
    }

    // Entries grouped by category key (per active tab)
    private final LinkedHashMap<String, List<ToolPaletteList.ToolEntry>> entriesByCategory = new LinkedHashMap<>();
    private final String CAT_TOOLS = Component.translatable("townstead.field_post.category.tools").getString();
    private final String CAT_VANILLA = Component.translatable("townstead.field_post.category.vanilla").getString();

    private void buildToolEntries() {
        allSeedEntries.clear();
        allSoilEntries.clear();

        // ── Seed tab entries ──
        //? if >=1.21 {
        ResourceLocation autoIcon = ResourceLocation.fromNamespaceAndPath("townstead", "textures/gui/icon_auto.png");
        //?} else {
        /*ResourceLocation autoIcon = new ResourceLocation("townstead", "textures/gui/icon_auto.png");
        *///?}
        allSeedEntries.add(new ToolPaletteList.ToolEntry(SeedAssignment.AUTO, Component.translatable("townstead.field_post.seed.auto").getString(), autoIcon, CAT_TOOLS));
        allSeedEntries.add(new ToolPaletteList.ToolEntry(SeedAssignment.NONE, Component.translatable("townstead.field_post.seed.none").getString(), new ItemStack(Items.BARRIER), CAT_TOOLS));
        allSeedEntries.add(new ToolPaletteList.ToolEntry(SeedAssignment.PROTECTED, Component.translatable("townstead.field_post.seed.protected").getString(), new ItemStack(Items.SHIELD), CAT_TOOLS));

        for (String seedId : CropDetection.getAllPlantableSeeds()) {
            //? if >=1.21 {
            ResourceLocation rl = ResourceLocation.parse(seedId);
            //?} else {
            /*ResourceLocation rl = new ResourceLocation(seedId);
            *///?}
            Item seedItem = BuiltInRegistries.ITEM.get(rl);
            if (seedItem == Items.AIR) continue;

            // Use server palette if available, otherwise fall back to client-side heuristic
            ItemStack cropProduct;
            if (serverCropPalette != null && serverCropPalette.containsKey(seedId)) {
                //? if >=1.21 {
                ResourceLocation productRl = ResourceLocation.parse(serverCropPalette.get(seedId));
                //?} else {
                /*ResourceLocation productRl = new ResourceLocation(serverCropPalette.get(seedId));
                *///?}
                Item productItem = BuiltInRegistries.ITEM.get(productRl);
                cropProduct = productItem != Items.AIR ? new ItemStack(productItem) : seedToCropIcon(seedItem);
            } else {
                cropProduct = seedToCropIcon(seedItem);
            }

            String name = cropProduct.getHoverName().getString();
            String category = categoryFor(rl.getNamespace());
            allSeedEntries.add(new ToolPaletteList.ToolEntry(seedId, name, cropProduct, category));
        }

        // ── Soil tab entries ──
        allSoilEntries.add(new ToolPaletteList.ToolEntry("FARMLAND", Component.translatable("townstead.field_post.soil.farmland").getString(), new ItemStack(Items.FARMLAND), CAT_TOOLS));
        allSoilEntries.add(new ToolPaletteList.ToolEntry("WATER", Component.translatable("townstead.field_post.soil.water").getString(), new ItemStack(Items.WATER_BUCKET), CAT_TOOLS));
        allSoilEntries.add(new ToolPaletteList.ToolEntry("NONE", Component.translatable("townstead.field_post.soil.none").getString(), new ItemStack(Items.BARRIER), CAT_TOOLS));
        allSoilEntries.add(new ToolPaletteList.ToolEntry("PROTECTED", Component.translatable("townstead.field_post.soil.protected").getString(), new ItemStack(Items.SHIELD), CAT_TOOLS));
        // Rich soil (only if FD is loaded)
        if (com.aetherianartificer.townstead.compat.ModCompat.isLoaded("farmersdelight")) {
            //? if >=1.21 {
            ResourceLocation richSoilId = ResourceLocation.fromNamespaceAndPath("farmersdelight", "rich_soil");
            //?} else {
            /*ResourceLocation richSoilId = new ResourceLocation("farmersdelight", "rich_soil");
            *///?}
            Item richSoilItem = BuiltInRegistries.ITEM.get(richSoilId);
            if (richSoilItem != Items.AIR) {
                allSoilEntries.add(new ToolPaletteList.ToolEntry("RICH_SOIL",
                        Component.translatable("townstead.field_post.soil.rich_soil").getString(),
                        new ItemStack(richSoilItem), categoryFor("farmersdelight")));
            }
        }
    }

    private List<ToolPaletteList.ToolEntry> activeEntries() {
        return activeTab == PaletteTab.SEEDS ? allSeedEntries : allSoilEntries;
    }

    private String categoryFor(String namespace) {
        return switch (namespace) {
            case "minecraft" -> CAT_VANILLA;
            case "farmersdelight" -> "Farmer's Delight";
            case "croptopia" -> "Croptopia";
            case "pamhc2crops", "pamhc2trees" -> "HarvestCraft";
            case "twilightforest" -> "Twilight Forest";
            case "byg" -> "Oh The Biomes You'll Go";
            case "biomesoplenty" -> "Biomes O' Plenty";
            case "rusticdelight" -> "Rustic Delight";
            default -> titleCase(namespace);
        };
    }

    private String titleCase(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = true;
        for (char c : s.toCharArray()) {
            if (c == '_' || c == '-') {
                sb.append(' ');
                nextUpper = true;
            } else if (nextUpper) {
                sb.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private void filterPalette() {
        if (paletteList == null) return;
        String query = searchBox != null ? searchBox.getValue().toLowerCase(Locale.ROOT) : "";

        // Rebuild category groupings from the active tab's entries
        entriesByCategory.clear();
        for (ToolPaletteList.ToolEntry e : activeEntries()) {
            entriesByCategory.computeIfAbsent(e.categoryKey, k -> new ArrayList<>()).add(e);
        }
        // Sort within categories
        for (List<ToolPaletteList.ToolEntry> entries : entriesByCategory.values()) {
            entries.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        }

        List<ToolPaletteList.ToolEntry> filtered = new ArrayList<>();
        // Fixed category order: Tools, Vanilla, then alphabetical mod names
        List<String> categoryOrder = new ArrayList<>();
        if (entriesByCategory.containsKey(CAT_TOOLS)) categoryOrder.add(CAT_TOOLS);
        if (entriesByCategory.containsKey(CAT_VANILLA)) categoryOrder.add(CAT_VANILLA);
        List<String> others = new ArrayList<>();
        for (String cat : entriesByCategory.keySet()) {
            if (!CAT_TOOLS.equals(cat) && !CAT_VANILLA.equals(cat)) others.add(cat);
        }
        others.sort(String::compareToIgnoreCase);
        categoryOrder.addAll(others);

        for (String category : categoryOrder) {
            List<ToolPaletteList.ToolEntry> items = entriesByCategory.get(category);
            List<ToolPaletteList.ToolEntry> matching = new ArrayList<>();
            for (ToolPaletteList.ToolEntry e : items) {
                if (query.isEmpty()
                        || e.label.toLowerCase(Locale.ROOT).contains(query)
                        || e.toolId.contains(query)) {
                    matching.add(e);
                }
            }
            if (matching.isEmpty()) continue;

            ToolPaletteList.ToolEntry header = ToolPaletteList.ToolEntry.header(category, matching.size());
            filtered.add(header);

            boolean forceExpand = !query.isEmpty();
            if (forceExpand || !paletteList.isCategoryCollapsed(category)) {
                filtered.addAll(matching);
            }
        }

        // Restore previous selection for this tab
        ToolPaletteList.ToolEntry prev = paletteList.getSelected();
        if (prev == null) {
            prev = activeTab == PaletteTab.SEEDS ? lastSeedSelection : lastSoilSelection;
        }
        paletteList.replaceEntries(filtered);
        if (prev != null && !prev.isHeader && filtered.contains(prev)) {
            paletteList.setSelected(prev);
        }
        paletteList.setScrollAmount(paletteList.getScrollAmount());

        refreshCounts();
    }

    private void switchTab(PaletteTab tab) {
        if (tab == activeTab) return;
        // Save current selection
        ToolPaletteList.ToolEntry current = paletteList != null ? paletteList.getSelected() : null;
        if (activeTab == PaletteTab.SEEDS) lastSeedSelection = current;
        else lastSoilSelection = current;
        activeTab = tab;
        filterPalette();
    }

    private void scanWorld() {
        renderStates = new BlockState[gridSize][gridSize];
        renderPositions = new BlockPos[gridSize][gridSize];
        cellFlags = new byte[gridSize][gridSize];
        cropIcons = new ItemStack[gridSize][gridSize];
        int half = gridSize / 2;
        int baseY = postPos.getY();

        for (int gz = 0; gz < gridSize; gz++) {
            for (int gx = 0; gx < gridSize; gx++) {
                int wx = postPos.getX() + (gx - half);
                int wz = postPos.getZ() + (gz - half);

                // Field post marker
                if (gx == half && gz == half) {
                    cellFlags[gz][gx] = CELL_POST;
                    BlockPos below = new BlockPos(wx, baseY - 1, wz);
                    renderStates[gz][gx] = level.getBlockState(below);
                    renderPositions[gz][gx] = below;
                    continue;
                }

                // Smart per-column scan: find the farm surface
                // Pass 1: farmland (always wins — this is a farm planner)
                // Pass 2: any solid block (barrels, composters show as context landmarks)
                //         but skip trees, crops, fluids
                BlockPos groundPos = null;
                BlockState groundState = null;

                for (int dy = 3; dy >= -3; dy--) {
                    BlockPos candidate = new BlockPos(wx, baseY + dy, wz);
                    BlockState state = level.getBlockState(candidate);
                    if (state.getBlock() instanceof FarmBlock) {
                        groundPos = candidate; groundState = state; break;
                    }
                }
                if (groundPos == null) {
                    for (int dy = 3; dy >= -3; dy--) {
                        BlockPos candidate = new BlockPos(wx, baseY + dy, wz);
                        BlockState state = level.getBlockState(candidate);
                        if (state.isAir()) continue;
                        if (state.getBlock() instanceof CropBlock) continue;
                        if (state.getBlock() instanceof BushBlock) continue;
                        if (state.getBlock() instanceof LeavesBlock) continue;
                        if (state.is(BlockTags.LOGS)) continue;
                        if (state.getFluidState().is(Fluids.WATER) || state.getFluidState().is(Fluids.LAVA)) continue;
                        groundPos = candidate; groundState = state; break;
                    }
                }
                if (groundPos == null) {
                    for (int dy = 3; dy >= -3; dy--) {
                        BlockPos candidate = new BlockPos(wx, baseY + dy, wz);
                        BlockState state = level.getBlockState(candidate);
                        if (state.getFluidState().is(Fluids.WATER)) {
                            groundPos = candidate; groundState = state; break;
                        }
                    }
                }

                if (groundPos == null) {
                    cellFlags[gz][gx] = CELL_AIR;
                    renderStates[gz][gx] = null;
                    renderPositions[gz][gx] = new BlockPos(wx, baseY - 1, wz);
                    continue;
                }

                // Check one block above the ground for crops/water/plants
                BlockPos abovePos = groundPos.above();
                BlockState aboveState = level.getBlockState(abovePos);

                if (aboveState.getFluidState().is(Fluids.WATER)) {
                    // Water above ground — the cell IS water (not "dirt with water overlay")
                    renderStates[gz][gx] = aboveState;
                    renderPositions[gz][gx] = abovePos;
                } else {
                    renderStates[gz][gx] = groundState;
                    renderPositions[gz][gx] = groundPos;

                    if (aboveState.getBlock() instanceof CropBlock crop) {
                        if (crop.isMaxAge(aboveState)) cellFlags[gz][gx] = CELL_CROP_MATURE;
                        cropIcons[gz][gx] = cropIcon(aboveState.getBlock());
                    } else if (aboveState.getBlock() instanceof BushBlock) {
                        cropIcons[gz][gx] = cropIcon(aboveState.getBlock());
                    }
                }
            }
        }
    }

    /**
     * Chat-style panel background color using the player's textBackgroundOpacity accessibility setting.
     */
    private int chatPanelColor() {
        double opacity = minecraft.options.textBackgroundOpacity().get();
        int alpha = (int) (opacity * 255.0) & 0xFF;
        return (alpha << 24);
    }

    private boolean isNaturalGround(BlockState state) {
        if (state.isAir()) return false;
        // Dirt family
        if (state.is(BlockTags.DIRT)) return true;
        // Sand, gravel
        Block block = state.getBlock();
        if (block == Blocks.SAND || block == Blocks.RED_SAND || block == Blocks.GRAVEL) return true;
        // Clay, mud
        if (block == Blocks.CLAY || block == Blocks.MUD || block == Blocks.MUDDY_MANGROVE_ROOTS) return true;
        // Stone variants
        if (state.is(BlockTags.BASE_STONE_OVERWORLD)) return true;
        if (block == Blocks.STONE || block == Blocks.COBBLESTONE || block == Blocks.MOSSY_COBBLESTONE) return true;
        if (block == Blocks.DEEPSLATE || block == Blocks.COBBLED_DEEPSLATE) return true;
        // Nether terrain
        if (block == Blocks.SOUL_SAND || block == Blocks.SOUL_SOIL || block == Blocks.NETHERRACK) return true;
        if (block == Blocks.BASALT || block == Blocks.SMOOTH_BASALT || block == Blocks.BLACKSTONE) return true;
        // End terrain
        if (block == Blocks.END_STONE) return true;
        // Path
        if (block == Blocks.DIRT_PATH) return true;
        // Moss
        if (block == Blocks.MOSS_BLOCK) return true;
        // Snow
        if (block == Blocks.SNOW_BLOCK) return true;
        return false;
    }

    /**
     * Maps a seed item to its crop product for display in the palette.
     * Derives the crop from the seed's registry name, not from display names.
     * e.g., wheat_seeds → wheat, coffee_seeds → coffee_beans, beetroot_seeds → beetroot
     */
    private ItemStack seedToCropIcon(Item seedItem) {
        // Direct vanilla mappings
        if (seedItem == Items.WHEAT_SEEDS) return new ItemStack(Items.WHEAT);
        if (seedItem == Items.BEETROOT_SEEDS) return new ItemStack(Items.BEETROOT);
        if (seedItem == Items.MELON_SEEDS) return new ItemStack(Items.MELON);
        if (seedItem == Items.PUMPKIN_SEEDS) return new ItemStack(Items.PUMPKIN);
        if (seedItem == Items.CARROT || seedItem == Items.POTATO) return new ItemStack(seedItem);
        if (seedItem == Items.SWEET_BERRIES) return new ItemStack(Items.SWEET_BERRIES);

        // Derive crop product from the seed's registry name
        net.minecraft.resources.ResourceLocation seedKey = BuiltInRegistries.ITEM.getKey(seedItem);
        if (seedKey != null) {
            String ns = seedKey.getNamespace();
            String path = seedKey.getPath();

            // Strip common seed suffixes to get the base crop name
            String baseName = path;
            if (baseName.endsWith("_seeds")) baseName = baseName.substring(0, baseName.length() - 6);
            else if (baseName.endsWith("_seed")) baseName = baseName.substring(0, baseName.length() - 5);

            // Try various crop item name patterns in the same namespace
            String[] candidates = {
                    baseName,                // coffee_seeds → coffee
                    baseName + "_beans",     // coffee_seeds → coffee_beans
                    baseName + "_fruit",     // X_seeds → X_fruit
                    baseName + "_berry",     // X_seeds → X_berry
                    baseName + "_berries",   // X_seeds → X_berries
            };
            for (String candidate : candidates) {
                //? if >=1.21 {
                net.minecraft.resources.ResourceLocation cropId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ns, candidate);
                //?} else {
                /*net.minecraft.resources.ResourceLocation cropId = new net.minecraft.resources.ResourceLocation(ns, candidate);
                *///?}
                Item cropItem = BuiltInRegistries.ITEM.get(cropId);
                if (cropItem != Items.AIR && cropItem != seedItem) return new ItemStack(cropItem);
            }
        }

        // Try via the block the seed places (uses cropIcon's block-name patterns)
        if (seedItem instanceof net.minecraft.world.item.BlockItem blockItem) {
            ItemStack cropResult = cropIcon(blockItem.getBlock());
            if (!cropResult.isEmpty() && cropResult.getItem() != seedItem) return cropResult;
        }

        // Fallback: show the seed itself
        return new ItemStack(seedItem);
    }

    private ItemStack cropIcon(Block block) {
        // Show the harvested product, not the seed
        if (block == Blocks.WHEAT) return new ItemStack(Items.WHEAT);
        if (block == Blocks.CARROTS) return new ItemStack(Items.CARROT);
        if (block == Blocks.POTATOES) return new ItemStack(Items.POTATO);
        if (block == Blocks.BEETROOTS) return new ItemStack(Items.BEETROOT);
        if (block == Blocks.MELON_STEM) return new ItemStack(Items.MELON);
        if (block == Blocks.PUMPKIN_STEM) return new ItemStack(Items.PUMPKIN);
        if (block == Blocks.SWEET_BERRY_BUSH) return new ItemStack(Items.SWEET_BERRIES);

        // For modded crops: try to find the crop item by registry key
        net.minecraft.resources.ResourceLocation blockKey = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
        if (blockKey != null) {
            String ns = blockKey.getNamespace();
            String blockPath = blockKey.getPath();
            // Try several common naming patterns that mods use
            String[] candidates = {
                    blockPath,                                              // exact match
                    blockPath.endsWith("s") ? blockPath.substring(0, blockPath.length() - 1) : null,  // cabbages→cabbage
                    blockPath.endsWith("es") ? blockPath.substring(0, blockPath.length() - 2) : null, // tomatoes→tomato (if 's' strip didn't work)
                    blockPath.endsWith("_crop") ? blockPath.substring(0, blockPath.length() - 5) : null, // X_crop→X
                    blockPath.startsWith("budding_") ? blockPath.substring(8) : null,                 // budding_X→X
                    blockPath.startsWith("budding_") && blockPath.endsWith("s")
                            ? blockPath.substring(8, blockPath.length() - 1) : null,                  // budding_tomatoes→tomato
            };
            for (String candidate : candidates) {
                if (candidate == null || candidate.isEmpty()) continue;
                //? if >=1.21 {
                net.minecraft.resources.ResourceLocation cropId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ns, candidate);
                //?} else {
                /*net.minecraft.resources.ResourceLocation cropId = new net.minecraft.resources.ResourceLocation(ns, candidate);
                *///?}
                Item cropItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(cropId);
                if (cropItem != Items.AIR && cropItem != block.asItem()) return new ItemStack(cropItem);
            }
        }

        // Fallback: the block's own item (may be a seed for some mods)
        Item item = block.asItem();
        if (item != Items.AIR) return new ItemStack(item);
        return ItemStack.EMPTY;
    }

    // No tick-based lerp — smooth panning runs at full render framerate in render()

    // ── Render ──

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // Smooth pan lerp at full render framerate
        if (!panning) {
            float lerp = 1.0f - (float) Math.pow(0.001, partial / 20.0f); // frame-independent smoothing
            scrollX += (targetScrollX - scrollX) * lerp;
            scrollY += (targetScrollY - scrollY) * lerp;
        }

        // Semi-transparent background so the game world shows through
        double opacity = minecraft.options.textBackgroundOpacity().get();
        // Map 0.0..1.0 -> 0x40..0xC0 alpha (never fully opaque, never fully transparent)
        int alpha = 0x40 + (int) (opacity * (0xC0 - 0x40));
        int bgColor = (alpha << 24) | 0x0A0705;
        g.fill(0, 0, width, height, bgColor);

        // Confirm / cancel icons — right side aligned with the map frame's right edge
        int btnSize = 14;
        int frameRight = vpLeft + vpW + FRAME_THICK;
        int cancelX = frameRight - btnSize;
        int checkX = cancelX - btnSize - 4;
        int btnY = SPACING - 2;
        boolean hCheck = mouseX >= checkX && mouseX < checkX + btnSize && mouseY >= btnY && mouseY < btnY + btnSize;
        boolean hCancel = mouseX >= cancelX && mouseX < cancelX + btnSize && mouseY >= btnY && mouseY < btnY + btnSize;
        g.fill(checkX - 1, btnY - 1, checkX + btnSize + 1, btnY + btnSize + 1, FrameRenderer.FRAME_SHADOW);
        g.fill(checkX, btnY, checkX + btnSize, btnY + btnSize, hCheck ? 0xFF66CC44 : 0xFF3D7A22);
        g.drawCenteredString(font, "\u2713", checkX + btnSize / 2, btnY + 3, 0xFFFFFFFF);
        g.fill(cancelX - 1, btnY - 1, cancelX + btnSize + 1, btnY + btnSize + 1, FrameRenderer.FRAME_SHADOW);
        g.fill(cancelX, btnY, cancelX + btnSize, btnY + btnSize, hCancel ? 0xFFCC4444 : 0xFF7A2222);
        g.drawCenteredString(font, "\u2717", cancelX + btnSize / 2, btnY + 3, 0xFFFFFFFF);

        // ── Palette panel ──
        int palLeft = SPACING + FRAME_THICK;
        int palTop = SPACING + FRAME_THICK + TITLE_H;
        int palH = height - palTop - SPACING - FRAME_THICK;
        FrameRenderer.drawWoodenFrame(g, palLeft, palTop, PALETTE_W, palH, FRAME_THICK);
        g.fill(palLeft, palTop, palLeft + PALETTE_W, palTop + palH, chatPanelColor());

        // ── Palette tabs (Crops | Soil) — full width, below search box ──
        int tabY = palTop + SEARCH_H + 4;
        int tabW = PALETTE_W / 2;
        for (int t = 0; t < 2; t++) {
            PaletteTab tab = t == 0 ? PaletteTab.SEEDS : PaletteTab.SOIL;
            int tx = palLeft + t * tabW;
            boolean active = tab == activeTab;
            boolean hoverTab = mouseX >= tx && mouseX < tx + tabW && mouseY >= tabY && mouseY < tabY + 14;
            // Vanilla button style
            int bodyColor = active ? 0xFF5A8A2A : (hoverTab ? 0xFF5A5A5A : 0xFF3A3A3A);
            g.fill(tx, tabY, tx + tabW, tabY + 14, bodyColor);
            g.fill(tx, tabY, tx + tabW, tabY + 1, active ? ACCENT : 0xFF555555);
            g.fill(tx, tabY + 13, tx + tabW, tabY + 14, 0xFF222222);
            String tabLabel = Component.translatable(t == 0 ? "townstead.field_post.tab.seeds" : "townstead.field_post.tab.soil").getString();
            g.drawCenteredString(font, tabLabel, tx + tabW / 2, tabY + 3,
                    active ? 0xFFFFFFFF : (hoverTab ? 0xFFDDDDDD : 0xFFAAAAAA));
        }
        // Separator below tabs
        g.fill(palLeft, tabY + 15, palLeft + PALETTE_W, tabY + 16, 0x40FFDEA0);

        // ── Grid viewport frame (wraps toolbar + grid + status bar) ──
        int vpFrameTop = toolbarTop;
        int vpFrameH = TOOLBAR_H + vpH;
        FrameRenderer.drawWoodenFrame(g, vpLeft, vpFrameTop, vpW, vpFrameH, FRAME_THICK);

        // ── Toolbar (inside the frame, at the top) ──
        renderToolbar(g, mouseX, mouseY);

        // ── Grid content (middle section) ──
        renderGrid(g, mouseX, mouseY);
        renderCardinalLabels(g);

        // Widgets (search box + palette list)
        super.render(g, mouseX, mouseY, partial);

        // Cell tooltip (after super so it renders on top)
        renderGridTooltip(g, mouseX, mouseY);
    }

    private void renderToolbar(GuiGraphics g, int mouseX, int mouseY) {
        int x = toolbarLeft;
        int y = toolbarTop;

        // Toolbar background strip — fills full height
        g.fill(x, y, x + vpW, y + TOOLBAR_H, chatPanelColor());

        // Mode buttons — flush left, centered vertically within full toolbar height
        Mode[] modes = Mode.values();
        int btnSize = 16;
        int btnGap = 2;
        int btnY = y + (TOOLBAR_H - btnSize) / 2;
        String hoveredLabel = null;
        int buttonsEndX = x + 2;
        for (int i = 0; i < modes.length; i++) {
            int bx = x + 2 + i * (btnSize + btnGap);
            Mode m = modes[i];
            boolean selected = m == mode;
            boolean hovered = mouseX >= bx && mouseX < bx + btnSize && mouseY >= btnY && mouseY < btnY + btnSize;

            g.fill(bx - 1, btnY - 1, bx + btnSize + 1, btnY + btnSize + 1, FrameRenderer.FRAME_SHADOW);
            g.fill(bx, btnY, bx + btnSize, btnY + btnSize, selected ? 0xFF5A8A2A : (hovered ? 0xFF3A3225 : 0xFF24201A));
            if (selected) {
                drawCellBorder(g, bx, btnY, btnSize, ACCENT);
            }

            // Scale item icon to fit within the button
            g.pose().pushPose();
            float iconScale = (btnSize - 2) / 16.0f;
            g.pose().translate(bx + 1, btnY + 1, 0);
            g.pose().scale(iconScale, iconScale, 1.0f);
            g.renderItem(new ItemStack(m.icon), 0, 0);
            g.pose().popPose();

            if (hovered) {
                hoveredLabel = m.label() + " (" + m.shortcut + ")";
            }
            buttonsEndX = bx + btnSize + btnGap;
        }

        // Separator
        g.fill(buttonsEndX + 2, y + 4, buttonsEndX + 3, y + TOOLBAR_H - 4, 0x40FFDEA0);

        // All text vertically centered at the same baseline
        int textY = y + (TOOLBAR_H - font.lineHeight) / 2 + 2;

        // Hover / active label
        int labelX = buttonsEndX + 8;
        if (hoveredLabel != null) {
            g.drawString(font, hoveredLabel, labelX, textY, TEXT_LIGHT, false);
        } else {
            g.drawString(font, mode.label() + " (" + mode.shortcut + ")", labelX, textY, TEXT_DIM, false);
        }

        // ── Right side: Zoom ──
        int smallBtn = 14;
        int rightBtnY = y + (TOOLBAR_H - smallBtn) / 2;

        int zoomPlusX = x + vpW - smallBtn - 4;
        int zoomMinusX = zoomPlusX - smallBtn - 2;
        drawSmallButton(g, zoomMinusX, rightBtnY, smallBtn, "-", mouseX, mouseY);
        drawSmallButton(g, zoomPlusX, rightBtnY, smallBtn, "+", mouseX, mouseY);
        String zoomLbl = Component.translatable("townstead.field_post.zoom").getString();
        g.drawString(font, zoomLbl, zoomMinusX - font.width(zoomLbl) - 4, textY, TEXT_DIM, false);
    }

    private void drawSmallButton(GuiGraphics g, int x, int y, int size, String label, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size;
        g.fill(x - 1, y - 1, x + size + 1, y + size + 1, FrameRenderer.FRAME_SHADOW);
        g.fill(x, y, x + size, y + size, hovered ? 0xFF4A4235 : 0xFF2A251A);
        g.drawCenteredString(font, label, x + size / 2, y + 3, TEXT_LIGHT);
    }

    private void renderGrid(GuiGraphics g, int mouseX, int mouseY) {
        int cs = cellSize();
        int st = stride();

        g.enableScissor(vpLeft, vpTop, vpLeft + vpW, vpTop + vpH);
        g.fill(vpLeft, vpTop, vpLeft + vpW, vpTop + vpH, 0xFF141210);

        int half = gridSize / 2;
        int startGX = (int) Math.floor(scrollX);
        int startGZ = (int) Math.floor(scrollY);
        // Round offset to integer pixels to prevent sub-pixel jitter
        int offsetX = Math.round(-(scrollX - startGX) * st);
        int offsetY = Math.round(-(scrollY - startGZ) * st);

        for (int vy = -1; vy <= viewRows + 1; vy++) {
            for (int vx = -1; vx <= viewCols + 1; vx++) {
                int gx = startGX + vx;
                int gz = startGZ + vy;
                if (gx < 0 || gz < 0 || gx >= gridSize || gz >= gridSize) continue;

                int cx = vpLeft + vx * st + offsetX;
                int cy = vpTop + vy * st + offsetY;
                if (cx + cs < vpLeft || cy + cs < vpTop || cx > vpLeft + vpW || cy > vpTop + vpH) continue;

                BlockState state = renderStates[gz][gx];
                BlockPos worldPos = renderPositions[gz][gx];
                byte flag = cellFlags[gz][gx];

                // Base color under the sprite for fallback
                g.fill(cx, cy, cx + cs, cy + cs, 0xFF1E1E1E);

                if (state != null && !state.isAir()) {
                    // Farmland: use known atlas texture directly (model lookup returns dirt particle)
                    if (state.getBlock() instanceof FarmBlock) {
                        boolean wet = state.getValue(FarmBlock.MOISTURE) > 0;
                        CellTextures.blit(g, wet ? "minecraft:block/farmland_moist" : "minecraft:block/farmland", cx, cy, cs);
                    } else if (state.getFluidState().is(Fluids.WATER)) {
                        // Water cell: use water texture with tint
                        CellTextures.blit(g, "minecraft:block/water_still", cx, cy, cs);
                        g.fill(cx, cy, cx + cs, cy + cs, 0x603F76E4);
                    } else {
                        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite =
                                BlockSpriteResolver.getTopSprite(state);
                        if (sprite != null) {
                            int tint = BlockSpriteResolver.getTint(state, level, worldPos);
                            float r = ((tint >> 16) & 0xFF) / 255f;
                            float gg = ((tint >> 8) & 0xFF) / 255f;
                            float b = (tint & 0xFF) / 255f;
                            g.setColor(r, gg, b, 1.0f);
                            g.blit(cx, cy, 0, cs, cs, sprite);
                            g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
                        }
                    }
                }

                // Existing crop icon (centered in cell)
                ItemStack icon = cropIcons[gz][gx];
                if (icon != null && !icon.isEmpty()) {
                    g.pose().pushPose();
                    float scale = cs / 16.0f * 0.7f;
                    float iconSize = 16 * scale;
                    float iconOff = (cs - iconSize) / 2.0f;
                    g.pose().translate(cx + iconOff, cy + iconOff, 100);
                    g.pose().scale(scale, scale, 1.0f);
                    g.renderItem(icon, 0, 0);
                    g.pose().popPose();
                }

                // Plan overlays — both layers
                int xOff = gx - half;
                int zOff = gz - half;
                int planKey = CellPlan.packXZ(xOff, zOff);

                // Soil plan: show as texture overlay so you can see what it'll become
                SoilType soilAssignment = soilPlan.get(planKey);
                if (soilAssignment != null) {
                    String soilTexture = switch (soilAssignment) {
                        case FARMLAND -> "minecraft:block/farmland";
                        case RICH_SOIL -> "minecraft:block/farmland_moist"; // visual approximation
                        case WATER -> "minecraft:block/water_still";
                        case NONE -> null;
                        case PROTECTED -> null;
                    };
                    if (soilTexture != null) {
                        // Render the planned soil texture as a semi-transparent overlay
                        g.setColor(1.0f, 1.0f, 1.0f, 0.6f);
                        CellTextures.blit(g, soilTexture, cx, cy, cs);
                        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
                        if (soilAssignment == SoilType.WATER) {
                            g.fill(cx, cy, cx + cs, cy + cs, 0x403F76E4);
                        }
                    }
                    int borderColor = switch (soilAssignment) {
                        case FARMLAND -> 0xFF8B6914;
                        case RICH_SOIL -> 0xFF4A2D0A;
                        case WATER -> 0xFF3366CC;
                        case NONE -> 0xFF666666;
                        case PROTECTED -> 0xFFFF4444;
                    };
                    drawCellBorder(g, cx, cy, cs, borderColor);
                }

                // Seed plan: show as a smaller centered icon
                String seedAssignment = seedPlan.get(planKey);
                if (seedAssignment != null) {
                    ToolPaletteList.ToolEntry tool = findToolEntry(seedAssignment);
                    if (tool != null) {
                        if (soilAssignment == null) drawCellBorder(g, cx, cy, cs, PLAN_BORDER);
                        float scale = cs / 16.0f * 0.65f;
                        float iconSize = 16 * scale;
                        float offXi = (cs - iconSize) / 2.0f;
                        float offYi = (cs - iconSize) / 2.0f;
                        g.pose().pushPose();
                        g.pose().translate(cx + offXi, cy + offYi, 200);
                        g.pose().scale(scale, scale, 1.0f);
                        if (tool.customIcon != null) {
                            //? if >=1.21 {
                            g.blit(tool.customIcon, 0, 0, 0, 0, 16, 16, 16, 16);
                            //?} else {
                            /*com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, tool.customIcon);
                            g.blit(tool.customIcon, 0, 0, 0, 0, 16, 16, 16, 16);
                            *///?}
                        } else {
                            g.renderItem(tool.icon, 0, 0);
                        }
                        g.pose().popPose();
                    }
                }

                // Growth stage dot (top-left corner of cell)
                if (cropIcons[gz][gx] != null && !cropIcons[gz][gx].isEmpty()) {
                    BlockPos gp = renderPositions[gz][gx];
                    BlockState cs2 = gp != null ? level.getBlockState(gp.above()) : null;
                    if (cs2 != null && cs2.getBlock() instanceof CropBlock crop2) {
                        int age = crop2.getAge(cs2);
                        int maxAge = crop2.getMaxAge();
                        int dotColor = age >= maxAge ? 0xFFFFDD44  // gold = mature
                                : (age * 100 / Math.max(1, maxAge) > 50 ? 0xFF55CC33 : 0xFFCC6622); // green or orange
                        int dotSize = Math.max(2, cs / 6);
                        g.fill(cx + 1, cy + 1, cx + 1 + dotSize, cy + 1 + dotSize, dotColor);
                    }
                }

                // Protected: red X across the cell
                boolean isProtected = soilPlan.get(planKey) == SoilType.PROTECTED
                        || SeedAssignment.PROTECTED.equals(seedPlan.get(planKey));
                if (isProtected) {
                    // Draw diagonal X lines
                    for (int d = 0; d < cs; d++) {
                        int px1 = cx + d, py1 = cy + d;
                        int px2 = cx + cs - 1 - d, py2 = cy + d;
                        if (px1 >= cx && px1 < cx + cs && py1 >= cy && py1 < cy + cs)
                            g.fill(px1, py1, px1 + 1, py1 + 1, 0xCCFF3333);
                        if (px2 >= cx && px2 < cx + cs && py2 >= cy && py2 < cy + cs)
                            g.fill(px2, py2, px2 + 1, py2 + 1, 0xCCFF3333);
                    }
                }

                // Center marker (field post)
                if (gx == half && gz == half) {
                    drawCellBorder(g, cx, cy, cs, CENTER_MARKER);
                    int midX = cx + cs / 2, midY = cy + cs / 2;
                    g.fill(midX - 2, cy + 3, midX + 2, cy + cs - 3, 0xCCFF8844);
                    g.fill(cx + 3, midY - 2, cx + cs - 3, midY + 2, 0xCCFF8844);
                }

                // Hover
                if (!panning && mouseX >= cx && mouseX < cx + cs && mouseY >= cy && mouseY < cy + cs
                        && mouseX >= vpLeft && mouseX < vpLeft + vpW && mouseY >= vpTop && mouseY < vpTop + vpH) {
                    g.fill(cx, cy, cx + cs, cy + cs, 0x40FFFFFF);
                    int borderColor = switch (mode) {
                        case PAINT -> ACCENT;
                        case PAN -> 0xFF6688FF;
                        case ERASE -> 0xFFFF6644;
                    };
                    drawCellBorder(g, cx, cy, cs, borderColor);
                }
            }
        }

        g.disableScissor();
    }

    private void renderCardinalLabels(GuiGraphics g) {
        // Rendered above all grid content with a Z push
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.enableScissor(vpLeft, vpTop, vpLeft + vpW, vpTop + vpH);
        int midX = vpLeft + vpW / 2;
        int midY = vpTop + vpH / 2;
        g.drawCenteredString(font, Component.translatable("townstead.field_post.direction.n").getString(), midX, vpTop + 2, TEXT_LIGHT);
        g.drawCenteredString(font, Component.translatable("townstead.field_post.direction.s").getString(), midX, vpTop + vpH - 10, TEXT_LIGHT);
        g.drawString(font, Component.translatable("townstead.field_post.direction.w").getString(), vpLeft + 2, midY - 4, TEXT_LIGHT, true);
        g.drawString(font, Component.translatable("townstead.field_post.direction.e").getString(), vpLeft + vpW - 8, midY - 4, TEXT_LIGHT, true);
        g.disableScissor();
        g.pose().popPose();
    }

    private void renderGridTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (panning || mouseX < vpLeft || mouseX >= vpLeft + vpW || mouseY < vpTop || mouseY >= vpTop + vpH) return;

        int[] cell = gridCellAt(mouseX, mouseY);
        if (cell == null) return;
        int gx = cell[0], gz = cell[1];
        BlockState state = renderStates[gz][gx];
        if (state == null || state.isAir()) return;

        int half = gridSize / 2;
        BlockPos groundPos = renderPositions[gz][gx];
        BlockPos cropPos = groundPos != null ? groundPos.above() : null;
        BlockState cropState = cropPos != null ? level.getBlockState(cropPos) : null;

        // ── Gather tooltip data ──
        String cropName = null;
        int cropAge = -1, cropMaxAge = -1;
        int nameColor = 0x77CC44;
        ItemStack cropItemIcon = null;

        if (cropState != null && cropState.getBlock() instanceof CropBlock crop) {
            cropItemIcon = cropIcon(crop);
            cropName = cropItemIcon.getHoverName().getString();
            cropAge = crop.getAge(cropState);
            cropMaxAge = crop.getMaxAge();
            int pct = cropMaxAge > 0 ? (cropAge * 100) / cropMaxAge : 0;
            nameColor = cropAge >= cropMaxAge ? 0xFFDD44 : (pct > 50 ? 0x77CC44 : 0xCCCC44);
        } else if (cropState != null && cropState.getBlock() instanceof BushBlock) {
            cropItemIcon = cropIcon(cropState.getBlock());
            cropName = cropItemIcon.getHoverName().getString();
        } else if (cropState != null && cropState.getFluidState().is(Fluids.WATER)) {
            cropName = Component.translatable("townstead.field_post.tooltip.water").getString();
            nameColor = 0x5599FF;
        }

        // Ground info
        String soilLabel = null;
        int soilColor = 0xAA9977;
        String hydrationLabel = null;
        int hydrationColor = 0;

        if (state.getFluidState().is(Fluids.WATER)) {
            soilLabel = Component.translatable("townstead.field_post.tooltip.water").getString();
            soilColor = 0x5599FF;
        } else if (state.getBlock() instanceof FarmBlock) {
            boolean wet = state.getValue(FarmBlock.MOISTURE) > 0;
            soilLabel = Component.translatable("townstead.field_post.tooltip.soil.farmland").getString();
            hydrationLabel = Component.translatable(wet ? "townstead.field_post.tooltip.hydrated" : "townstead.field_post.tooltip.dry").getString();
            hydrationColor = wet ? 0x55AAFF : 0xCC8844;
        } else if (isNaturalGround(state)) {
            soilLabel = Component.translatable("townstead.field_post.tooltip.soil", new ItemStack(state.getBlock()).getHoverName()).getString();
        } else {
            soilLabel = new ItemStack(state.getBlock()).getHoverName().getString();
            soilColor = 0x888888;
        }

        // Plan
        int planKey = CellPlan.packXZ(gx - half, gz - half);
        SoilType soilPlanEntry = soilPlan.get(planKey);
        String seedPlanEntry = seedPlan.get(planKey);

        // Mod origin
        String modName = null;
        net.minecraft.resources.ResourceLocation blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (blockId != null && !"minecraft".equals(blockId.getNamespace())) {
            modName = categoryFor(blockId.getNamespace());
        }

        // ── Compute tooltip dimensions — measure every line precisely ──
        int lineH = 11;
        int pad = 5;
        int barH = 6;

        // Pre-render all text strings so we can measure them
        String readyText = cropAge >= 0 && cropAge >= cropMaxAge
                ? Component.translatable("townstead.field_post.tooltip.ready").getString() : null;
        String pctText = (cropAge >= 0 && cropAge < cropMaxAge && cropMaxAge > 0)
                ? ((cropAge * 100) / cropMaxAge) + "%" : null;
        String soilPlanText = soilPlanEntry != null
                ? Component.translatable("townstead.field_post.tooltip.plan.soil", titleCase(soilPlanEntry.name().toLowerCase())).getString() : null;
        String seedPlanText = null;
        if (seedPlanEntry != null) {
            ToolPaletteList.ToolEntry t = findToolEntry(seedPlanEntry);
            seedPlanText = Component.translatable("townstead.field_post.tooltip.plan.seed", t != null ? t.label : seedPlanEntry).getString();
        }

        // Measure max text width across all lines
        int maxTextW = 0;
        int cropIconW = cropItemIcon != null ? (int)(16 * (lineH / 16.0f)) + 2 : 0;
        if (cropName != null) maxTextW = Math.max(maxTextW, font.width(cropName) + cropIconW);
        if (readyText != null) maxTextW = Math.max(maxTextW, font.width(readyText));
        // Progress bar: reserve space for bar + gap + "99%" label on the right
        int pctReservedW = font.width("99%");
        int barGap = 4;
        if (pctText != null) maxTextW = Math.max(maxTextW, 40 + barGap + pctReservedW);
        if (soilLabel != null) maxTextW = Math.max(maxTextW, font.width(soilLabel));
        if (hydrationLabel != null) maxTextW = Math.max(maxTextW, font.width("  " + hydrationLabel));
        if (soilPlanText != null) maxTextW = Math.max(maxTextW, font.width(soilPlanText));
        if (seedPlanText != null) maxTextW = Math.max(maxTextW, font.width(seedPlanText));
        if (modName != null) maxTextW = Math.max(maxTextW, (int)(font.width(modName) * 0.85f));
        int tooltipW = maxTextW + pad * 2;
        int barW = tooltipW - pad * 2;

        // Measure content height — only count lines that exist
        int contentH = pad;
        if (cropName != null) contentH += lineH;
        if (readyText != null) contentH += lineH;
        else if (pctText != null) contentH += barH + 4;
        if (soilLabel != null) contentH += lineH;
        if (hydrationLabel != null) contentH += lineH;
        boolean hasPlan = soilPlanText != null || seedPlanText != null;
        if (hasPlan) {
            contentH += 6; // divider gap
            if (soilPlanText != null) contentH += lineH;
            if (seedPlanText != null) contentH += lineH;
        }
        if (modName != null) contentH += lineH;
        contentH += pad;

        // Position tooltip (avoid going off-screen)
        int tx = mouseX + 12;
        int ty = mouseY - 4;
        if (tx + tooltipW > width) tx = mouseX - tooltipW - 4;
        if (ty + contentH > height) ty = height - contentH - 2;
        if (ty < 2) ty = 2;

        // ── Draw tooltip box (above everything, Z=400) ──
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);

        int borderInner = 0xFF5000AA;
        int borderGrad = 0xFF28007F;
        int bgColor = 0xF0100010;
        // Background with rounded corners (1px inset)
        g.fill(tx + 1, ty, tx + tooltipW - 1, ty + contentH, bgColor);
        g.fill(tx, ty + 1, tx + tooltipW, ty + contentH - 1, bgColor);
        // Gradient border (top=lighter, bottom=darker)
        g.fill(tx + 1, ty, tx + tooltipW - 1, ty + 1, borderInner);
        g.fill(tx + 1, ty + contentH - 1, tx + tooltipW - 1, ty + contentH, borderGrad);
        g.fill(tx, ty + 1, tx + 1, ty + contentH - 1, borderInner);
        g.fill(tx + tooltipW - 1, ty + 1, tx + tooltipW, ty + contentH - 1, borderGrad);

        // ── Draw content ──
        int cy = ty + pad;
        int cx = tx + pad;

        // Crop name + icon (icon scaled to fit within line height)
        if (cropName != null) {
            if (cropItemIcon != null && !cropItemIcon.isEmpty()) {
                float iconScale = lineH / 16.0f; // scale 16px icon to fit line height
                g.pose().pushPose();
                g.pose().translate(cx, cy - 1, 300);
                g.pose().scale(iconScale, iconScale, 1);
                g.renderItem(cropItemIcon, 0, 0);
                g.pose().popPose();
                int iconW = (int)(16 * iconScale) + 2;
                g.drawString(font, cropName, cx + iconW, cy, nameColor, true);
            } else {
                g.drawString(font, cropName, cx, cy, nameColor, true);
            }
            cy += lineH;
        }

        // Progress bar
        if (readyText != null) {
            g.drawString(font, readyText, cx, cy, 0x55FF55, true);
            cy += lineH;
        } else if (pctText != null) {
            int pct = (cropAge * 100) / Math.max(1, cropMaxAge);
            // Bar is shorter — leaves room for % on the right
            int actualBarW = barW - pctReservedW - barGap;
            // Bar track
            g.fill(cx, cy, cx + actualBarW, cy + barH, 0xFF1A1A1A);
            // Bar fill
            int fillW = Math.max(1, (pct * actualBarW) / 100);
            int barColor = pct > 60 ? 0xFF44BB33 : (pct > 30 ? 0xFFBB9922 : 0xFFBB5522);
            g.fill(cx, cy, cx + fillW, cy + barH, barColor);
            g.fill(cx, cy, cx + fillW, cy + 1, 0x40FFFFFF); // top highlight
            // Border
            g.fill(cx - 1, cy - 1, cx + actualBarW + 1, cy, 0xFF333333);
            g.fill(cx - 1, cy + barH, cx + actualBarW + 1, cy + barH + 1, 0xFF333333);
            g.fill(cx - 1, cy, cx, cy + barH, 0xFF333333);
            g.fill(cx + actualBarW, cy, cx + actualBarW + 1, cy + barH, 0xFF333333);
            // Percentage to the right of the bar, right-aligned to reserved space
            int pctX = cx + actualBarW + barGap + pctReservedW - font.width(pctText);
            g.drawString(font, pctText, pctX, cy - 1, 0xCCCCCC, false);
            cy += barH + 3;
        }

        // Soil / ground
        if (soilLabel != null) {
            g.drawString(font, soilLabel, cx, cy, soilColor, false);
            cy += lineH;
        }
        if (hydrationLabel != null) {
            g.drawString(font, "  " + hydrationLabel, cx, cy, hydrationColor, false);
            cy += lineH;
        }

        // Plan section with divider
        if (hasPlan) {
            cy += 1;
            g.fill(cx, cy, cx + barW, cy + 1, 0x50FFCC00);
            cy += 4;
            if (soilPlanText != null) {
                g.drawString(font, soilPlanText, cx, cy, 0x88BBFF, false);
                cy += lineH;
            }
            if (seedPlanText != null) {
                g.drawString(font, seedPlanText, cx, cy, 0x55FF55, false);
                cy += lineH;
            }
        }

        // Mod origin (small, dim)
        if (modName != null) {
            g.pose().pushPose();
            g.pose().translate(cx, cy + 1, 0);
            g.pose().scale(0.85f, 0.85f, 1);
            g.drawString(font, modName, 0, 0, 0x555577, false);
            g.pose().popPose();
        }

        g.pose().popPose(); // pop Z=400 translation
    }

    private void drawCellBorder(GuiGraphics g, int cx, int cy, int cs, int color) {
        g.fill(cx, cy, cx + cs, cy + 1, color);
        g.fill(cx, cy + cs - 1, cx + cs, cy + cs, color);
        g.fill(cx, cy + 1, cx + 1, cy + cs - 1, color);
        g.fill(cx + cs - 1, cy + 1, cx + cs, cy + cs - 1, color);
    }

    private ToolPaletteList.ToolEntry findToolEntry(String id) {
        for (ToolPaletteList.ToolEntry e : allSeedEntries) { if (e.toolId.equals(id)) return e; }
        for (ToolPaletteList.ToolEntry e : allSoilEntries) { if (e.toolId.equals(id)) return e; }
        return null;
    }

    // ── Input ──

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Confirm / cancel — aligned with map frame right edge
        int btnSize = 14;
        int frameRight = vpLeft + vpW + FRAME_THICK;
        int cancelBtnX = frameRight - btnSize;
        int checkBtnX = cancelBtnX - btnSize - 4;
        int btnClickY = SPACING - 2;
        if (my >= btnClickY && my < btnClickY + btnSize) {
            if (mx >= checkBtnX && mx < checkBtnX + btnSize) { applyAndClose(); return true; }
            if (mx >= cancelBtnX && mx < cancelBtnX + btnSize) { onClose(); return true; }
        }

        // Tab clicks (Seeds | Soil)
        if (button == 0) {
            int palLeft = SPACING + FRAME_THICK;
            int palTop = SPACING + FRAME_THICK + TITLE_H;
            int tabY = palTop + SEARCH_H + 4;
            int tabW = PALETTE_W / 2;
            if (my >= tabY && my < tabY + 14) {
                if (mx >= palLeft && mx < palLeft + tabW) { switchTab(PaletteTab.SEEDS); return true; }
                if (mx >= palLeft + tabW && mx < palLeft + tabW * 2) { switchTab(PaletteTab.SOIL); return true; }
            }
        }

        // Toolbar clicks
        if (handleToolbarClick(mx, my, button)) return true;

        // Right/middle click in grid = pan (regardless of mode)
        if ((button == 1 || button == 2) && inGrid(mx, my)) {
            startPan(mx, my);
            return true;
        }

        // Left click in grid
        if (button == 0 && inGrid(mx, my)) {
            if (mode == Mode.PAN) {
                startPan(mx, my);
                return true;
            }
            paintAt(mx, my);
            return true;
        }

        return super.mouseClicked(mx, my, button);
    }

    private boolean handleToolbarClick(double mx, double my, int button) {
        if (button != 0) return false;
        if (my < toolbarTop || my >= toolbarTop + TOOLBAR_H) return false;

        // Mode buttons
        Mode[] modes = Mode.values();
        int btnSize = 16;
        int btnGap = 2;
        int btnY = toolbarTop + (TOOLBAR_H - btnSize) / 2;
        for (int i = 0; i < modes.length; i++) {
            int bx = toolbarLeft + 2 + i * (btnSize + btnGap);
            if (mx >= bx && mx < bx + btnSize && my >= btnY && my < btnY + btnSize) {
                mode = modes[i];
                return true;
            }
        }

        // Zoom buttons
        int smallBtn = 14;
        int rightY = toolbarTop + (TOOLBAR_H - smallBtn) / 2;
        if (my >= rightY && my < rightY + smallBtn) {
            int zoomPlusX = toolbarLeft + vpW - smallBtn - 4;
            int zoomMinusX = zoomPlusX - smallBtn - 2;
            if (mx >= zoomMinusX && mx < zoomMinusX + smallBtn) { adjustZoom(-1); return true; }
            if (mx >= zoomPlusX && mx < zoomPlusX + smallBtn) { adjustZoom(1); return true; }
        }
        return false;
    }

    private void adjustZoom(int delta) {
        int newZoom = Math.max(0, Math.min(ZOOM_LEVELS.length - 1, zoomIndex + delta));
        if (newZoom == zoomIndex) return;
        // Keep center on post
        float centerX = scrollX + viewCols / 2.0f;
        float centerY = scrollY + viewRows / 2.0f;
        zoomIndex = newZoom;
        recomputeViewport();
        targetScrollX = scrollX = centerX - viewCols / 2.0f;
        targetScrollY = scrollY = centerY - viewRows / 2.0f;
        clampScroll();
    }

    private void startPan(double mx, double my) {
        panning = true;
        panStartX = mx; panStartY = my;
        panStartScrollX = scrollX; panStartScrollY = scrollY;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (panning) {
            int st = stride();
            targetScrollX = panStartScrollX - (float) (mx - panStartX) / st;
            targetScrollY = panStartScrollY - (float) (my - panStartY) / st;
            scrollX = targetScrollX; // instant during active drag (no lerp while dragging)
            scrollY = targetScrollY;
            clampScroll();
            return true;
        }
        if (button == 0 && inGrid(mx, my) && (mode == Mode.PAINT || mode == Mode.ERASE)) {
            paintAt(mx, my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (panning) { panning = false; return true; }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my,
                                  //? if >=1.21 {
                                  double scrollXD, double scrollYD) {
                                  //?} else {
                                  /*double scrollYD) {
                                  *///?}
        if (inGrid(mx, my)) {
            // Ctrl+scroll = zoom, otherwise pan
            if (hasControlDown()) {
                adjustZoom(scrollYD > 0 ? 1 : -1);
                return true;
            }
            if (hasShiftDown()) targetScrollX -= (float) scrollYD * 2;
            else targetScrollY -= (float) scrollYD * 2;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mx, my,
                //? if >=1.21 {
                scrollXD, scrollYD);
                //?} else {
                /*scrollYD);
                *///?}
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (searchBox != null && searchBox.isFocused()) {
            if (key == 256) { searchBox.setFocused(false); return true; }
            return super.keyPressed(key, scan, mods);
        }
        if (key == 256) { onClose(); return true; }
        // Ctrl+Z = undo, Ctrl+Y = redo
        if (key == 90 && hasControlDown()) { undo(); return true; }  // Z
        if (key == 89 && hasControlDown()) { redo(); return true; }  // Y
        // Mode shortcuts
        if (key == 66) { mode = Mode.PAINT; return true; }  // B
        if (key == 72) { mode = Mode.PAN; return true; }    // H
        if (key == 69) { mode = Mode.ERASE; return true; }  // E
        // Tab shortcuts
        if (key == 49) { switchTab(PaletteTab.SEEDS); return true; }  // 1
        if (key == 50) { switchTab(PaletteTab.SOIL); return true; }   // 2
        // Pan with arrow keys
        if (key == 263) { targetScrollX -= 2; clampScroll(); return true; }
        if (key == 262) { targetScrollX += 2; clampScroll(); return true; }
        if (key == 265) { targetScrollY -= 2; clampScroll(); return true; }
        if (key == 264) { targetScrollY += 2; clampScroll(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    private boolean inGrid(double mx, double my) {
        return mx >= vpLeft && mx < vpLeft + vpW && my >= vpTop && my < vpTop + vpH;
    }

    private int[] gridCellAt(double mx, double my) {
        int st = stride();
        int startGX = (int) Math.floor(scrollX);
        int startGZ = (int) Math.floor(scrollY);
        int offsetX = Math.round(-(scrollX - startGX) * st);
        int offsetY = Math.round(-(scrollY - startGZ) * st);
        int vx = (int) Math.floor((mx - vpLeft - offsetX) / st);
        int vy = (int) Math.floor((my - vpTop - offsetY) / st);
        int gx = startGX + vx;
        int gz = startGZ + vy;
        if (gx < 0 || gz < 0 || gx >= gridSize || gz >= gridSize) return null;
        if (cellFlags[gz][gx] == CELL_POST) return null;
        return new int[]{gx, gz};
    }

    private void paintAt(double mx, double my) {
        int[] cell = gridCellAt(mx, my);
        if (cell == null) return;
        int half = gridSize / 2;
        int key = CellPlan.packXZ(cell[0] - half, cell[1] - half);

        if (mode == Mode.ERASE) {
            eraseAt(key);
            return;
        }
        ToolPaletteList.ToolEntry tool = paletteList.getSelected();
        if (tool == null || tool.isHeader) return;

        // Push undo
        pushUndo(key);

        if (activeTab == PaletteTab.SOIL) {
            SoilType type = SoilType.fromName(tool.toolId);
            if (type != null) soilPlan.put(key, type);
        } else {
            seedPlan.put(key, tool.toolId);
        }
        refreshCounts();
    }

    private void eraseAt(int key) {
        pushUndo(key);
        if (activeTab == PaletteTab.SOIL) soilPlan.remove(key);
        else seedPlan.remove(key);
        refreshCounts();
    }

    private void eraseAtMouse(double mx, double my) {
        int[] cell = gridCellAt(mx, my);
        if (cell == null) return;
        int half = gridSize / 2;
        int key = CellPlan.packXZ(cell[0] - half, cell[1] - half);
        eraseAt(key);
    }

    private void pushUndo(int key) {
        undoStack.push(new UndoEntry(key, activeTab, soilPlan.get(key), seedPlan.get(key)));
        if (undoStack.size() > MAX_UNDO) ((ArrayDeque<UndoEntry>) undoStack).removeLast();
        redoStack.clear(); // new action invalidates redo history
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        UndoEntry entry = undoStack.pop();
        redoStack.push(new UndoEntry(entry.key, entry.tab, soilPlan.get(entry.key), seedPlan.get(entry.key)));
        if (entry.prevSoil != null) soilPlan.put(entry.key, entry.prevSoil);
        else soilPlan.remove(entry.key);
        if (entry.prevSeed != null) seedPlan.put(entry.key, entry.prevSeed);
        else seedPlan.remove(entry.key);
        refreshCounts();
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        UndoEntry entry = redoStack.pop();
        undoStack.push(new UndoEntry(entry.key, entry.tab, soilPlan.get(entry.key), seedPlan.get(entry.key)));
        if (entry.prevSoil != null) soilPlan.put(entry.key, entry.prevSoil);
        else soilPlan.remove(entry.key);
        if (entry.prevSeed != null) seedPlan.put(entry.key, entry.prevSeed);
        else seedPlan.remove(entry.key);
        refreshCounts();
    }

    private void refreshCounts() {
        if (paletteList == null) return;
        if (activeTab == PaletteTab.SEEDS && villageSeedCounts != null) {
            // Use server-provided village seed counts
            paletteList.setAssignmentCounts(villageSeedCounts);
        } else if (activeTab == PaletteTab.SOIL) {
            // For soil tab, show plan assignments
            Map<String, Integer> counts = new HashMap<>();
            for (SoilType val : soilPlan.values()) counts.merge(val.name(), 1, Integer::sum);
            paletteList.setAssignmentCounts(counts);
        } else {
            paletteList.setAssignmentCounts(Map.of());
        }
    }

    private void clampScroll() {
        targetScrollX = Math.max(0, Math.min(targetScrollX, Math.max(0, gridSize - viewCols)));
        targetScrollY = Math.max(0, Math.min(targetScrollY, Math.max(0, gridSize - viewRows)));
        scrollX = Math.max(0, Math.min(scrollX, Math.max(0, gridSize - viewCols)));
        scrollY = Math.max(0, Math.min(scrollY, Math.max(0, gridSize - viewRows)));
    }

    private void applyAndClose() {
        CellPlan.Builder planBuilder = CellPlan.builder();
        soilPlan.forEach(planBuilder::rawSoil);
        seedPlan.forEach(planBuilder::rawSeed);
        CellPlan plan = planBuilder.build();
        // Build seeds list for the seed filter from explicit seed assignments
        List<String> seeds = new ArrayList<>();
        for (String val : seedPlan.values()) {
            if (SeedAssignment.isExplicitSeed(val) && !seeds.contains(val)) seeds.add(val);
        }
        FieldPostConfig config = new FieldPostConfig(
                loadedConfig.patternId(), loadedConfig.tierCap(), loadedConfig.radius(),
                loadedConfig.priority(), seeds.isEmpty(), seeds,
                loadedConfig.waterEnabled(), loadedConfig.maxWaterCells(),
                loadedConfig.groomEnabled(), loadedConfig.groomRadius(),
                loadedConfig.rotationEnabled(), loadedConfig.rotationPatterns(),
                plan);
        FieldPostConfigSetPayload payload = new FieldPostConfigSetPayload(postPos, config);
        //? if neoforge {
        PacketDistributor.sendToServer(payload);
        //?} else if forge {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(payload);
        *///?}
        onClose();
    }

    //? if >=1.21 {
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {}
    //?}

    public BlockPos getPostPos() { return postPos; }

    /**
     * Called by the client packet handler when the server sends the grid snapshot.
     * Populates the grid render data from server-provided data instead of client scanning.
     */
    public void applyServerSnapshot(com.aetherianartificer.townstead.farming.GridSnapshot snapshot,
                                     Map<String, String> cropPalette,
                                     Map<String, Integer> villageSeedCounts,
                                     int farmerCount, int totalPlots, int tilledPlots, int hydrationPercent) {
        if (snapshot.gridSize() != gridSize) return; // size mismatch — ignore

        int count = snapshot.cellCount();
        renderStates = new BlockState[gridSize][gridSize];
        renderPositions = new BlockPos[gridSize][gridSize];
        cellFlags = new byte[gridSize][gridSize];
        cropIcons = new ItemStack[gridSize][gridSize];

        int half = gridSize / 2;
        for (int gz = 0; gz < gridSize; gz++) {
            for (int gx = 0; gx < gridSize; gx++) {
                int idx = snapshot.index(gx, gz);
                byte flags = snapshot.flags()[idx];
                int blockId = snapshot.groundBlockIds()[idx];

                // Reconstruct BlockState for sprite rendering
                Block block = BuiltInRegistries.BLOCK.byId(blockId);
                BlockState state = block.defaultBlockState();

                // Apply moisture for farmland sprite selection
                if ((flags & com.aetherianartificer.townstead.farming.GridSnapshot.FLAG_FARMLAND) != 0
                        && block instanceof FarmBlock) {
                    int moisture = (flags & com.aetherianartificer.townstead.farming.GridSnapshot.FLAG_MOIST) != 0 ? 7 : 0;
                    state = state.setValue(FarmBlock.MOISTURE, moisture);
                }

                renderStates[gz][gx] = (flags & com.aetherianartificer.townstead.farming.GridSnapshot.FLAG_AIR) != 0 ? null : state;
                renderPositions[gz][gx] = new BlockPos(postPos.getX() + (gx - half), postPos.getY() - 1, postPos.getZ() + (gz - half));

                // Cell flags
                if ((flags & com.aetherianartificer.townstead.farming.GridSnapshot.FLAG_POST) != 0) {
                    cellFlags[gz][gx] = CELL_POST;
                } else if ((flags & com.aetherianartificer.townstead.farming.GridSnapshot.FLAG_AIR) != 0) {
                    cellFlags[gz][gx] = CELL_AIR;
                } else if ((flags & com.aetherianartificer.townstead.farming.GridSnapshot.FLAG_MATURE) != 0) {
                    cellFlags[gz][gx] = CELL_CROP_MATURE;
                }

                // Crop icon from server-resolved product
                int cropItemId = snapshot.cropItemIds()[idx];
                if (cropItemId > 0) {
                    Item cropItem = BuiltInRegistries.ITEM.byId(cropItemId);
                    if (cropItem != Items.AIR) {
                        cropIcons[gz][gx] = new ItemStack(cropItem);
                    }
                }
            }
        }

        // Store server data
        this.serverSnapshot = snapshot;
        this.serverCropPalette = cropPalette;
        this.villageSeedCounts = villageSeedCounts;
        buildToolEntries();
        filterPalette();
    }

    private com.aetherianartificer.townstead.farming.GridSnapshot serverSnapshot;
    private Map<String, String> serverCropPalette;
    private Map<String, Integer> villageSeedCounts;

    @Override
    public boolean isPauseScreen() { return false; }
}
