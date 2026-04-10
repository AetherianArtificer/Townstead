package com.aetherianartificer.townstead.client.gui.fieldpost;

import com.aetherianartificer.townstead.block.CropDetection;
import com.aetherianartificer.townstead.block.FieldPostBlockEntity;
import com.aetherianartificer.townstead.farming.FieldPostConfigSetPayload;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}

import java.util.*;

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
    private static final int TITLE_H = 18;
    private static final int SEARCH_H = 16;
    private static final int TOOLBAR_H = 22;
    private static final int STATUS_H = 16;

    // ── Colors ──
    private static final int TEXT_LIGHT = 0xFFF0E6CF;
    private static final int TEXT_DIM = 0xFFA89A80;
    private static final int ACCENT = 0xFF88DD44;
    private static final int PLAN_BORDER = 0xFFFFCC00;
    private static final int CENTER_MARKER = 0xFFFF8844;
    private static final int BG_DARK = 0xFF0F0A05;

    // ── Modes ──
    private enum Mode {
        PAINT("Paint", Items.WOODEN_HOE),
        PAN("Pan", Items.COMPASS),
        ERASE("Erase", Items.BARRIER);
        final String label;
        final Item icon;
        Mode(String l, Item i) { this.label = l; this.icon = i; }
    }
    private Mode mode = Mode.PAINT;

    // ── Cell flags (special case markers) ──
    private static final byte CELL_NORMAL = 0;
    private static final byte CELL_POST = 1;
    private static final byte CELL_CROP_MATURE = 2;
    private static final byte CELL_AIR = 3;

    // ── State ──
    private final BlockPos postPos;
    private final Level level;
    private int gridSize;
    private BlockState[][] renderStates;     // block state to render per cell (top-down)
    private BlockPos[][] renderPositions;    // world position for tint sampling
    private byte[][] cellFlags;              // special cell markers
    private ItemStack[][] cropIcons;
    private final Map<Long, String> plan = new HashMap<>();

    // Widgets
    private EditBox searchBox;
    private ToolPaletteList paletteList;
    private final List<ToolPaletteList.ToolEntry> allEntries = new ArrayList<>();

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

    // Config
    private String patternId = FieldPostBlockEntity.DEFAULT_PATTERN;
    private int tierCap = FieldPostBlockEntity.DEFAULT_TIER_CAP;
    private int radius = FieldPostBlockEntity.DEFAULT_RADIUS;
    private int priority = FieldPostBlockEntity.DEFAULT_PRIORITY;
    private boolean waterEnabled = true;
    private int maxWaterCells = FieldPostBlockEntity.DEFAULT_MAX_WATER_CELLS;
    private boolean groomEnabled = true;
    private int groomRadius = FieldPostBlockEntity.DEFAULT_GROOM_RADIUS;
    private boolean rotationEnabled = false;
    private final List<String> rotationPatterns = new ArrayList<>();

    public FieldPostScreen(BlockPos pos, Level level) {
        super(Component.translatable("container.townstead.field_post"));
        this.postPos = pos;
        this.level = level;
    }

    @Override
    protected void init() {
        super.init();

        if (level.getBlockEntity(postPos) instanceof FieldPostBlockEntity be) {
            patternId = be.getPatternId();
            tierCap = be.getTierCap();
            radius = be.getRadius();
            priority = be.getPriority();
            waterEnabled = be.isWaterEnabled();
            maxWaterCells = be.getMaxWaterCells();
            groomEnabled = be.isGroomEnabled();
            groomRadius = be.getGroomRadius();
            rotationEnabled = be.isRotationEnabled();
            rotationPatterns.clear();
            rotationPatterns.addAll(be.getRotationPatterns());
            plan.clear();
            plan.putAll(be.getCellPlan());
        }

        gridSize = radius * 2 + 1;

        // Layout — consistent SPACING gutters everywhere (screen edges + between panels)
        int palLeft = SPACING + FRAME_THICK;
        int palTop = SPACING + FRAME_THICK + TITLE_H;

        // Viewport frame wraps both toolbar AND grid (shared frame)
        // Gap between palette frame and viewport frame = SPACING of empty space
        vpLeft = palLeft + PALETTE_W + FRAME_THICK + SPACING + FRAME_THICK;
        // Viewport content area starts at palTop (same as palette) and includes toolbar row at top
        int vpFrameTop = palTop;
        vpTop = vpFrameTop + TOOLBAR_H;  // grid cells start below toolbar row
        vpW = width - vpLeft - SPACING - FRAME_THICK;
        // Full frame height = toolbar + grid + room for status bar below frame
        int vpFrameH = height - vpFrameTop - SPACING - FRAME_THICK - STATUS_H - SPACING;
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
        searchBox.setHint(Component.literal("Search seeds..."));
        searchBox.setResponder(text -> filterPalette());
        addRenderableWidget(searchBox);

        buildToolEntries();

        // Tool list
        int listTop = palTop + SEARCH_H + 6;
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

    // Entries grouped by category key
    private final LinkedHashMap<String, List<ToolPaletteList.ToolEntry>> entriesByCategory = new LinkedHashMap<>();
    private static final String CAT_TOOLS = "Tools";
    private static final String CAT_VANILLA = "Vanilla";

    private void buildToolEntries() {
        allEntries.clear();
        entriesByCategory.clear();

        // Built-in tools
        addCategoryEntry(CAT_TOOLS, new ToolPaletteList.ToolEntry("auto", "Auto", new ItemStack(Items.BONE_MEAL), CAT_TOOLS));
        addCategoryEntry(CAT_TOOLS, new ToolPaletteList.ToolEntry("water", "Water", new ItemStack(Items.WATER_BUCKET), CAT_TOOLS));

        // Crops discovered from registry — group by mod namespace
        for (String seedId : CropDetection.getAllPlantableSeeds()) {
            //? if >=1.21 {
            ResourceLocation rl = ResourceLocation.parse(seedId);
            //?} else {
            /*ResourceLocation rl = new ResourceLocation(seedId);
            *///?}
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item == Items.AIR) continue;
            String name = new ItemStack(item).getHoverName().getString();
            String category = categoryFor(rl.getNamespace());
            addCategoryEntry(category, new ToolPaletteList.ToolEntry(seedId, name, new ItemStack(item), category));
        }

        // Sort entries within each category alphabetically
        for (List<ToolPaletteList.ToolEntry> entries : entriesByCategory.values()) {
            entries.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        }
    }

    private void addCategoryEntry(String category, ToolPaletteList.ToolEntry entry) {
        entriesByCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(entry);
        allEntries.add(entry);
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
            // Apply search filter to this category's items
            List<ToolPaletteList.ToolEntry> matching = new ArrayList<>();
            for (ToolPaletteList.ToolEntry e : items) {
                if (query.isEmpty()
                        || e.label.toLowerCase(Locale.ROOT).contains(query)
                        || e.toolId.contains(query)) {
                    matching.add(e);
                }
            }
            if (matching.isEmpty()) continue;

            // Add header
            ToolPaletteList.ToolEntry header = ToolPaletteList.ToolEntry.header(category, matching.size());
            filtered.add(header);

            // Add items unless collapsed (but during search, always show matching)
            boolean forceExpand = !query.isEmpty();
            if (forceExpand || !paletteList.isCategoryCollapsed(category)) {
                filtered.addAll(matching);
            }
        }

        ToolPaletteList.ToolEntry prev = paletteList.getSelected();
        paletteList.replaceEntries(filtered);
        if (prev != null && !prev.isHeader && filtered.contains(prev)) {
            paletteList.setSelected(prev);
        }
    }

    private void scanWorld() {
        renderStates = new BlockState[gridSize][gridSize];
        renderPositions = new BlockPos[gridSize][gridSize];
        cellFlags = new byte[gridSize][gridSize];
        cropIcons = new ItemStack[gridSize][gridSize];
        int half = gridSize / 2;

        for (int gz = 0; gz < gridSize; gz++) {
            for (int gx = 0; gx < gridSize; gx++) {
                int wx = postPos.getX() + (gx - half);
                int wz = postPos.getZ() + (gz - half);

                // Walk upward from post.Y-1 to post.Y+3 looking for the topmost visible block
                // (handles snow layers, crops on farmland, etc.)
                BlockPos chosen = null;
                BlockState chosenState = null;
                // Prefer the block AT post.y (crops/water) if non-air; fall back to below (terrain)
                BlockPos atPos = new BlockPos(wx, postPos.getY(), wz);
                BlockPos belowPos = atPos.below();
                BlockState stateAt = level.getBlockState(atPos);
                BlockState stateBelow = level.getBlockState(belowPos);

                if (gx == half && gz == half) {
                    cellFlags[gz][gx] = CELL_POST;
                    chosen = atPos;
                    chosenState = level.getBlockState(atPos);
                    // Use planks as post marker if the block entity state isn't useful
                } else if (stateAt.getFluidState().is(Fluids.WATER) || stateAt.getFluidState().is(Fluids.LAVA)) {
                    chosen = atPos;
                    chosenState = stateAt;
                } else if (stateAt.getBlock() instanceof CropBlock crop) {
                    chosen = atPos;
                    chosenState = stateAt;
                    if (crop.isMaxAge(stateAt)) cellFlags[gz][gx] = CELL_CROP_MATURE;
                    cropIcons[gz][gx] = cropIcon(stateAt.getBlock());
                } else if (stateAt.getBlock() instanceof BushBlock) {
                    chosen = atPos;
                    chosenState = stateAt;
                    cropIcons[gz][gx] = new ItemStack(stateAt.getBlock().asItem());
                } else if (!stateBelow.isAir()) {
                    chosen = belowPos;
                    chosenState = stateBelow;
                } else if (!stateAt.isAir()) {
                    chosen = atPos;
                    chosenState = stateAt;
                } else {
                    cellFlags[gz][gx] = CELL_AIR;
                    chosen = belowPos;
                    chosenState = stateBelow;
                }

                renderStates[gz][gx] = chosenState;
                renderPositions[gz][gx] = chosen;
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

    private ItemStack cropIcon(Block block) {
        if (block == Blocks.WHEAT) return new ItemStack(Items.WHEAT);
        if (block == Blocks.CARROTS) return new ItemStack(Items.CARROT);
        if (block == Blocks.POTATOES) return new ItemStack(Items.POTATO);
        if (block == Blocks.BEETROOTS) return new ItemStack(Items.BEETROOT);
        if (block == Blocks.MELON_STEM) return new ItemStack(Items.MELON_SEEDS);
        if (block == Blocks.PUMPKIN_STEM) return new ItemStack(Items.PUMPKIN_SEEDS);
        return new ItemStack(block.asItem());
    }

    // ── Tick: smooth pan lerp ──
    @Override
    public void tick() {
        super.tick();
        // Lerp toward target scroll (smooth panning)
        float lerp = 0.35f;
        scrollX += (targetScrollX - scrollX) * lerp;
        scrollY += (targetScrollY - scrollY) * lerp;
    }

    // ── Render ──

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // Semi-transparent background so the game world shows through
        double opacity = minecraft.options.textBackgroundOpacity().get();
        // Map 0.0..1.0 -> 0x40..0xC0 alpha (never fully opaque, never fully transparent)
        int alpha = 0x40 + (int) (opacity * (0xC0 - 0x40));
        int bgColor = (alpha << 24) | 0x0A0705;
        g.fill(0, 0, width, height, bgColor);

        // Title bar
        String titleStr = title.getString();
        g.drawString(font, titleStr, 12, 6, TEXT_LIGHT, true);

        // Top-right confirm / cancel icons
        int btnSize = 14;
        int checkX = width - 36;
        int cancelX = width - 18;
        boolean hCheck = mouseX >= checkX && mouseX < checkX + btnSize && mouseY >= 4 && mouseY < 4 + btnSize;
        boolean hCancel = mouseX >= cancelX && mouseX < cancelX + btnSize && mouseY >= 4 && mouseY < 4 + btnSize;
        g.fill(checkX - 1, 3, checkX + btnSize + 1, 4 + btnSize + 1, FrameRenderer.FRAME_SHADOW);
        g.fill(checkX, 4, checkX + btnSize, 4 + btnSize, hCheck ? 0xFF66CC44 : 0xFF3D7A22);
        g.drawCenteredString(font, "\u2713", checkX + btnSize / 2, 7, 0xFFFFFFFF);
        g.fill(cancelX - 1, 3, cancelX + btnSize + 1, 4 + btnSize + 1, FrameRenderer.FRAME_SHADOW);
        g.fill(cancelX, 4, cancelX + btnSize, 4 + btnSize, hCancel ? 0xFFCC4444 : 0xFF7A2222);
        g.drawCenteredString(font, "\u2717", cancelX + btnSize / 2, 7, 0xFFFFFFFF);

        // ── Palette panel ──
        int palLeft = SPACING + FRAME_THICK;
        int palTop = SPACING + FRAME_THICK + TITLE_H;
        int palH = height - palTop - SPACING - FRAME_THICK;
        FrameRenderer.drawWoodenFrame(g, palLeft, palTop, PALETTE_W, palH, FRAME_THICK);
        // Solid dark panel (no parchment - more readable)
        g.fill(palLeft, palTop, palLeft + PALETTE_W, palTop + palH, chatPanelColor());
        g.fill(palLeft, palTop + SEARCH_H + 5, palLeft + PALETTE_W, palTop + SEARCH_H + 6, 0x40FFDEA0);

        // ── Grid viewport frame (wraps toolbar + grid) ──
        int vpFrameTop = toolbarTop;
        int vpFrameH = TOOLBAR_H + vpH;
        FrameRenderer.drawWoodenFrame(g, vpLeft, vpFrameTop, vpW, vpFrameH, FRAME_THICK);

        // ── Toolbar (inside the frame, at the top) ──
        renderToolbar(g, mouseX, mouseY);

        // ── Grid content (below toolbar, inside same frame) ──
        renderGrid(g, mouseX, mouseY);
        renderCardinalLabels(g);

        // ── Status bar ──
        renderStatusBar(g, mouseX, mouseY);

        // Widgets (search box + palette list)
        super.render(g, mouseX, mouseY, partial);

        // Cell tooltip (after super so it renders on top)
        renderGridTooltip(g, mouseX, mouseY);
    }

    private void renderToolbar(GuiGraphics g, int mouseX, int mouseY) {
        int x = toolbarLeft;
        int y = toolbarTop;

        // Toolbar background strip — solid dark
        g.fill(x, y, x + vpW, y + TOOLBAR_H - 2, chatPanelColor());
        g.fill(x, y + TOOLBAR_H - 3, x + vpW, y + TOOLBAR_H - 2, 0x40FFDEA0);

        // Mode buttons
        Mode[] modes = Mode.values();
        int btnSize = 18;
        int btnGap = 4;
        for (int i = 0; i < modes.length; i++) {
            int bx = x + 4 + i * (btnSize + btnGap);
            int by = y + 1;
            Mode m = modes[i];
            boolean selected = m == mode;
            boolean hovered = mouseX >= bx && mouseX < bx + btnSize && mouseY >= by && mouseY < by + btnSize;

            // Slot background
            g.fill(bx - 1, by - 1, bx + btnSize + 1, by + btnSize + 1, FrameRenderer.FRAME_SHADOW);
            g.fill(bx, by, bx + btnSize, by + btnSize, selected ? 0xFF5A8A2A : (hovered ? 0xFF3A3225 : 0xFF24201A));
            if (selected) {
                g.fill(bx, by, bx + btnSize, by + 1, ACCENT);
                g.fill(bx, by + btnSize - 1, bx + btnSize, by + btnSize, ACCENT);
                g.fill(bx, by, bx + 1, by + btnSize, ACCENT);
                g.fill(bx + btnSize - 1, by, bx + btnSize, by + btnSize, ACCENT);
            }

            // Icon
            g.renderItem(new ItemStack(m.icon), bx + 1, by + 1);

            // Tooltip
            if (hovered) {
                g.renderTooltip(font, Component.literal(m.label), mouseX, mouseY);
            }
        }

        // Zoom controls on the right
        int zoomBtnSize = 14;
        int zoomY = y + 3;
        int plusX = x + vpW - zoomBtnSize - 4;
        int minusX = plusX - zoomBtnSize - 4;
        drawSmallButton(g, minusX, zoomY, zoomBtnSize, "-", mouseX, mouseY);
        drawSmallButton(g, plusX, zoomY, zoomBtnSize, "+", mouseX, mouseY);

        // Zoom label
        String zoomLbl = (zoomIndex + 1) + "/" + ZOOM_LEVELS.length;
        g.drawString(font, zoomLbl, minusX - font.width(zoomLbl) - 6, zoomY + 3, TEXT_DIM, false);
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
        g.fill(vpLeft, vpTop, vpLeft + vpW, vpTop + vpH, 0xFF0D0A05);

        int half = gridSize / 2;
        int startGX = (int) Math.floor(scrollX);
        int startGZ = (int) Math.floor(scrollY);
        float offsetX = -(scrollX - startGX) * st;
        float offsetY = -(scrollY - startGZ) * st;

        for (int vy = -1; vy <= viewRows + 1; vy++) {
            for (int vx = -1; vx <= viewCols + 1; vx++) {
                int gx = startGX + vx;
                int gz = startGZ + vy;
                if (gx < 0 || gz < 0 || gx >= gridSize || gz >= gridSize) continue;

                int cx = vpLeft + (int) (vx * st + offsetX);
                int cy = vpTop + (int) (vy * st + offsetY);
                if (cx + cs < vpLeft || cy + cs < vpTop || cx > vpLeft + vpW || cy > vpTop + vpH) continue;

                BlockState state = renderStates[gz][gx];
                BlockPos worldPos = renderPositions[gz][gx];
                byte flag = cellFlags[gz][gx];

                // Base color under the sprite for fallback
                g.fill(cx, cy, cx + cs, cy + cs, 0xFF1E1E1E);

                if (state != null && !state.isAir()) {
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

                // Existing crop icon (small, bottom-left)
                ItemStack icon = cropIcons[gz][gx];
                if (icon != null && !icon.isEmpty()) {
                    g.pose().pushPose();
                    float scale = cs / 16.0f * 0.6f;
                    g.pose().translate(cx + 2, cy + cs - cs * 0.6f - 2, 100);
                    g.pose().scale(scale, scale, 1.0f);
                    g.renderItem(icon, 0, 0);
                    g.pose().popPose();
                }

                // Plan overlay
                int wx = postPos.getX() + (gx - half);
                int wz = postPos.getZ() + (gz - half);
                long posKey = BlockPos.asLong(wx, postPos.getY(), wz);
                String assignment = plan.get(posKey);
                if (assignment != null) {
                    ToolPaletteList.ToolEntry tool = findToolEntry(assignment);
                    if (tool != null) {
                        drawCellBorder(g, cx, cy, cs, PLAN_BORDER);
                        // Smaller centered icon so terrain is still visible
                        g.pose().pushPose();
                        float scale = cs / 16.0f * 0.65f;
                        float iconSize = 16 * scale;
                        float offX = (cs - iconSize) / 2.0f;
                        float offY = (cs - iconSize) / 2.0f;
                        g.pose().translate(cx + offX, cy + offY, 200);
                        g.pose().scale(scale, scale, 1.0f);
                        g.renderItem(tool.icon, 0, 0);
                        g.pose().popPose();
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
        // Labels drawn just inside the viewport rectangle so they sit on the grid, not the frame
        int midX = vpLeft + vpW / 2;
        int midY = vpTop + vpH / 2;
        g.drawCenteredString(font, "N", midX, vpTop + 2, TEXT_LIGHT);
        g.drawCenteredString(font, "S", midX, vpTop + vpH - 10, TEXT_LIGHT);
        g.drawString(font, "W", vpLeft + 2, midY - 4, TEXT_LIGHT, true);
        g.drawString(font, "E", vpLeft + vpW - 8, midY - 4, TEXT_LIGHT, true);
    }

    private void renderStatusBar(GuiGraphics g, int mouseX, int mouseY) {
        // Sits below the viewport frame with SPACING of gap
        int y = vpTop + vpH + FRAME_THICK + SPACING;
        int w = vpW;
        g.fill(vpLeft, y, vpLeft + w, y + STATUS_H - 2, chatPanelColor());
        g.fill(vpLeft, y, vpLeft + w, y + 1, 0x40FFDEA0);

        // Selected tool
        ToolPaletteList.ToolEntry selected = paletteList != null ? paletteList.getSelected() : null;
        String toolStr;
        if (mode == Mode.ERASE) {
            toolStr = "Tool: Erase";
        } else if (mode == Mode.PAN) {
            toolStr = "Tool: Pan (drag)";
        } else if (selected != null && !selected.isHeader) {
            toolStr = "Tool: " + selected.label;
        } else {
            toolStr = "Tool: (select from palette)";
        }
        g.drawString(font, toolStr, vpLeft + 4, y + 3, TEXT_LIGHT, false);

        // Plan count
        String planStr = plan.size() + " assigned";
        int coverage = gridSize * gridSize == 0 ? 0 : (plan.size() * 100) / (gridSize * gridSize);
        String covStr = "Coverage: " + coverage + "%";
        g.drawString(font, covStr, vpLeft + w - font.width(covStr) - 4, y + 3, TEXT_DIM, false);
        g.drawString(font, planStr, vpLeft + w - font.width(covStr) - 12 - font.width(planStr), y + 3, TEXT_DIM, false);
    }

    private void renderGridTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (panning || mouseX < vpLeft || mouseX >= vpLeft + vpW || mouseY < vpTop || mouseY >= vpTop + vpH) return;

        int cs = cellSize();
        int st = stride();
        int startGX = (int) Math.floor(scrollX);
        int startGZ = (int) Math.floor(scrollY);
        float offsetX = -(scrollX - startGX) * st;
        float offsetY = -(scrollY - startGZ) * st;
        int vx = (int) Math.floor((mouseX - vpLeft - offsetX) / st);
        int vy = (int) Math.floor((mouseY - vpTop - offsetY) / st);
        int gx = startGX + vx;
        int gz = startGZ + vy;
        if (gx < 0 || gz < 0 || gx >= gridSize || gz >= gridSize) return;

        int half = gridSize / 2;
        int wx = postPos.getX() + (gx - half);
        int wz = postPos.getZ() + (gz - half);
        BlockState state = renderStates[gz][gx];

        List<Component> tips = new ArrayList<>();
        tips.add(Component.literal(String.format("(%d, %d)", wx, wz)));
        String blockName = state != null ? new ItemStack(state.getBlock()).getHoverName().getString() : "Unknown";
        tips.add(Component.literal(blockName).withStyle(s -> s.withColor(0xAACCCC)));
        long posKey = BlockPos.asLong(wx, postPos.getY(), wz);
        String a = plan.get(posKey);
        if (a != null) {
            ToolPaletteList.ToolEntry t = findToolEntry(a);
            tips.add(Component.literal("Plan: " + (t != null ? t.label : a)).withStyle(s -> s.withColor(0x55FF55)));
        }
        g.renderTooltip(font, tips, Optional.empty(), mouseX, mouseY);
    }

    private void drawCellBorder(GuiGraphics g, int cx, int cy, int cs, int color) {
        g.fill(cx, cy, cx + cs, cy + 1, color);
        g.fill(cx, cy + cs - 1, cx + cs, cy + cs, color);
        g.fill(cx, cy + 1, cx + 1, cy + cs - 1, color);
        g.fill(cx + cs - 1, cy + 1, cx + cs, cy + cs - 1, color);
    }

    private ToolPaletteList.ToolEntry findToolEntry(String id) {
        for (ToolPaletteList.ToolEntry e : allEntries) { if (e.toolId.equals(id)) return e; }
        return null;
    }

    // ── Input ──

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Confirm / cancel
        int btnSize = 14;
        if (my >= 4 && my < 4 + btnSize) {
            if (mx >= width - 36 && mx < width - 22) { applyAndClose(); return true; }
            if (mx >= width - 18 && mx < width - 4) { onClose(); return true; }
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

        Mode[] modes = Mode.values();
        int btnSize = 18;
        int btnGap = 4;
        for (int i = 0; i < modes.length; i++) {
            int bx = toolbarLeft + 4 + i * (btnSize + btnGap);
            int by = toolbarTop + 1;
            if (mx >= bx && mx < bx + btnSize && my >= by && my < by + btnSize) {
                mode = modes[i];
                return true;
            }
        }

        // Zoom buttons
        int zoomBtnSize = 14;
        int plusX = toolbarLeft + vpW - zoomBtnSize - 4;
        int minusX = plusX - zoomBtnSize - 4;
        int zoomY = toolbarTop + 3;
        if (my >= zoomY && my < zoomY + zoomBtnSize) {
            if (mx >= minusX && mx < minusX + zoomBtnSize) { adjustZoom(-1); return true; }
            if (mx >= plusX && mx < plusX + zoomBtnSize) { adjustZoom(1); return true; }
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
        // Mode shortcuts
        if (key == 66) { mode = Mode.PAINT; return true; }  // B
        if (key == 72) { mode = Mode.PAN; return true; }    // H
        if (key == 69) { mode = Mode.ERASE; return true; }  // E
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

    private void paintAt(double mx, double my) {
        int cs = cellSize();
        int st = stride();
        int startGX = (int) Math.floor(scrollX);
        int startGZ = (int) Math.floor(scrollY);
        float offsetX = -(scrollX - startGX) * st;
        float offsetY = -(scrollY - startGZ) * st;
        int vx = (int) Math.floor((mx - vpLeft - offsetX) / st);
        int vy = (int) Math.floor((my - vpTop - offsetY) / st);
        int gx = startGX + vx;
        int gz = startGZ + vy;
        if (gx < 0 || gz < 0 || gx >= gridSize || gz >= gridSize) return;

        // Don't allow painting on the field post itself
        if (cellFlags[gz][gx] == CELL_POST) return;

        int half = gridSize / 2;
        int wx = postPos.getX() + (gx - half);
        int wz = postPos.getZ() + (gz - half);
        long posKey = BlockPos.asLong(wx, postPos.getY(), wz);

        if (mode == Mode.ERASE) {
            plan.remove(posKey);
            return;
        }
        ToolPaletteList.ToolEntry tool = paletteList.getSelected();
        if (tool == null || tool.isHeader) return;
        plan.put(posKey, tool.toolId);
    }

    private void clampScroll() {
        targetScrollX = Math.max(0, Math.min(targetScrollX, Math.max(0, gridSize - viewCols)));
        targetScrollY = Math.max(0, Math.min(targetScrollY, Math.max(0, gridSize - viewRows)));
        scrollX = Math.max(0, Math.min(scrollX, Math.max(0, gridSize - viewCols)));
        scrollY = Math.max(0, Math.min(scrollY, Math.max(0, gridSize - viewRows)));
    }

    private void applyAndClose() {
        List<String> seeds = new ArrayList<>();
        for (String val : plan.values()) {
            if (!"water".equals(val) && !"auto".equals(val) && !seeds.contains(val))
                seeds.add(val);
        }
        FieldPostConfigSetPayload payload = new FieldPostConfigSetPayload(
                postPos, patternId, tierCap, radius, priority,
                seeds.isEmpty(), seeds, waterEnabled, maxWaterCells,
                groomEnabled, groomRadius, rotationEnabled, rotationPatterns,
                new HashMap<>(plan));
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

    @Override
    public boolean isPauseScreen() { return false; }
}
