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

public class FieldPostScreen extends Screen {

    private static final int CELL = 20;
    private static final int GAP = 1;
    private static final int STRIDE = CELL + GAP;
    private static final int PALETTE_W = 130;
    private static final int TITLE_H = 16;
    private static final int SEARCH_H = 16;

    // Colors
    private static final int BG = 0xFF111111;
    private static final int PANEL_BG = 0xFF1A1A1A;
    private static final int BORDER = 0xFF3A3A3A;
    private static final int ACCENT = 0xFF44AA44;
    private static final int CENTER_MARKER = 0xFFFF8844;
    private static final int PLAN_BORDER = 0xFFFFCC00;
    private static final int TEXT = 0xFFDDDDDD;
    private static final int TEXT_DIM = 0xFF888888;

    private enum CellKind {
        AIR(0xFF1E1E1E), DIRT(0xFF6B5430), FARMLAND_DRY(0xFF5C4020), FARMLAND_WET(0xFF3A2A18),
        WATER(0xFF2244AA), CROP_GROWING(0xFF447722), CROP_MATURE(0xFF77AA33),
        PATH(0xFF907850), POST(0xFFAA6622), SOLID(0xFF3A3A3A);
        final int color;
        CellKind(int c) { this.color = c; }
    }

    // State
    private final BlockPos postPos;
    private final Level level;
    private int gridSize;
    private CellKind[][] worldGrid;
    private ItemStack[][] cropIcons;
    private final Map<Long, String> plan = new HashMap<>();

    // Widgets
    private EditBox searchBox;
    private ToolPaletteList paletteList;
    private final List<ToolPaletteList.ToolEntry> allEntries = new ArrayList<>();

    // Viewport
    private int viewCols, viewRows;
    private float scrollX, scrollY;
    private int vpLeft, vpTop, vpW, vpH;

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

        // Layout
        int margin = 4;
        int palLeft = margin;
        int palTop = TITLE_H + margin;

        vpLeft = palLeft + PALETTE_W + margin;
        vpTop = TITLE_H + margin;
        vpW = width - vpLeft - margin;
        vpH = height - vpTop - margin;
        viewCols = vpW / STRIDE;
        viewRows = vpH / STRIDE;

        // Search box
        searchBox = new EditBox(font, palLeft + 2, palTop, PALETTE_W - 4, SEARCH_H,
                Component.literal("Search"));
        searchBox.setMaxLength(64);
        searchBox.setBordered(true);
        searchBox.setHint(Component.literal("Search..."));
        searchBox.setResponder(text -> filterPalette());
        addRenderableWidget(searchBox);

        // Build tool entries
        buildToolEntries();

        // Tool palette list
        int listTop = palTop + SEARCH_H + 4;
        int listH = height - listTop - margin;
        paletteList = new ToolPaletteList(minecraft, palLeft, PALETTE_W, listH, listTop, entry -> {});
        filterPalette();
        addRenderableWidget(paletteList);

        // Center scroll
        scrollX = Math.max(0, gridSize / 2.0f - viewCols / 2.0f);
        scrollY = Math.max(0, gridSize / 2.0f - viewRows / 2.0f);
        clampScroll();

        scanWorld();
    }

    private void buildToolEntries() {
        allEntries.clear();
        allEntries.add(new ToolPaletteList.ToolEntry("auto", "Auto", new ItemStack(Items.BONE_MEAL)));
        allEntries.add(new ToolPaletteList.ToolEntry("water", "Water", new ItemStack(Items.WATER_BUCKET)));
        allEntries.add(new ToolPaletteList.ToolEntry("erase", "Erase", new ItemStack(Items.BARRIER)));

        for (String seedId : CropDetection.getAllPlantableSeeds()) {
            //? if >=1.21 {
            ResourceLocation rl = ResourceLocation.parse(seedId);
            //?} else {
            /*ResourceLocation rl = new ResourceLocation(seedId);
            *///?}
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item == Items.AIR) continue;
            String name = new ItemStack(item).getHoverName().getString();
            allEntries.add(new ToolPaletteList.ToolEntry(seedId, name, new ItemStack(item)));
        }
    }

    private void filterPalette() {
        String query = searchBox != null ? searchBox.getValue().toLowerCase(Locale.ROOT) : "";
        List<ToolPaletteList.ToolEntry> filtered = new ArrayList<>();
        for (ToolPaletteList.ToolEntry e : allEntries) {
            if (query.isEmpty() || e.label.toLowerCase(Locale.ROOT).contains(query) || e.toolId.contains(query)) {
                filtered.add(e);
            }
        }
        if (paletteList != null) {
            ToolPaletteList.ToolEntry prev = paletteList.getSelected();
            paletteList.replaceEntries(filtered);
            if (prev != null && filtered.contains(prev)) paletteList.setSelected(prev);
        }
    }

    private void scanWorld() {
        worldGrid = new CellKind[gridSize][gridSize];
        cropIcons = new ItemStack[gridSize][gridSize];
        int half = gridSize / 2;
        for (int gz = 0; gz < gridSize; gz++) {
            for (int gx = 0; gx < gridSize; gx++) {
                int wx = postPos.getX() + (gx - half);
                int wz = postPos.getZ() + (gz - half);
                BlockPos at = new BlockPos(wx, postPos.getY(), wz);
                BlockPos below = at.below();
                BlockState stateAt = level.getBlockState(at);
                BlockState stateBelow = level.getBlockState(below);

                if (gx == half && gz == half) {
                    worldGrid[gz][gx] = CellKind.POST;
                } else if (stateAt.getFluidState().is(Fluids.WATER)) {
                    worldGrid[gz][gx] = CellKind.WATER;
                } else if (stateAt.getBlock() instanceof CropBlock crop) {
                    worldGrid[gz][gx] = crop.isMaxAge(stateAt) ? CellKind.CROP_MATURE : CellKind.CROP_GROWING;
                    cropIcons[gz][gx] = cropIcon(stateAt.getBlock());
                } else if (stateAt.getBlock() instanceof BushBlock) {
                    worldGrid[gz][gx] = CellKind.CROP_GROWING;
                    cropIcons[gz][gx] = new ItemStack(stateAt.getBlock().asItem());
                } else if (stateBelow.getBlock() instanceof FarmBlock) {
                    worldGrid[gz][gx] = stateBelow.getValue(FarmBlock.MOISTURE) > 0 ? CellKind.FARMLAND_WET : CellKind.FARMLAND_DRY;
                } else if (stateBelow.is(Blocks.DIRT) || stateBelow.is(Blocks.GRASS_BLOCK) || stateBelow.is(Blocks.COARSE_DIRT)) {
                    worldGrid[gz][gx] = CellKind.DIRT;
                } else if (stateBelow.is(Blocks.DIRT_PATH)) {
                    worldGrid[gz][gx] = CellKind.PATH;
                } else if (stateAt.isAir() && stateBelow.isAir()) {
                    worldGrid[gz][gx] = CellKind.AIR;
                } else {
                    worldGrid[gz][gx] = CellKind.SOLID;
                }
            }
        }
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

    // ── Render ──

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, BG);

        // Title bar
        g.drawString(font, title, 6, 4, TEXT, false);

        // Confirm/cancel (top-right, small)
        int btnSize = 12;
        int checkX = width - 30;
        int cancelX = width - 15;
        boolean hCheck = mouseX >= checkX && mouseX < checkX + btnSize && mouseY >= 2 && mouseY < 2 + btnSize;
        boolean hCancel = mouseX >= cancelX && mouseX < cancelX + btnSize && mouseY >= 2 && mouseY < 2 + btnSize;
        g.fill(checkX, 2, checkX + btnSize, 2 + btnSize, hCheck ? 0xFF44AA44 : 0xFF336633);
        g.drawCenteredString(font, "\u2713", checkX + btnSize / 2, 4, 0xFFFFFFFF);
        g.fill(cancelX, 2, cancelX + btnSize, 2 + btnSize, hCancel ? 0xFFAA4444 : 0xFF663333);
        g.drawCenteredString(font, "\u2717", cancelX + btnSize / 2, 4, 0xFFFFFFFF);

        // Palette background
        g.fill(4, TITLE_H, 4 + PALETTE_W, height - 4, PANEL_BG);
        g.fill(4 + PALETTE_W - 1, TITLE_H, 4 + PALETTE_W, height - 4, BORDER);

        // Grid
        renderGrid(g, mouseX, mouseY);

        // Widgets (EditBox, list) rendered by super
        super.render(g, mouseX, mouseY, partial);

        // Grid tooltip (render after super so it's on top)
        renderGridTooltip(g, mouseX, mouseY);
    }

    private void renderGrid(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(vpLeft - 1, vpTop - 1, vpLeft + vpW + 1, vpTop + vpH + 1, BORDER);
        g.enableScissor(vpLeft, vpTop, vpLeft + vpW, vpTop + vpH);
        g.fill(vpLeft, vpTop, vpLeft + vpW, vpTop + vpH, 0xFF0D0D0D);

        int half = gridSize / 2;
        int startGX = (int) scrollX;
        int startGZ = (int) scrollY;
        float offsetX = -(scrollX - startGX) * STRIDE;
        float offsetY = -(scrollY - startGZ) * STRIDE;

        for (int vy = -1; vy <= viewRows + 1; vy++) {
            for (int vx = -1; vx <= viewCols + 1; vx++) {
                int gx = startGX + vx;
                int gz = startGZ + vy;
                if (gx < 0 || gz < 0 || gx >= gridSize || gz >= gridSize) continue;

                int cx = vpLeft + (int) (vx * STRIDE + offsetX);
                int cy = vpTop + (int) (vy * STRIDE + offsetY);
                if (cx + CELL < vpLeft || cy + CELL < vpTop || cx > vpLeft + vpW || cy > vpTop + vpH) continue;

                CellKind kind = worldGrid[gz][gx];
                g.fill(cx, cy, cx + CELL, cy + CELL, kind.color);

                // Crop icon (centered, filling cell)
                ItemStack icon = cropIcons[gz][gx];
                if (icon != null && !icon.isEmpty()) {
                    g.pose().pushPose();
                    float scale = CELL / 16.0f;
                    g.pose().translate(cx, cy, 100);
                    g.pose().scale(scale, scale, 1.0f);
                    g.renderItem(icon, 0, 0);
                    g.pose().popPose();
                }

                // Water visual
                if (kind == CellKind.WATER) {
                    g.fill(cx + 2, cy + 8, cx + CELL - 2, cy + 12, 0x604488FF);
                }

                // Plan overlay — centered icon filling cell
                int wx = postPos.getX() + (gx - half);
                int wz = postPos.getZ() + (gz - half);
                long posKey = BlockPos.asLong(wx, postPos.getY(), wz);
                String assignment = plan.get(posKey);
                if (assignment != null) {
                    ToolPaletteList.ToolEntry tool = findToolEntry(assignment);
                    if (tool != null) {
                        drawCellBorder(g, cx, cy, PLAN_BORDER);
                        // Centered icon filling the cell
                        g.pose().pushPose();
                        float scale = CELL / 16.0f;
                        g.pose().translate(cx, cy, 200);
                        g.pose().scale(scale, scale, 1.0f);
                        g.renderItem(tool.icon, 0, 0);
                        g.pose().popPose();
                    }
                }

                // Center marker
                if (gx == half && gz == half) {
                    drawCellBorder(g, cx, cy, CENTER_MARKER);
                    g.fill(cx + 8, cy + 4, cx + 12, cy + 16, 0xCCFF8844);
                    g.fill(cx + 4, cy + 8, cx + 16, cy + 12, 0xCCFF8844);
                }

                // Hover
                if (!panning && mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL
                        && mouseX >= vpLeft && mouseX < vpLeft + vpW && mouseY >= vpTop && mouseY < vpTop + vpH) {
                    g.fill(cx, cy, cx + CELL, cy + CELL, 0x30FFFFFF);
                    boolean hasTool = paletteList != null && paletteList.getSelected() != null;
                    drawCellBorder(g, cx, cy, hasTool ? ACCENT : 0xFFFFFFFF);
                }
            }
        }

        g.disableScissor();
    }

    private void renderGridTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (panning || mouseX < vpLeft || mouseX >= vpLeft + vpW || mouseY < vpTop || mouseY >= vpTop + vpH) return;

        int startGX = (int) scrollX;
        int startGZ = (int) scrollY;
        float offsetX = -(scrollX - startGX) * STRIDE;
        float offsetY = -(scrollY - startGZ) * STRIDE;
        int vx = (int) ((mouseX - vpLeft - offsetX) / STRIDE);
        int vy = (int) ((mouseY - vpTop - offsetY) / STRIDE);
        int gx = startGX + vx;
        int gz = startGZ + vy;
        if (gx < 0 || gz < 0 || gx >= gridSize || gz >= gridSize) return;

        int half = gridSize / 2;
        int wx = postPos.getX() + (gx - half);
        int wz = postPos.getZ() + (gz - half);
        CellKind kind = worldGrid[gz][gx];

        List<Component> tips = new ArrayList<>();
        tips.add(Component.literal(String.format("(%d, %d)", wx, wz)));
        tips.add(Component.literal(kindLabel(kind)).withStyle(s -> s.withColor(kind.color & 0x00FFFFFF)));
        long posKey = BlockPos.asLong(wx, postPos.getY(), wz);
        String a = plan.get(posKey);
        if (a != null) {
            ToolPaletteList.ToolEntry t = findToolEntry(a);
            tips.add(Component.literal("Plan: " + (t != null ? t.label : a)).withStyle(s -> s.withColor(0x55FF55)));
        }
        g.renderTooltip(font, tips, Optional.empty(), mouseX, mouseY);
    }

    private void drawCellBorder(GuiGraphics g, int cx, int cy, int color) {
        g.fill(cx, cy, cx + CELL, cy + 1, color);
        g.fill(cx, cy + CELL - 1, cx + CELL, cy + CELL, color);
        g.fill(cx, cy + 1, cx + 1, cy + CELL - 1, color);
        g.fill(cx + CELL - 1, cy + 1, cx + CELL, cy + CELL - 1, color);
    }

    private String kindLabel(CellKind k) {
        return switch (k) {
            case AIR -> "Empty"; case DIRT -> "Dirt"; case FARMLAND_DRY -> "Farmland";
            case FARMLAND_WET -> "Farmland (wet)"; case WATER -> "Water";
            case CROP_GROWING -> "Growing"; case CROP_MATURE -> "Ready";
            case PATH -> "Path"; case POST -> "Field Post"; case SOLID -> "Block";
        };
    }

    private ToolPaletteList.ToolEntry findToolEntry(String id) {
        for (ToolPaletteList.ToolEntry e : allEntries) { if (e.toolId.equals(id)) return e; }
        return null;
    }

    // ── Input ──

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Confirm / cancel
        int btnSize = 12;
        if (my >= 2 && my < 14) {
            if (mx >= width - 30 && mx < width - 18) { applyAndClose(); return true; }
            if (mx >= width - 15 && mx < width - 3) { onClose(); return true; }
        }

        // Right/middle-click in grid = pan
        if ((button == 1 || button == 2) && inGrid(mx, my)) {
            panning = true;
            panStartX = mx; panStartY = my;
            panStartScrollX = scrollX; panStartScrollY = scrollY;
            return true;
        }

        // Left-click in grid = paint
        if (button == 0 && inGrid(mx, my) && paletteList != null && paletteList.getSelected() != null) {
            paintAt(mx, my);
            return true;
        }

        // Let widgets handle their own clicks
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (panning) {
            scrollX = panStartScrollX - (float) (mx - panStartX) / STRIDE;
            scrollY = panStartScrollY - (float) (my - panStartY) / STRIDE;
            clampScroll();
            return true;
        }
        if (button == 0 && inGrid(mx, my) && paletteList != null && paletteList.getSelected() != null) {
            paintAt(mx, my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (panning && (button == 1 || button == 2)) { panning = false; return true; }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my,
                                  //? if >=1.21 {
                                  double scrollXD, double scrollYD) {
                                  //?} else {
                                  /*double scrollYD) {
                                  *///?}
        // Grid scroll
        if (inGrid(mx, my)) {
            if (hasShiftDown()) scrollX -= (float) scrollYD * 2;
            else scrollY -= (float) scrollYD * 2;
            clampScroll();
            return true;
        }
        // Palette list handles its own scroll via the widget
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
        if (key == 263) { scrollX -= 2; clampScroll(); return true; }
        if (key == 262) { scrollX += 2; clampScroll(); return true; }
        if (key == 265) { scrollY -= 2; clampScroll(); return true; }
        if (key == 264) { scrollY += 2; clampScroll(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    private boolean inGrid(double mx, double my) {
        return mx >= vpLeft && mx < vpLeft + vpW && my >= vpTop && my < vpTop + vpH;
    }

    private void paintAt(double mx, double my) {
        int startGX = (int) scrollX;
        int startGZ = (int) scrollY;
        float offsetX = -(scrollX - startGX) * STRIDE;
        float offsetY = -(scrollY - startGZ) * STRIDE;
        int vx = (int) ((mx - vpLeft - offsetX) / STRIDE);
        int vy = (int) ((my - vpTop - offsetY) / STRIDE);
        int gx = startGX + vx;
        int gz = startGZ + vy;
        if (gx < 0 || gz < 0 || gx >= gridSize || gz >= gridSize) return;

        CellKind kind = worldGrid[gz][gx];
        if (kind == CellKind.POST || kind == CellKind.SOLID) return;

        int half = gridSize / 2;
        int wx = postPos.getX() + (gx - half);
        int wz = postPos.getZ() + (gz - half);
        long posKey = BlockPos.asLong(wx, postPos.getY(), wz);

        ToolPaletteList.ToolEntry tool = paletteList.getSelected();
        if (tool == null) return;
        if ("erase".equals(tool.toolId)) plan.remove(posKey);
        else plan.put(posKey, tool.toolId);
    }

    private void clampScroll() {
        scrollX = Math.max(0, Math.min(scrollX, gridSize - viewCols));
        scrollY = Math.max(0, Math.min(scrollY, gridSize - viewRows));
    }

    private void applyAndClose() {
        List<String> seeds = new ArrayList<>();
        for (String val : plan.values()) {
            if (!"water".equals(val) && !"auto".equals(val) && !"erase".equals(val) && !seeds.contains(val))
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
