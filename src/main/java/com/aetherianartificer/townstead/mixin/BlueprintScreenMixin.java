package com.aetherianartificer.townstead.mixin;

//? if forge {
/*import com.aetherianartificer.townstead.TownsteadNetwork;
*///?}
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.mixin.accessor.BlueprintScreenAccessor;
import com.aetherianartificer.townstead.profession.ProfessionClientStore;
import com.aetherianartificer.townstead.profession.ProfessionQueryPayload;
import com.aetherianartificer.townstead.profession.ProfessionSetPayload;
import com.aetherianartificer.townstead.shift.ShiftClientStore;
import com.aetherianartificer.townstead.shift.ShiftData;
import com.aetherianartificer.townstead.shift.ShiftSetPayload;
import com.aetherianartificer.townstead.village.VillageResidentClientStore;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.aetherianartificer.townstead.compat.ModCompat;
import net.conczin.mca.MCA;
import net.conczin.mca.client.gui.BlueprintScreen;
import net.conczin.mca.client.gui.widget.TooltipButtonWidget;
import net.conczin.mca.client.gui.widget.WidgetUtils;
//? if neoforge {
import net.conczin.mca.network.Network;
//?} else {
/*import net.conczin.mca.cobalt.network.NetworkHandler;
*///?}
import net.conczin.mca.network.c2s.GetVillageRequest;
import net.conczin.mca.network.c2s.ReportBuildingMessage;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.util.compat.ButtonWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.TagKey;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.net.URL;
import java.util.Enumeration;

@Mixin(BlueprintScreen.class)
public abstract class BlueprintScreenMixin extends Screen {
    @Shadow(remap = false)
    private String page;

    @Shadow(remap = false)
    private Village village;

    @Shadow(remap = false)
    private void setPage(String page) {
    }

    @Shadow(remap = false)
    protected abstract void drawBuildingIcon(GuiGraphics context, ResourceLocation texture, int x, int y, int u, int v);

    @Unique
    private static final String TOWNSTEAD_CATALOG_PAGE = "townstead_catalog";
    @Unique
    private static final String TOWNSTEAD_SHIFT_PAGE = "townstead_shift";
    @Unique
    private static final String TOWNSTEAD_PROFESSION_PAGE = "townstead_profession";
    @Unique
    private static final int NAV_BUTTON_WIDTH = 80;
    @Unique
    private static final int NAV_BUTTON_HEIGHT = 20;
    @Unique
    private static final int NAV_BUTTON_STEP = 22;
    @Unique
    private static final int NAV_VISIBLE_ROWS = 6;
    @Unique
    private static final String KITCHEN_TYPE_PREFIX = "compat/farmersdelight/kitchen_l";
    @Unique
    private static final String CAFE_TYPE_PREFIX = "compat/rusticdelight/cafe_l";
    @Unique
    private static final int ADV_WINDOW_MIN_W = 320;
    @Unique
    private static final int ADV_WINDOW_MIN_H = 188;
    @Unique
    private static final int ADV_WINDOW_MAX_W = 640;
    @Unique
    private static final int ADV_WINDOW_MAX_H = 380;
    @Unique
    private static final int ADV_INSIDE_X = 9;
    @Unique
    private static final int ADV_INSIDE_Y = 18;
    @Unique
    private int ADV_WINDOW_W = ADV_WINDOW_MIN_W;
    @Unique
    private int ADV_WINDOW_H = ADV_WINDOW_MIN_H;
    @Unique
    private int ADV_INSIDE_W = ADV_WINDOW_MIN_W - 18;
    @Unique
    private int ADV_INSIDE_H = ADV_WINDOW_MIN_H - 27;
    @Unique
    private int CATALOG_DETAILS_W = 108;
    @Unique
    private static final ResourceLocation MCA_BUILDING_ICONS = MCA.locate("textures/buildings.png");

    @Unique
    private final List<Button> townstead$navButtons = new ArrayList<>();
    @Unique
    private final Map<Button, Integer> townstead$navBaseY = new IdentityHashMap<>();
    @Unique
    private int townstead$navScrollPx = 0;
    @Unique
    private boolean townstead$redirectingCatalog = false;
    @Unique
    private Button townstead$catalogBackButton;
    @Unique
    private Button townstead$catalogZoomInButton;
    @Unique
    private Button townstead$catalogZoomOutButton;
    @Unique
    private Button townstead$catalogNeedsPrevButton;
    @Unique
    private Button townstead$catalogNeedsNextButton;
    @Unique
    private Button townstead$upgradeBuildingButton;
    @Unique
    private String townstead$catalogReturnPage = "map";

    @Unique
    private List<BuildingType> townstead$catalogEntries = List.of();
    @Unique
    private int townstead$catalogSelected = 0;
    @Unique
    private final List<NodeData> townstead$catalogNodes = new ArrayList<>();
    @Unique
    private double townstead$catalogPanX = 0.0;
    @Unique
    private double townstead$catalogPanY = 0.0;
    @Unique
    private double townstead$catalogZoom = 1.0;
    @Unique
    private boolean townstead$catalogDragging = false;
    @Unique
    private boolean townstead$catalogDragArmed = false;
    @Unique
    private double townstead$dragStartX = 0.0;
    @Unique
    private double townstead$dragStartY = 0.0;
    @Unique
    private double townstead$lastDragX = 0.0;
    @Unique
    private double townstead$lastDragY = 0.0;
    @Unique
    private final Map<String, Optional<ResourceLocation>> townstead$nodeItemIconCache = new HashMap<>();
    @Unique
    private final Map<Long, Optional<ResourceLocation>> townstead$iconUvItemCache = new HashMap<>();
    @Unique
    private int townstead$catalogNeedsPage = 0;
    @Unique
    private int townstead$catalogNeedsRowsPerPage = 1;

    // --- Shift page state ---
    @Unique
    private static final int SHIFT_ROWS_PER_PAGE = 7;
    @Unique
    private static final int SHIFT_CELL_H = 12;
    @Unique
    private static final int SHIFT_NAME_W = 50;
    @Unique
    private Button townstead$shiftNavButton;
    @Unique
    private int townstead$shiftPage = 0;
    @Unique
    private List<UUID> townstead$shiftVillagerUuids = List.of();
    @Unique
    private Map<UUID, String> townstead$shiftVillagerNames = new HashMap<>();
    @Unique
    private Map<UUID, Integer> townstead$shiftVillagerEntityIds = new HashMap<>();
    @Unique
    private final Map<UUID, int[]> townstead$shiftEdits = new HashMap<>();
    @Unique
    private boolean townstead$shiftQueried = false;
    @Unique
    private int townstead$shiftPaintOrdinal = -1; // -1 = cycle mode, 0-3 = paint mode

    // --- Profession page state ---
    @Unique
    private static final int PROF_ROWS_PER_PAGE = 7;
    @Unique
    private int townstead$profPage = 0;
    @Unique
    private List<UUID> townstead$profVillagerUuids = List.of();
    @Unique
    private Map<UUID, String> townstead$profVillagerNames = new HashMap<>();
    @Unique
    private Map<UUID, Integer> townstead$profVillagerEntityIds = new HashMap<>();
    @Unique
    private UUID townstead$profSelectedVillager = null;
    @Unique
    private int townstead$profScroll = 0;

    @Unique
    private record NodeData(int index, BuildingType type, String group, int worldX, int worldY) {
    }

    @Unique
    private record RequirementRow(ResourceLocation id, String name, int qty) {
    }

    private BlueprintScreenMixin() {
        super(Component.empty());
    }

    @Inject(method = "setPage", remap = false, at = @At("HEAD"), cancellable = true)
    private void townstead$redirectCatalogPage(String pageName, CallbackInfo ci) {
        if (!"catalog".equals(pageName) || townstead$redirectingCatalog)
            return;
        if (!TownsteadConfig.USE_TOWNSTEAD_CATALOG.get())
            return;
        if (this.page != null && !this.page.isBlank() && !TOWNSTEAD_CATALOG_PAGE.equals(this.page)) {
            townstead$catalogReturnPage = this.page;
        } else {
            townstead$catalogReturnPage = "map";
        }
        townstead$redirectingCatalog = true;
        setPage(TOWNSTEAD_CATALOG_PAGE);
        townstead$redirectingCatalog = false;
        ci.cancel();
    }

    @Inject(method = "setPage", remap = false, at = @At("TAIL"))
    private void townstead$injectFarmingPage(String pageName, CallbackInfo ci) {
        townstead$collectNavButtons();
        townstead$applyNavScroll();

        if (TOWNSTEAD_SHIFT_PAGE.equals(this.page)) {
            townstead$initShiftPage();
            townstead$setNavVisible(true);
        } else if (TOWNSTEAD_PROFESSION_PAGE.equals(this.page)) {
            townstead$initProfessionPage();
            townstead$setNavVisible(true);
        } else if (TOWNSTEAD_CATALOG_PAGE.equals(this.page)) {
            townstead$recomputeCatalogDims();
            townstead$nodeItemIconCache.clear();
            townstead$buildCatalogEntries();
            townstead$buildCatalogNodes();
            townstead$addCatalogControls();
            townstead$catalogNeedsPage = 0;
            townstead$setNavVisible(false);
        } else if ("map".equals(this.page)) {
            townstead$addUpgradeBuildingControl();
            townstead$setNavVisible(true);
        } else if ("villagers".equals(this.page)) {
            townstead$addVillagersPageControls();
            townstead$setNavVisible(true);
        } else {
            townstead$catalogNodes.clear();
            townstead$catalogDragging = false;
            townstead$catalogDragArmed = false;
            townstead$catalogBackButton = null;
            townstead$catalogZoomInButton = null;
            townstead$catalogZoomOutButton = null;
            townstead$catalogNeedsPrevButton = null;
            townstead$catalogNeedsNextButton = null;
            townstead$catalogNeedsPage = 0;
            townstead$upgradeBuildingButton = null;
            townstead$shiftEdits.clear();
            townstead$shiftQueried = false;
            townstead$profSelectedVillager = null;
            townstead$setNavVisible(true);
        }
        for (Button b : townstead$navButtons) {
            if (!(b.getMessage().getContents() instanceof TranslatableContents t))
                continue;
            if ("gui.blueprint.catalog".equals(t.getKey())) {
                b.active = !TOWNSTEAD_CATALOG_PAGE.equals(this.page);
            }
        }
    }

    //? if neoforge {
    @Inject(method = "render", at = @At("TAIL"))
    //?} else {
    /*@Inject(method = "m_88315_", remap = false, at = @At("TAIL"))
    *///?}
    private void townstead$refreshMapUpgradeButton(GuiGraphics context, int mouseX, int mouseY, float partialTicks,
            CallbackInfo ci) {
        if (!"map".equals(this.page) || townstead$upgradeBuildingButton == null)
            return;
        townstead$upgradeBuildingButton.active = townstead$upgradeTargetTypeAtPlayer() != null;
    }

    @Inject(method = "renderMap", remap = false, at = @At("TAIL"))
    private void townstead$renderCustomIconBuildingBorders(GuiGraphics context, CallbackInfo ci) {
        if (!"map".equals(this.page) || this.village == null) {
            return;
        }

        int mapSize = 75;
        int y = this.height / 2 + 8;
        float sc = Math.min((float) mapSize / (this.village.getBox().getMaxBlockCount() + 3) * 2, 2.0f);

        context.pose().pushPose();
        context.pose().translate(this.width / 2.0, y, 0);
        context.pose().scale(sc, sc, 0.0f);
        context.pose().translate(-this.village.getCenter().getX(), -this.village.getCenter().getZ(), 0);

        for (Building building : this.village.getBuildings().values()) {
            if (!building.isComplete()) {
                continue;
            }
            BuildingType bt = building.getBuildingType();
            if (!bt.isIcon() || townstead$nodeItemForType(bt.name()).isEmpty()) {
                continue;
            }

            BlockPos p0 = building.getPos0();
            BlockPos p1 = building.getPos1();
            WidgetUtils.drawRectangle(context, p0.getX(), p0.getZ(), p1.getX(), p1.getZ(), bt.getColor());

            BlockPos c = building.getCenter();
            drawBuildingIcon(context, MCA_BUILDING_ICONS, c.getX(), c.getZ(), bt.iconU(), bt.iconV());
        }

        context.pose().popPose();
    }

    //? if neoforge {
    @Inject(method = "render", at = @At("TAIL"))
    //?} else {
    /*@Inject(method = "m_88315_", remap = false, at = @At("TAIL"))
    *///?}
    private void townstead$renderCompatCatalog(GuiGraphics context, int mouseX, int mouseY, float partialTicks,
            CallbackInfo ci) {
        if (!TOWNSTEAD_CATALOG_PAGE.equals(this.page))
            return;
        townstead$recomputeCatalogDims();
        int windowX = townstead$catalogWindowX();
        int windowY = townstead$catalogWindowY();
        context.fill(windowX, windowY, windowX + ADV_WINDOW_W, windowY + ADV_WINDOW_H, 0xFFDEDEDE);
        context.fill(windowX + 1, windowY + 1, windowX + ADV_WINDOW_W - 1, windowY + ADV_WINDOW_H - 1, 0xFF2B2F38);
        context.fill(windowX + 3, windowY + 3, windowX + ADV_WINDOW_W - 3, windowY + 14, 0xFF3A3F47);

        int insideX = windowX + ADV_INSIDE_X;
        int insideY = windowY + ADV_INSIDE_Y;
        int insideRight = insideX + ADV_INSIDE_W;
        int insideBottom = insideY + ADV_INSIDE_H;
        int graphX = insideX;
        int graphY = insideY;
        int graphW = ADV_INSIDE_W - CATALOG_DETAILS_W - 2;
        int graphH = ADV_INSIDE_H;
        int graphRight = graphX + graphW;
        int detailsX = graphRight + 2;
        int detailsY = insideY;
        int detailsRight = insideRight;
        int detailsBottom = insideBottom;

        // Reliable drag fallback: only activate after movement threshold while left
        // mouse is held.
        boolean mouseHeld = this.minecraft != null
                && GLFW.glfwGetMouseButton(this.minecraft.getWindow().getWindow(),
                        GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (!mouseHeld) {
            townstead$catalogDragging = false;
            townstead$catalogDragArmed = false;
        } else if (townstead$catalogDragArmed) {
            if (!townstead$catalogDragging) {
                double ddx = mouseX - townstead$dragStartX;
                double ddy = mouseY - townstead$dragStartY;
                if ((ddx * ddx + ddy * ddy) >= 9.0) {
                    townstead$catalogDragging = true;
                }
            }
            if (townstead$catalogDragging) {
                double dx = mouseX - townstead$lastDragX;
                double dy = mouseY - townstead$lastDragY;
                if (dx != 0.0 || dy != 0.0) {
                    townstead$catalogPanX += dx / townstead$catalogZoom;
                    townstead$catalogPanY += dy / townstead$catalogZoom;
                    townstead$lastDragX = mouseX;
                    townstead$lastDragY = mouseY;
                }
            }
        }

        context.enableScissor(graphX, graphY, graphRight, insideBottom);
        context.fill(graphX, graphY, graphRight, insideBottom, 0xFF1B1E24);
        townstead$drawCatalogGrid(context, graphX, graphY, graphW, graphH);
        townstead$drawCatalogConnections(context, graphX, graphY, graphW, graphH);
        townstead$drawCatalogNodes(context, graphX, graphY, graphW, graphH);
        context.disableScissor();

        context.fill(detailsX, detailsY, detailsRight, detailsBottom, 0xFF232A36);
        context.fill(detailsX, detailsY, detailsRight, detailsY + 1, 0xFF8CA2BF);
        context.fill(detailsX, detailsBottom - 1, detailsRight, detailsBottom, 0xFF8CA2BF);
        context.fill(detailsX, detailsY, detailsX + 1, detailsBottom, 0xFF8CA2BF);
        context.fill(detailsRight - 1, detailsY, detailsRight, detailsBottom, 0xFF8CA2BF);
        context.drawCenteredString(this.font, Component.literal("Catalog"), windowX + (ADV_WINDOW_W / 2), windowY + 6,
                0xFFFFFF);

        BuildingType selected = townstead$getSelectedCatalogEntry();
        if (selected == null)
            return;
        int detailsMidY = detailsY + ((detailsBottom - detailsY) / 2);
        context.fill(detailsX + 1, detailsMidY, detailsRight - 1, detailsMidY + 1, 0x446E86A5);

        int detailsTextX = detailsX + 4;
        int detailsTextY = detailsY + 4;
        Component nameComponent = Component.literal(townstead$displayBuildingName(selected.name()));
        context.drawWordWrap(this.font, nameComponent, detailsTextX, detailsTextY, CATALOG_DETAILS_W - 8, 0xFFFFFF);
        detailsTextY += Math.max(this.font.lineHeight + 2,
                this.font.split(nameComponent, CATALOG_DETAILS_W - 8).size() * this.font.lineHeight + 2);
        String tierLine = townstead$tierLine(selected.name());
        if (tierLine != null) {
            context.pose().pushPose();
            context.pose().scale(0.68f, 0.68f, 1.0f);
            context.drawString(this.font, Component.literal(tierLine), (int) Math.floor(detailsTextX / 0.68f),
                    (int) Math.floor(detailsTextY / 0.68f), 0xE3D18A);
            context.pose().popPose();
            detailsTextY += (int) Math.ceil(this.font.lineHeight * 0.68f) + 1;
        }
        String modLine = townstead$modLine(selected.name());
        if (modLine != null) {
            context.pose().pushPose();
            context.pose().scale(0.68f, 0.68f, 1.0f);
            context.drawString(this.font, Component.literal(modLine), (int) Math.floor(detailsTextX / 0.68f),
                    (int) Math.floor(detailsTextY / 0.68f), 0x8FC1FF);
            context.pose().popPose();
            detailsTextY += (int) Math.ceil(this.font.lineHeight * 0.68f) + 2;
        }
        String descKey = "buildingType." + selected.name() + ".description";
        String desc = Component.translatable(descKey).getString();
        if (desc.equals(descKey))
            desc = "No description.";
        Component descComponent = Component.literal(desc);
        context.pose().pushPose();
        context.pose().scale(0.85f, 0.85f, 1.0f);
        int scaledDescX = (int) Math.floor(detailsTextX / 0.85f);
        int scaledDescY = (int) Math.floor(detailsTextY / 0.85f);
        int scaledDescW = (int) Math.floor((CATALOG_DETAILS_W - 8) / 0.85f);
        context.drawWordWrap(this.font, descComponent, scaledDescX, scaledDescY, scaledDescW, 0xA8A8A8);
        context.pose().popPose();

        int needsHeaderY = detailsMidY + 6;
        context.drawString(this.font, Component.literal("Needs"), detailsTextX, needsHeaderY, 0xD0D0D0);
        int pageIndicatorY = needsHeaderY + 2;
        int pageIndicatorX = detailsRight - 50;
        int needsListTop = needsHeaderY + this.font.lineHeight + 4;
        int needsListBottom = detailsBottom - 4;
        List<RequirementRow> allRequirements = townstead$sortedRequirements(selected.getGroups());
        int rowHeight = 12;
        int listHeight = Math.max(0, needsListBottom - needsListTop);
        int rowsPerPage = Math.max(1, listHeight / rowHeight);
        townstead$catalogNeedsRowsPerPage = rowsPerPage;
        int totalPages = Math.max(1, (int) Math.ceil(allRequirements.size() / (double) rowsPerPage));
        townstead$catalogNeedsPage = Math.max(0, Math.min(townstead$catalogNeedsPage, totalPages - 1));
        boolean showPager = totalPages > 1;
        if (showPager) {
            int buttonY = needsHeaderY - 1;
            int nextX = detailsRight - 12;
            int prevX = nextX - 12;
            if (townstead$catalogNeedsPrevButton != null) {
                townstead$catalogNeedsPrevButton.visible = true;
                townstead$catalogNeedsPrevButton.active = townstead$catalogNeedsPage > 0;
                townstead$catalogNeedsPrevButton.setX(prevX);
                townstead$catalogNeedsPrevButton.setY(buttonY);
            }
            if (townstead$catalogNeedsNextButton != null) {
                townstead$catalogNeedsNextButton.visible = true;
                townstead$catalogNeedsNextButton.active = townstead$catalogNeedsPage < (totalPages - 1);
                townstead$catalogNeedsNextButton.setX(nextX);
                townstead$catalogNeedsNextButton.setY(buttonY);
            }
            pageIndicatorX = prevX - 42;
            context.pose().pushPose();
            context.pose().scale(0.72f, 0.72f, 1.0f);
            context.drawString(
                    this.font,
                    Component.literal("[" + (townstead$catalogNeedsPage + 1) + " / " + totalPages + "]"),
                    (int) Math.floor(pageIndicatorX / 0.72f),
                    (int) Math.floor(pageIndicatorY / 0.72f),
                    0xA8BDD8);
            context.pose().popPose();
        } else {
            if (townstead$catalogNeedsPrevButton != null) {
                townstead$catalogNeedsPrevButton.visible = false;
                townstead$catalogNeedsPrevButton.active = false;
            }
            if (townstead$catalogNeedsNextButton != null) {
                townstead$catalogNeedsNextButton.visible = false;
                townstead$catalogNeedsNextButton.active = false;
            }
        }

        int start = townstead$catalogNeedsPage * rowsPerPage;
        int end = Math.min(allRequirements.size(), start + rowsPerPage);
        long ticker = this.minecraft != null && this.minecraft.level != null ? this.minecraft.level.getGameTime()
                : System.currentTimeMillis() / 50L;
        String hovered = null;
        for (int i = start; i < end; i++) {
            RequirementRow row = allRequirements.get(i);
            int rowIndex = i - start;
            int rowY = needsListTop + (rowIndex * rowHeight);
            ItemStack ingredientIcon = townstead$resolveRequirementIcon(row.id(), ticker, i);
            if (!ingredientIcon.isEmpty()) {
                context.pose().pushPose();
                context.pose().scale(0.75f, 0.75f, 1.0f);
                context.renderItem(ingredientIcon, (int) Math.round((detailsTextX + 1) / 0.75f),
                        (int) Math.round((rowY - 2) / 0.75f));
                context.pose().popPose();
            }
            context.pose().pushPose();
            context.pose().scale(0.72f, 0.72f, 1.0f);
            int qtyX = (int) Math.floor((detailsTextX + 15) / 0.72f);
            int textY = (int) Math.floor(rowY / 0.72f);
            String qtyText = row.qty() + "x";
            context.drawString(this.font, Component.literal(qtyText), qtyX, textY, 0xE3D18A);
            int nameX = qtyX + 18;
            int maxNameWidth = Math.max(8, (int) Math.floor((detailsRight - 8) / 0.72f) - nameX);
            context.drawString(this.font, Component.literal(townstead$truncateToWidth(row.name(), maxNameWidth)), nameX,
                    textY, 0x9AD0FF);
            context.pose().popPose();

            int hoverLeft = detailsTextX + 14;
            int hoverRight = detailsRight - 6;
            if (mouseX >= hoverLeft && mouseX <= hoverRight && mouseY >= rowY - 1 && mouseY <= rowY + rowHeight) {
                hovered = row.name();
            }
        }
        if (hovered != null) {
            context.renderTooltip(this.font, Component.literal(hovered), mouseX, mouseY);
        }
    }

    @Inject(method = "drawBuildingIcon", remap = false, at = @At("HEAD"), cancellable = true)
    private void townstead$drawCompatBuildingIcon(
            GuiGraphics context,
            ResourceLocation texture,
            int x,
            int y,
            int u,
            int v,
            CallbackInfo ci) {
        Optional<ResourceLocation> itemId = townstead$nodeItemForIconUv(u, v);
        if (itemId.isEmpty() || !BuiltInRegistries.ITEM.containsKey(itemId.get()))
            return;
        Item item = BuiltInRegistries.ITEM.get(itemId.get());
        if (item == null)
            return;
        ItemStack stack = new ItemStack(item);
        if (stack.isEmpty())
            return;
        // Forge 1.20.1 can leave the replacement item render competing with the
        // map border quad at the same depth. Push only the Forge render forward
        // so it matches the 1.21.1 layering, with the icon clearly above its frame.
        context.pose().pushPose();
        //? if forge {
        context.pose().translate(x - 6.0, y - 6.0, 200.0);
        //?} else {
        /*context.pose().translate(x - 6.0, y - 6.0, 0.0);
        *///?}
        context.pose().scale(0.75f, 0.75f, 1.0f);
        context.renderItem(stack, 0, 0);
        context.pose().popPose();
        ci.cancel();
    }

    @Unique
    private Optional<ResourceLocation> townstead$nodeItemForIconUv(int u, int v) {
        long key = (((long) u) << 32) ^ (v & 0xFFFFFFFFL);
        return townstead$iconUvItemCache.computeIfAbsent(key, ignored -> {
            ResourceLocation resolved = null;
            for (BuildingType bt : BuildingTypes.getInstance()) {
                if (bt.iconU() != u || bt.iconV() != v)
                    continue;
                Optional<ResourceLocation> candidate = townstead$nodeItemForType(bt.name());
                if (candidate.isEmpty() || !BuiltInRegistries.ITEM.containsKey(candidate.get()))
                    continue;
                if (resolved == null) {
                    resolved = candidate.get();
                    continue;
                }
                if (!resolved.equals(candidate.get())) {
                    // Ambiguous icon slot: do not override vanilla icon rendering.
                    return Optional.empty();
                }
            }
            return Optional.ofNullable(resolved);
        });
    }

    //? if neoforge {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (townstead$dispatchScroll(mouseX, mouseY, verticalAmount))
            return true;
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    //?} else {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (townstead$dispatchScroll(mouseX, mouseY, delta))
            return true;
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
    *///?}

    @Unique
    private boolean townstead$dispatchScroll(double mouseX, double mouseY, double verticalAmount) {
        if (townstead$handleCatalogScroll(mouseX, mouseY, verticalAmount))
            return true;
        if (townstead$handleShiftScroll(mouseX, mouseY, verticalAmount))
            return true;
        if (townstead$handleProfessionScroll(mouseX, mouseY, verticalAmount))
            return true;
        return townstead$handleNavScroll(mouseX, mouseY, verticalAmount);
    }

    @Unique
    private boolean townstead$handleCatalogScroll(double mouseX, double mouseY, double verticalAmount) {
        if (!TOWNSTEAD_CATALOG_PAGE.equals(this.page))
            return false;
        int direction = verticalAmount > 0 ? 1 : (verticalAmount < 0 ? -1 : 0);
        if (direction == 0)
            return false;
        int windowX = townstead$catalogWindowX();
        int windowY = townstead$catalogWindowY();
        int insideX = windowX + ADV_INSIDE_X;
        int insideY = windowY + ADV_INSIDE_Y;
        int graphRight = insideX + (ADV_INSIDE_W - CATALOG_DETAILS_W - 2);
        int insideBottom = insideY + ADV_INSIDE_H;
        double focalX = mouseX;
        double focalY = mouseY;
        if (mouseX < insideX || mouseX > graphRight || mouseY < insideY || mouseY > insideBottom) {
            focalX = insideX + ((ADV_INSIDE_W - CATALOG_DETAILS_W - 2) / 2.0);
            focalY = insideY + (ADV_INSIDE_H / 2.0);
        }
        townstead$applyCatalogZoom(direction, focalX, focalY, insideX, insideY);
        return true;
    }

    @Unique
    private boolean townstead$handleNavScroll(double mouseX, double mouseY, double verticalAmount) {
        if (townstead$navButtons.isEmpty())
            return false;
        int left = this.width / 2 - 180;
        int top = this.height / 2 - 56;
        int right = left + NAV_BUTTON_WIDTH;
        int bottom = top + (NAV_VISIBLE_ROWS * NAV_BUTTON_STEP);
        if (!(mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom))
            return false;
        int overflowRows = Math.max(0, townstead$navButtons.size() - NAV_VISIBLE_ROWS);
        if (overflowRows <= 0)
            return false;
        int maxScroll = overflowRows * NAV_BUTTON_STEP;
        if (verticalAmount < 0) {
            townstead$navScrollPx = Math.max(-maxScroll, townstead$navScrollPx - NAV_BUTTON_STEP);
        } else if (verticalAmount > 0) {
            townstead$navScrollPx = Math.min(0, townstead$navScrollPx + NAV_BUTTON_STEP);
        }
        townstead$applyNavScroll();
        return true;
    }

    //? if neoforge {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_6375_", remap = false, at = @At("HEAD"), cancellable = true)
    *///?}
    private void townstead$catalogMouseClicked(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (!TOWNSTEAD_CATALOG_PAGE.equals(this.page) || button != 0)
            return;
        int windowX = townstead$catalogWindowX();
        int windowY = townstead$catalogWindowY();
        int insideX = windowX + ADV_INSIDE_X;
        int insideY = windowY + ADV_INSIDE_Y;
        int graphW = ADV_INSIDE_W - CATALOG_DETAILS_W - 2;
        int detailsX = insideX + graphW + 2;
        int detailsY = insideY;
        int detailsRight = insideX + ADV_INSIDE_W;
        int detailsBottom = insideY + ADV_INSIDE_H;
        if (mouseX >= detailsX && mouseX <= detailsRight && mouseY >= detailsY && mouseY <= detailsBottom) {
            return;
        }
        int insideRight = insideX + (ADV_INSIDE_W - CATALOG_DETAILS_W - 2);
        int insideBottom = insideY + ADV_INSIDE_H;
        if (mouseX < insideX || mouseX > insideRight || mouseY < insideY || mouseY > insideBottom)
            return;

        townstead$catalogDragging = false;
        townstead$catalogDragArmed = true;
        townstead$dragStartX = mouseX;
        townstead$dragStartY = mouseY;
        townstead$lastDragX = mouseX;
        townstead$lastDragY = mouseY;
        int clickedIndex = townstead$findCatalogNodeAt(mouseX, mouseY, insideX, insideY);
        if (clickedIndex >= 0 && clickedIndex != townstead$catalogSelected)
            townstead$catalogSelected = clickedIndex;
        cir.setReturnValue(true);
        cir.cancel();
    }

    //? if neoforge {
    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_7979_", remap = false, at = @At("HEAD"), cancellable = true)
    *///?}
    private void townstead$catalogMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY,
            CallbackInfoReturnable<Boolean> cir) {
        if (!TOWNSTEAD_CATALOG_PAGE.equals(this.page) || button != 0)
            return;
        int windowX = townstead$catalogWindowX();
        int windowY = townstead$catalogWindowY();
        int insideX = windowX + ADV_INSIDE_X;
        int insideY = windowY + ADV_INSIDE_Y;
        int detailsX = insideX + (ADV_INSIDE_W - CATALOG_DETAILS_W - 2) + 2;
        int detailsRight = insideX + ADV_INSIDE_W;
        int detailsBottom = insideY + ADV_INSIDE_H;
        if (mouseX >= detailsX && mouseX <= detailsRight && mouseY >= insideY && mouseY <= detailsBottom) {
            cir.setReturnValue(true);
            cir.cancel();
            return;
        }
        if (!townstead$catalogDragArmed)
            return;
        if (!townstead$catalogDragging) {
            double ddx = mouseX - townstead$dragStartX;
            double ddy = mouseY - townstead$dragStartY;
            if ((ddx * ddx + ddy * ddy) < 9.0) {
                cir.setReturnValue(true);
                cir.cancel();
                return;
            }
            townstead$catalogDragging = true;
        }
        townstead$catalogPanX += dragX / townstead$catalogZoom;
        townstead$catalogPanY += dragY / townstead$catalogZoom;
        townstead$lastDragX = mouseX;
        townstead$lastDragY = mouseY;
        cir.setReturnValue(true);
        cir.cancel();
    }

    //? if neoforge {
    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_6348_", remap = false, at = @At("HEAD"), cancellable = true)
    *///?}
    private void townstead$catalogMouseReleased(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (!TOWNSTEAD_CATALOG_PAGE.equals(this.page) || button != 0)
            return;
        townstead$catalogDragging = false;
        townstead$catalogDragArmed = false;
        cir.setReturnValue(true);
        cir.cancel();
    }

    //? if neoforge {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_7933_", remap = false, at = @At("HEAD"), cancellable = true)
    *///?}
    private void townstead$catalogKeyScroll(int keyCode, int scanCode, int modifiers,
            CallbackInfoReturnable<Boolean> cir) {
        if (!TOWNSTEAD_CATALOG_PAGE.equals(this.page))
            return;
        BuildingType selected = townstead$getSelectedCatalogEntry();
        if (selected == null)
            return;
        int pages = townstead$needsPageCount(selected.getGroups());
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_LEFT_BRACKET) {
            if (townstead$catalogNeedsPage > 0) {
                townstead$catalogNeedsPage--;
                cir.setReturnValue(true);
                cir.cancel();
            }
            return;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN || keyCode == GLFW.GLFW_KEY_RIGHT_BRACKET) {
            if (townstead$catalogNeedsPage < (pages - 1)) {
                townstead$catalogNeedsPage++;
                cir.setReturnValue(true);
                cir.cancel();
            }
        }
    }

    @Unique
    private void townstead$recomputeCatalogDims() {
        int w = Math.max(ADV_WINDOW_MIN_W, Math.min(ADV_WINDOW_MAX_W, this.width - 40));
        int h = Math.max(ADV_WINDOW_MIN_H, Math.min(ADV_WINDOW_MAX_H, this.height - 40));
        ADV_WINDOW_W = w;
        ADV_WINDOW_H = h;
        ADV_INSIDE_W = w - 18;
        ADV_INSIDE_H = h - 27;
        CATALOG_DETAILS_W = Math.max(108, Math.min(180, (int) Math.round(w * 0.32)));
    }

    @Unique
    private int townstead$catalogWindowX() {
        return (this.width - ADV_WINDOW_W) / 2;
    }

    @Unique
    private int townstead$catalogWindowY() {
        return (this.height - ADV_WINDOW_H) / 2;
    }

    @Unique
    private void townstead$addCatalogControls() {
        int windowX = townstead$catalogWindowX();
        int windowY = townstead$catalogWindowY();
        townstead$catalogBackButton = addRenderableWidget(new ButtonWidget(
                windowX + 2,
                windowY + 2,
                40,
                14,
                Component.translatable("townstead.gui.back"),
                b -> setPage(townstead$catalogReturnPage)));
        townstead$catalogZoomOutButton = addRenderableWidget(new ButtonWidget(
                windowX + ADV_WINDOW_W - 40,
                windowY + 2,
                16,
                14,
                Component.literal("-"),
                b -> townstead$applyCatalogZoom(-1,
                        windowX + ADV_INSIDE_X + ((ADV_INSIDE_W - CATALOG_DETAILS_W - 2) / 2.0),
                        windowY + ADV_INSIDE_Y + (ADV_INSIDE_H / 2.0), windowX + ADV_INSIDE_X,
                        windowY + ADV_INSIDE_Y)));
        townstead$catalogZoomInButton = addRenderableWidget(new ButtonWidget(
                windowX + ADV_WINDOW_W - 22,
                windowY + 2,
                16,
                14,
                Component.literal("+"),
                b -> townstead$applyCatalogZoom(1,
                        windowX + ADV_INSIDE_X + ((ADV_INSIDE_W - CATALOG_DETAILS_W - 2) / 2.0),
                        windowY + ADV_INSIDE_Y + (ADV_INSIDE_H / 2.0), windowX + ADV_INSIDE_X,
                        windowY + ADV_INSIDE_Y)));
        int detailsX = windowX + ADV_INSIDE_X + (ADV_INSIDE_W - CATALOG_DETAILS_W - 2) + 2;
        int detailsY = windowY + ADV_INSIDE_Y;
        int detailsMidY = detailsY + (ADV_INSIDE_H / 2);
        int needsHeaderY = detailsMidY + 6;
        int buttonY = needsHeaderY - 1;
        int rightEdge = detailsX + CATALOG_DETAILS_W - 5;
        townstead$catalogNeedsPrevButton = addRenderableWidget(new ButtonWidget(
                rightEdge - 24,
                buttonY,
                10,
                10,
                Component.literal("<"),
                b -> {
                    if (townstead$catalogNeedsPage > 0)
                        townstead$catalogNeedsPage--;
                }));
        townstead$catalogNeedsNextButton = addRenderableWidget(new ButtonWidget(
                rightEdge - 11,
                buttonY,
                10,
                10,
                Component.literal(">"),
                b -> {
                    BuildingType selected = townstead$getSelectedCatalogEntry();
                    if (selected == null)
                        return;
                    int pages = townstead$needsPageCount(selected.getGroups());
                    if (townstead$catalogNeedsPage < (pages - 1))
                        townstead$catalogNeedsPage++;
                }));
    }

    @Unique
    private void townstead$addUpgradeBuildingControl() {
        int bx = this.width / 2 + 180 - 64 - 16;
        int by = this.height / 2 - 56 + 22 * 6;
        townstead$upgradeBuildingButton = addRenderableWidget(new TooltipButtonWidget(
                bx,
                by,
                96,
                20,
                Component.translatable("townstead.blueprint.upgradeBuilding"),
                Component.translatable("townstead.blueprint.upgradeBuilding.tooltip"),
                b -> townstead$tryUpgradeCurrentBuilding()));
        String next = townstead$upgradeTargetTypeAtPlayer();
        townstead$upgradeBuildingButton.active = next != null;
    }

    @Unique
    private void townstead$tryUpgradeCurrentBuilding() {
        String nextType = townstead$upgradeTargetTypeAtPlayer();
        if (nextType == null)
            return;
        //? if neoforge {
        Network.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.FORCE_TYPE, nextType));
        Network.sendToServer(new GetVillageRequest());
        //?} else {
        /*NetworkHandler.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.FORCE_TYPE, nextType));
        NetworkHandler.sendToServer(new GetVillageRequest());
        *///?}
        BlueprintScreenAccessor accessor = (BlueprintScreenAccessor) (Object) this;
        accessor.townstead$invokeSetPage("map");
    }

    @Unique
    private String townstead$upgradeTargetTypeAtPlayer() {
        if (this.minecraft == null || this.minecraft.player == null)
            return null;
        BlueprintScreenAccessor accessor = (BlueprintScreenAccessor) (Object) this;
        if (accessor.townstead$getVillage() == null)
            return null;
        BlockPos pos = this.minecraft.player.blockPosition();
        for (Building building : accessor.townstead$getVillage().getBuildings().values()) {
            if (!building.containsPos(pos))
                continue;
            return townstead$highestSatisfiableUpgradeType(building);
        }
        return null;
    }

    @Unique
    private String townstead$highestSatisfiableUpgradeType(Building building) {
        String current = building.getType();
        if (current == null)
            return null;
        int idx = current.lastIndexOf("_l");
        if (idx < 0 || idx >= current.length() - 2)
            return null;
        String tierText = current.substring(idx + 2);
        if (!tierText.chars().allMatch(Character::isDigit))
            return null;

        int startTier;
        try {
            startTier = Integer.parseInt(tierText);
        } catch (NumberFormatException ignored) {
            return null;
        }
        if (startTier <= 0)
            return null;

        String prefix = current.substring(0, idx + 2);
        String best = null;
        for (int tier = startTier + 1; tier < startTier + 20; tier++) {
            String candidateType = prefix + tier;
            if (!BuildingTypes.getInstance().getBuildingTypes().containsKey(candidateType))
                break;
            BuildingType candidate = BuildingTypes.getInstance().getBuildingType(candidateType);
            if (candidate == null)
                break;
            if (townstead$buildingMeetsRequirements(building, candidate)) {
                best = candidateType;
            } else {
                break;
            }
        }
        return best;
    }

    @Unique
    private boolean townstead$buildingMeetsRequirements(Building building, BuildingType targetType) {
        if (building == null || targetType == null)
            return false;
        Map<ResourceLocation, Integer> liveCounts = townstead$collectLiveBlockCounts(building);
        for (Map.Entry<ResourceLocation, Integer> req : targetType.getGroups().entrySet()) {
            int have = townstead$countMatchingRequirementBlocks(liveCounts, req.getKey());
            if (have < req.getValue())
                return false;
        }
        return true;
    }

    @Unique
    private Map<ResourceLocation, Integer> townstead$collectLiveBlockCounts(Building building) {
        Map<ResourceLocation, Integer> counts = new HashMap<>();
        if (this.minecraft == null)
            return counts;
        ClientLevel level = this.minecraft.level;
        if (level == null)
            return counts;

        BlockPos p0 = building.getPos0();
        BlockPos p1 = building.getPos1();
        for (BlockPos pos : BlockPos.betweenClosed(p0, p1)) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir())
                continue;
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (id == null)
                continue;
            counts.merge(id, 1, Integer::sum);
        }
        return counts;
    }

    @Unique
    private int townstead$countMatchingRequirementBlocks(Map<ResourceLocation, Integer> presentCounts,
            ResourceLocation requirement) {
        if (BuiltInRegistries.BLOCK.containsKey(requirement)) {
            return presentCounts.getOrDefault(requirement, 0);
        }
        TagKey<Block> blockTag = TagKey.create(Registries.BLOCK, requirement);
        int total = 0;
        for (Map.Entry<ResourceLocation, Integer> entry : presentCounts.entrySet()) {
            ResourceLocation blockId = entry.getKey();
            if (!BuiltInRegistries.BLOCK.containsKey(blockId))
                continue;
            Block block = BuiltInRegistries.BLOCK.get(blockId);
            if (!block.defaultBlockState().is(blockTag))
                continue;
            total += entry.getValue();
        }
        return total;
    }

    @Unique
    private void townstead$applyCatalogZoom(int direction, double px, double py, int insideX, int insideY) {
        double worldX = (px - insideX) / townstead$catalogZoom - townstead$catalogPanX;
        double worldY = (py - insideY) / townstead$catalogZoom - townstead$catalogPanY;
        double step = direction > 0 ? 1.12 : 0.88;
        double newZoom = Math.max(0.55, Math.min(2.0, townstead$catalogZoom * step));
        townstead$catalogZoom = newZoom;
        townstead$catalogPanX = (px - insideX) / newZoom - worldX;
        townstead$catalogPanY = (py - insideY) / newZoom - worldY;
    }

    @Unique
    private void townstead$setNavVisible(boolean visible) {
        for (Button b : townstead$navButtons) {
            b.visible = visible;
            b.active = visible;
        }
    }

    @Unique
    private void townstead$buildCatalogEntries() {
        List<BuildingType> all = new ArrayList<>(BuildingTypes.getInstance().getBuildingTypes().values());
        Set<String> builtTypes = new HashSet<>();
        BlueprintScreenAccessor accessor = (BlueprintScreenAccessor) (Object) this;
        if (accessor.townstead$getVillage() != null) {
            for (Building building : accessor.townstead$getVillage().getBuildings().values()) {
                builtTypes.add(building.getType());
            }
        }
        all = all.stream()
                .filter(BuildingType::visible)
                .filter(bt -> ModCompat.isCompatAvailable(bt.name()))
                .sorted(Comparator.comparing(this::townstead$catalogSortKey))
                .collect(Collectors.toList());
        townstead$catalogEntries = all;
        townstead$catalogSelected = Math.max(0, Math.min(townstead$catalogSelected, Math.max(0, all.size() - 1)));
    }

    @Unique
    private void townstead$buildCatalogNodes() {
        townstead$catalogNodes.clear();
        if (townstead$catalogEntries.isEmpty())
            return;

        Map<String, List<Integer>> grouped = new LinkedHashMap<>();
        for (int i = 0; i < townstead$catalogEntries.size(); i++) {
            BuildingType type = townstead$catalogEntries.get(i);
            String group = townstead$compatGroupLabel(type.name());
            grouped.computeIfAbsent(group, ignored -> new ArrayList<>()).add(i);
        }

        int y = 16;
        for (Map.Entry<String, List<Integer>> entry : grouped.entrySet()) {
            List<Integer> indices = entry.getValue();
            int maxBottom = y + 24;
            int col = 0;
            int row = 0;
            for (int index : indices) {
                BuildingType type = townstead$catalogEntries.get(index);
                String name = type.name();
                int nodeX;
                int nodeY;
                if (name.startsWith(KITCHEN_TYPE_PREFIX)) {
                    int tier = 1;
                    try {
                        tier = Integer.parseInt(name.substring(KITCHEN_TYPE_PREFIX.length()));
                    } catch (NumberFormatException ignored) {
                    }
                    nodeX = 24 + (tier - 1) * 56;
                    nodeY = y + 8;
                } else if (name.startsWith(CAFE_TYPE_PREFIX)) {
                    int tier = 1;
                    try {
                        tier = Integer.parseInt(name.substring(CAFE_TYPE_PREFIX.length()));
                    } catch (NumberFormatException ignored) {
                    }
                    nodeX = 24 + (tier - 1) * 56;
                    nodeY = y + 8;
                } else {
                    nodeX = 24 + col * 56;
                    nodeY = y + 8 + row * 42;
                    col++;
                    if (col >= 4) {
                        col = 0;
                        row++;
                    }
                }
                townstead$catalogNodes.add(new NodeData(index, type, entry.getKey(), nodeX, nodeY));
                maxBottom = Math.max(maxBottom, nodeY + 30);
            }
            y = maxBottom + 26;
        }
    }

    @Unique
    private boolean townstead$isCatalogEntryVisible(String typeName, Set<String> builtTypes) {
        return true;
    }

    @Unique
    private void townstead$drawCatalogGrid(GuiGraphics context, int insideX, int insideY, int insideW, int insideH) {
        int spacing = Math.max(14, (int) Math.round(20 * townstead$catalogZoom));
        int offsetX = (int) Math.round((townstead$catalogPanX * townstead$catalogZoom) % spacing);
        int offsetY = (int) Math.round((townstead$catalogPanY * townstead$catalogZoom) % spacing);
        for (int x = insideX - spacing + offsetX; x <= insideX + insideW; x += spacing) {
            context.fill(x, insideY, x + 1, insideY + insideH, 0x182A2F38);
        }
        for (int y = insideY - spacing + offsetY; y <= insideY + insideH; y += spacing) {
            context.fill(insideX, y, insideX + insideW, y + 1, 0x182A2F38);
        }
    }

    @Unique
    private void townstead$drawCatalogConnections(GuiGraphics context, int insideX, int insideY, int insideW,
            int insideH) {
        for (String prefix : new String[] { KITCHEN_TYPE_PREFIX, CAFE_TYPE_PREFIX }) {
            for (int tier = 1; tier < 5; tier++) {
                NodeData from = null;
                NodeData to = null;
                String fromId = prefix + tier;
                String toId = prefix + (tier + 1);
                for (NodeData node : townstead$catalogNodes) {
                    String name = node.type().name();
                    if (fromId.equals(name))
                        from = node;
                    if (toId.equals(name))
                        to = node;
                }
                if (from == null || to == null)
                    continue;
                int x1 = insideX
                        + (int) Math.round((from.worldX() + 26 + townstead$catalogPanX) * townstead$catalogZoom);
                int y1 = insideY
                        + (int) Math.round((from.worldY() + 13 + townstead$catalogPanY) * townstead$catalogZoom);
                int x2 = insideX + (int) Math.round((to.worldX() + townstead$catalogPanX) * townstead$catalogZoom);
                int y2 = insideY
                        + (int) Math.round((to.worldY() + 13 + townstead$catalogPanY) * townstead$catalogZoom);
                int minY = Math.min(y1, y2);
                int maxY = Math.max(y1, y2);
                context.fill(Math.min(x1, x2), minY, Math.max(x1, x2) + 1, maxY + 1, 0xFFA6B6CC);
            }
        }
    }

    @Unique
    private void townstead$drawCatalogNodes(GuiGraphics context, int insideX, int insideY, int insideW, int insideH) {
        for (NodeData node : townstead$catalogNodes) {
            int screenX = insideX + (int) Math.round((node.worldX() + townstead$catalogPanX) * townstead$catalogZoom);
            int screenY = insideY + (int) Math.round((node.worldY() + townstead$catalogPanY) * townstead$catalogZoom);
            int nodeW = Math.max(16, (int) Math.round(26 * townstead$catalogZoom));
            int nodeH = Math.max(16, (int) Math.round(26 * townstead$catalogZoom));

            int border = node.index() == townstead$catalogSelected ? 0xFFD9E9FF : 0xFF6D7A8D;
            int fill = node.index() == townstead$catalogSelected ? 0xFF3A4D66 : 0xFF2A3342;
            context.fill(screenX - 1, screenY - 1, screenX + nodeW + 1, screenY + nodeH + 1, border);
            context.fill(screenX, screenY, screenX + nodeW, screenY + nodeH, fill);

            townstead$drawNodeIcon(context, node, screenX, screenY, nodeW, nodeH);
        }
    }

    @Unique
    private ItemStack townstead$resolveNodeIcon(BuildingType type) {
        Optional<ResourceLocation> configured = townstead$nodeItemForType(type.name());
        if (configured.isPresent() && BuiltInRegistries.ITEM.containsKey(configured.get())) {
            Item item = BuiltInRegistries.ITEM.get(configured.get());
            if (item != null)
                return new ItemStack(item);
        }
        for (ResourceLocation requirement : type.getGroups().keySet()) {
            if (BuiltInRegistries.BLOCK.containsKey(requirement)) {
                Item item = BuiltInRegistries.BLOCK.get(requirement).asItem();
                if (item != null)
                    return new ItemStack(item);
            }
            if (BuiltInRegistries.ITEM.containsKey(requirement)) {
                Item item = BuiltInRegistries.ITEM.get(requirement);
                if (item != null)
                    return new ItemStack(item);
            }
        }
        return ItemStack.EMPTY;
    }

    @Unique
    private Optional<ResourceLocation> townstead$nodeItemForType(String buildingTypeName) {
        Optional<ResourceLocation> cached = townstead$nodeItemIconCache.get(buildingTypeName);
        if (cached != null && cached.isPresent()) {
            return cached;
        }
        Optional<ResourceLocation> result = Optional.empty();
        try {
            String relPath = buildingTypeName.startsWith("compat/")
                    ? "townstead_compat/building_types/" + buildingTypeName + ".json"
                    : "data/mca/building_types/" + buildingTypeName + ".json";
            ClassLoader cl = BlueprintScreenMixin.class.getClassLoader();
            if (cl != null) {
                Enumeration<URL> urls = cl.getResources(relPath);
                while (urls.hasMoreElements()) {
                    URL url = urls.nextElement();
                    try (InputStream in = url.openStream();
                            InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                        JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                        if (obj.has("townsteadNodeItem")) {
                            //? if >=1.21 {
                            result = Optional.of(ResourceLocation.parse(obj.get("townsteadNodeItem").getAsString()));
                            //?} else {
                            /*result = Optional.of(new ResourceLocation(obj.get("townsteadNodeItem").getAsString()));
                            *///?}
                            break;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            result = Optional.empty();
        }
        if (result.isPresent()) {
            townstead$nodeItemIconCache.put(buildingTypeName, result);
        }
        return result;
    }

    @Unique
    private void townstead$drawNodeIcon(GuiGraphics context, NodeData node, int screenX, int screenY, int nodeW,
            int nodeH) {
        BuildingType type = node.type();
        if (!type.name().startsWith("compat/")) {
            int centerX = screenX + (nodeW / 2);
            int centerY = screenY + (nodeH / 2);
            this.drawBuildingIcon(context, MCA_BUILDING_ICONS, centerX, centerY, type.iconU(), type.iconV());
            return;
        }
        ItemStack icon = townstead$resolveNodeIcon(type);
        if (icon.isEmpty())
            return;
        int iconX = screenX + nodeW / 2 - 8;
        int iconY = screenY + nodeH / 2 - 8;
        context.renderItem(icon, iconX, iconY);
    }

    @Unique
    private int townstead$findCatalogNodeAt(double mouseX, double mouseY, int insideX, int insideY) {
        for (int i = townstead$catalogNodes.size() - 1; i >= 0; i--) {
            NodeData node = townstead$catalogNodes.get(i);
            int screenX = insideX + (int) Math.round((node.worldX() + townstead$catalogPanX) * townstead$catalogZoom);
            int screenY = insideY + (int) Math.round((node.worldY() + townstead$catalogPanY) * townstead$catalogZoom);
            int nodeW = Math.max(16, (int) Math.round(26 * townstead$catalogZoom));
            int nodeH = Math.max(16, (int) Math.round(26 * townstead$catalogZoom));
            if (mouseX >= screenX && mouseX <= screenX + nodeW && mouseY >= screenY && mouseY <= screenY + nodeH) {
                return node.index();
            }
        }
        return -1;
    }

    @Unique
    private BuildingType townstead$getSelectedCatalogEntry() {
        if (townstead$catalogEntries.isEmpty())
            return null;
        int idx = Math.max(0, Math.min(townstead$catalogSelected, townstead$catalogEntries.size() - 1));
        return townstead$catalogEntries.get(idx);
    }

    @Unique
    private String townstead$catalogSortKey(BuildingType type) {
        String group = townstead$compatGroupLabel(type.name());
        String name = townstead$displayBuildingName(type.name());
        return group + "|" + name;
    }

    @Unique
    private String townstead$compatGroupLabel(String name) {
        if (!name.startsWith("compat/"))
            return "Core";
        String[] parts = name.split("/");
        if (parts.length < 2)
            return "Compat";
        String mod = parts[1];
        if ("farmersdelight".equals(mod))
            return "Farmer's Delight";
        if ("rusticdelight".equals(mod))
            return "Rustic Delight";
        return mod.substring(0, 1).toUpperCase(Locale.ROOT) + mod.substring(1);
    }

    @Unique
    private String townstead$modLine(String buildingTypeId) {
        if (!buildingTypeId.startsWith("compat/"))
            return null;
        return townstead$compatGroupLabel(buildingTypeId);
    }

    @Unique
    private String townstead$tierLine(String buildingTypeId) {
        int tier = -1;
        if (buildingTypeId.matches(".*_l\\d+$")) {
            int idx = buildingTypeId.lastIndexOf("_l");
            try {
                tier = Integer.parseInt(buildingTypeId.substring(idx + 2));
            } catch (NumberFormatException ignored) {
                tier = -1;
            }
        }
        if (tier <= 0)
            return null;
        return "Tier " + townstead$roman(tier);
    }

    @Unique
    private String townstead$roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> Integer.toString(value);
        };
    }

    @Unique
    private String townstead$displayBuildingName(String buildingTypeId) {
        String key = "buildingType." + buildingTypeId;
        String translated = Component.translatable(key).getString();
        if (!translated.equals(key))
            return translated;
        String[] parts = buildingTypeId.split("/");
        String raw = parts[parts.length - 1];
        String[] words = raw.split("_");
        StringBuilder out = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty())
                continue;
            if (!out.isEmpty())
                out.append(' ');
            out.append(w.substring(0, 1).toUpperCase(Locale.ROOT)).append(w.substring(1));
        }
        return out.toString();
    }

    @Unique
    private String townstead$displayRequirementName(ResourceLocation id) {
        if (BuiltInRegistries.BLOCK.containsKey(id)) {
            Block block = BuiltInRegistries.BLOCK.get(id);
            return Component.translatable(block.getDescriptionId()).getString();
        }
        if (BuiltInRegistries.ITEM.containsKey(id)) {
            Item item = BuiltInRegistries.ITEM.get(id);
            return Component.translatable(item.getDescriptionId()).getString();
        }
        String tagPath = id.toString().replace(':', '.').replace('/', '.');
        String slashKey = "tag.block." + tagPath;
        String dottedKey = "tag.item." + tagPath;
        String slash = Component.translatable(slashKey).getString();
        if (!slash.equals(slashKey))
            return slash;
        String dotted = Component.translatable(dottedKey).getString();
        if (!dotted.equals(dottedKey))
            return dotted;
        String fallback = id.getPath().replace('_', ' ');
        if (fallback.endsWith("s") && fallback.length() > 3) {
            fallback = fallback.substring(0, fallback.length() - 1);
        }
        String[] words = fallback.split(" ");
        StringBuilder out = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty())
                continue;
            if (!out.isEmpty())
                out.append(' ');
            out.append(w.substring(0, 1).toUpperCase(Locale.ROOT)).append(w.substring(1));
        }
        return out.toString();
    }

    @Unique
    private ItemStack townstead$resolveRequirementIcon(ResourceLocation id, long ticker, int salt) {
        if (BuiltInRegistries.BLOCK.containsKey(id)) {
            Item item = BuiltInRegistries.BLOCK.get(id).asItem();
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        }
        if (BuiltInRegistries.ITEM.containsKey(id)) {
            Item item = BuiltInRegistries.ITEM.get(id);
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        }

        List<Item> candidates = new ArrayList<>();
        TagKey<Block> blockTag = TagKey.create(Registries.BLOCK, id);
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!block.defaultBlockState().is(blockTag))
                continue;
            Item item = block.asItem();
            if (item == null || item == ItemStack.EMPTY.getItem())
                continue;
            candidates.add(item);
        }
        if (candidates.isEmpty()) {
            TagKey<Item> itemTag = TagKey.create(Registries.ITEM, id);
            for (Item item : BuiltInRegistries.ITEM) {
                if (item.builtInRegistryHolder().is(itemTag)) {
                    candidates.add(item);
                }
            }
        }
        if (candidates.isEmpty())
            return ItemStack.EMPTY;
        int idx = (int) Math.floorMod((ticker / 20L) + salt, candidates.size());
        return new ItemStack(candidates.get(idx));
    }

    @Unique
    private String townstead$truncate(String text, int visibleChars) {
        if (text.length() <= visibleChars)
            return text;
        return text.substring(0, Math.max(1, visibleChars - 1)) + "…";
    }

    @Unique
    private String townstead$truncateToWidth(String text, int maxWidth) {
        if (this.font == null || maxWidth <= 0)
            return text;
        if (this.font.width(text) <= maxWidth)
            return text;
        String ellipsis = "…";
        int ellipsisWidth = this.font.width(ellipsis);
        if (ellipsisWidth >= maxWidth)
            return ellipsis;
        int end = text.length();
        while (end > 1) {
            String candidate = text.substring(0, end) + ellipsis;
            if (this.font.width(candidate) <= maxWidth)
                return candidate;
            end--;
        }
        return ellipsis;
    }

    @Unique
    private List<RequirementRow> townstead$sortedRequirements(Map<ResourceLocation, Integer> requirements) {
        return requirements.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<ResourceLocation, Integer> e) -> e.getKey().toString())
                        .thenComparingInt(Map.Entry::getValue))
                .map(e -> new RequirementRow(e.getKey(), townstead$displayRequirementName(e.getKey()), e.getValue()))
                .toList();
    }

    @Unique
    private int townstead$needsPageCount(Map<ResourceLocation, Integer> requirements) {
        int total = requirements.size();
        int rows = Math.max(1, townstead$catalogNeedsRowsPerPage);
        return Math.max(1, (int) Math.ceil(total / (double) rows));
    }

    @Unique
    private void townstead$collectNavButtons() {
        townstead$navButtons.clear();
        townstead$navBaseY.clear();

        int navX = this.width / 2 - 180;
        for (GuiEventListener listener : this.children()) {
            if (!(listener instanceof Button b))
                continue;
            if (b.getWidth() != NAV_BUTTON_WIDTH)
                continue;
            if (b.getHeight() != NAV_BUTTON_HEIGHT)
                continue;
            if (b.getX() != navX)
                continue;
            townstead$navButtons.add(b);
        }
        townstead$navButtons.sort(Comparator.comparingInt(Button::getY));
        for (Button b : townstead$navButtons) {
            townstead$navBaseY.put(b, b.getY());
        }
    }

    @Unique
    private void townstead$applyNavScroll() {
        for (Button b : townstead$navButtons) {
            Integer baseY = townstead$navBaseY.get(b);
            if (baseY == null)
                continue;
            b.setY(baseY + townstead$navScrollPx);
        }
    }


    // =====================================================================
    // Shift Manager page
    // =====================================================================

    @Unique
    private void townstead$addVillagersPageControls() {
        // Position mirroring the nav column: same Y as "Map" button, right side,
        // matching the padding/width of the map page's right-side buttons.
        int x = this.width / 2 + 100;
        int y = this.height / 2 - 56;
        addRenderableWidget(new TooltipButtonWidget(
                x, y, 96, 20,
                Component.translatable("gui.blueprint.shifts"),
                Component.empty(),
                b -> setPage(TOWNSTEAD_SHIFT_PAGE)));
        addRenderableWidget(new TooltipButtonWidget(
                x, y + 22, 96, 20,
                Component.translatable("gui.blueprint.professions"),
                Component.empty(),
                b -> setPage(TOWNSTEAD_PROFESSION_PAGE)));
    }

    @Unique
    private int townstead$shiftGridLeft() {
        return this.width / 2 - 80 + SHIFT_NAME_W + 4;
    }

    @Unique
    private int townstead$shiftGridRight() {
        return this.width / 2 + 176;
    }

    @Unique
    private int townstead$shiftCellW() {
        return (townstead$shiftGridRight() - townstead$shiftGridLeft()) / ShiftData.HOURS_PER_DAY;
    }

    @Unique
    private void townstead$initShiftPage() {
        townstead$shiftPage = 0;
        townstead$shiftEdits.clear();
        townstead$shiftQueried = false;
        townstead$shiftPaintOrdinal = -1;
        townstead$refreshShiftVillagers();

        // Controls row at the top
        int topY = this.height / 2 - 74;
        int leftX = this.width / 2 - 80;

        // Back button
        addRenderableWidget(new ButtonWidget(
                leftX, topY, 40, 14,
                Component.translatable("townstead.gui.back"),
                b -> setPage("villagers")));

        // Pagination buttons (right-aligned)
        int rightEdge = townstead$shiftGridRight();
        addRenderableWidget(new ButtonWidget(
                rightEdge - 42, topY, 20, 14,
                Component.literal(">"),
                b -> townstead$shiftPageDelta(1)));
        addRenderableWidget(new ButtonWidget(
                rightEdge - 64, topY, 20, 14,
                Component.literal("<"),
                b -> townstead$shiftPageDelta(-1)));

        // Reset all button — bottom-aligned with the Refresh nav button
        int refreshBottom = this.height / 2 - 56 + 22 * 5 + 20;
        addRenderableWidget(new ButtonWidget(
                rightEdge - 60, refreshBottom - 14, 60, 14,
                Component.translatable("townstead.shift.reset"),
                b -> townstead$resetAllShifts()));

        // Query shift data for visible villagers
        townstead$queryShiftData();
    }

    @Unique
    private void townstead$populateShiftVillagers() {
        townstead$refreshShiftVillagers();
    }

    @Unique
    private void townstead$refreshShiftVillagers() {
        townstead$shiftVillagerUuids = new ArrayList<>();
        townstead$shiftVillagerNames.clear();
        townstead$shiftVillagerEntityIds.clear();

        for (VillageResidentClientStore.Resident resident : VillageResidentClientStore.getResidents()) {
            UUID uuid = resident.villagerUuid();
            townstead$shiftVillagerUuids.add(uuid);
            townstead$shiftVillagerNames.put(uuid, resident.name());
            ShiftClientStore.set(uuid, resident.shifts());
        }

        townstead$shiftVillagerUuids.sort(Comparator.comparing(
                uuid -> townstead$shiftVillagerNames.getOrDefault(uuid, uuid.toString())));
        townstead$shiftPage = Math.max(0, Math.min(townstead$shiftPage, townstead$shiftTotalPages() - 1));
    }

    @Unique
    private void townstead$queryShiftData() {
        if (townstead$shiftQueried) return;
        townstead$shiftQueried = true;
        //? if neoforge {
        PacketDistributor.sendToServer(new ProfessionQueryPayload());
        //?} else if forge {
        /*TownsteadNetwork.sendToServer(new ProfessionQueryPayload());
        *///?}
    }

    @Unique
    private void townstead$shiftPageDelta(int delta) {
        int totalPages = townstead$shiftTotalPages();
        townstead$shiftPage = Math.max(0, Math.min(townstead$shiftPage + delta, totalPages - 1));
    }

    @Unique
    private int townstead$shiftTotalPages() {
        int count = townstead$shiftVillagerUuids.size();
        return Math.max(1, (int) Math.ceil(count / (double) SHIFT_ROWS_PER_PAGE));
    }

    @Unique
    private void townstead$resetAllShifts() {
        int[] defaults = ShiftData.getVanillaDefault();
        for (UUID uuid : townstead$shiftVillagerUuids) {
            townstead$shiftEdits.put(uuid, Arrays.copyOf(defaults, defaults.length));
            //? if neoforge {
            PacketDistributor.sendToServer(new ShiftSetPayload(uuid, Arrays.copyOf(defaults, defaults.length)));
            //?} else if forge {
            /*TownsteadNetwork.sendToServer(new ShiftSetPayload(uuid, Arrays.copyOf(defaults, defaults.length)));
            *///?}
        }
    }

    //? if neoforge {
    @Inject(method = "render", at = @At("TAIL"))
    //?} else {
    /*@Inject(method = "m_88315_", remap = false, at = @At("TAIL"))
    *///?}
    private void townstead$renderShiftPage(GuiGraphics context, int mouseX, int mouseY, float partialTicks,
            CallbackInfo ci) {
        if (!TOWNSTEAD_SHIFT_PAGE.equals(this.page))
            return;
        townstead$refreshShiftVillagers();

        int leftX = this.width / 2 - 80;
        int gridX = townstead$shiftGridLeft();
        int gridRight = townstead$shiftGridRight();
        int cellW = townstead$shiftCellW();
        int titleY = this.height / 2 - 74;

        // Title (centered between back button and pagination)
        int titleCenterX = (leftX + 42 + gridRight - 66) / 2;
        context.drawCenteredString(this.font, Component.translatable("townstead.shift.title"),
                titleCenterX, titleY + 3, 0xFFFFFF);

        // Page indicator (to the left of the < > buttons)
        int totalPages = townstead$shiftTotalPages();
        String pageText = String.format("%d/%d", townstead$shiftPage + 1, totalPages);
        context.drawString(this.font, Component.literal(pageText),
                gridRight - 66 - this.font.width(pageText) - 4, titleY + 4, 0xA0A0A0, false);

        // Grid content Y
        int gridY = this.height / 2 - 48;

        // Draw hour labels (every hour, half-scale)
        for (int h = 0; h < ShiftData.HOURS_PER_DAY; h++) {
            int displayHour = ShiftData.toDisplayHour(h);
            String label = String.valueOf(displayHour);
            int lx = gridX + h * cellW;
            context.pose().pushPose();
            context.pose().translate(lx + cellW / 2.0f, gridY - 2, 0);
            context.pose().scale(0.5f, 0.5f, 1.0f);
            context.drawString(this.font, label, -this.font.width(label) / 2, -this.font.lineHeight, 0xC0C0C0, false);
            context.pose().popPose();
        }

        // Draw villager rows
        int startIdx = townstead$shiftPage * SHIFT_ROWS_PER_PAGE;
        int endIdx = Math.min(startIdx + SHIFT_ROWS_PER_PAGE, townstead$shiftVillagerUuids.size());

        for (int row = 0; row < endIdx - startIdx; row++) {
            UUID uuid = townstead$shiftVillagerUuids.get(startIdx + row);
            String name = townstead$shiftVillagerNames.getOrDefault(uuid, "???");
            int rowY = gridY + row * (SHIFT_CELL_H + 2);

            // Truncate name to fit
            String truncated = name;
            while (this.font.width(truncated) > SHIFT_NAME_W - 2 && truncated.length() > 1) {
                truncated = truncated.substring(0, truncated.length() - 1);
            }
            if (!truncated.equals(name)) truncated += "..";

            context.drawString(this.font, truncated,
                    leftX, rowY + (SHIFT_CELL_H - this.font.lineHeight) / 2 + 1, 0xFFFFFF, false);

            // Get shifts (prefer local edits, then client store)
            int[] shifts = townstead$shiftEdits.containsKey(uuid)
                    ? townstead$shiftEdits.get(uuid)
                    : ShiftClientStore.get(uuid);

            // Draw 24 colored cells (no text - colors are self-explanatory with legend)
            for (int h = 0; h < ShiftData.HOURS_PER_DAY; h++) {
                int cellX = gridX + h * cellW;
                int cellY = rowY;
                int ord = shifts[h];
                if (ord < 0 || ord >= ShiftData.ORDINAL_COLORS.length) ord = ShiftData.ORD_IDLE;

                int color = ShiftData.ORDINAL_COLORS[ord];
                context.fill(cellX, cellY, cellX + cellW - 1, cellY + SHIFT_CELL_H - 1, color);

                // Hover highlight
                if (mouseX >= cellX && mouseX < cellX + cellW - 1
                        && mouseY >= cellY && mouseY < cellY + SHIFT_CELL_H - 1) {
                    context.fill(cellX, cellY, cellX + cellW - 1, cellY + SHIFT_CELL_H - 1, 0x40FFFFFF);
                }
            }
        }

        // Legend row — bottom-aligned with the Refresh nav button
        // Clickable: select a legend to enter paint mode, click again to deselect
        int refreshBottom = this.height / 2 - 56 + 22 * 5 + 20;
        int legendY = refreshBottom - 11;
        int legendX = leftX;
        for (int i = 0; i < ShiftData.ORDINAL_COLORS.length; i++) {
            int lx = legendX + i * 42;
            boolean selected = townstead$shiftPaintOrdinal == i;
            // Selection highlight: draw a border around the selected legend item
            if (selected) {
                context.fill(lx - 2, legendY - 2, lx + 40, legendY + 11, 0xFFFFFFFF);
                context.fill(lx - 1, legendY - 1, lx + 39, legendY + 10, 0xFF000000);
            }
            context.fill(lx, legendY, lx + 8, legendY + 8, ShiftData.ORDINAL_COLORS[i]);
            context.drawString(this.font, Component.translatable(ShiftData.ORDINAL_TO_KEY[i]),
                    lx + 10, legendY, selected ? 0xFFFFFF : 0xC0C0C0, false);
        }

        // Tooltip for hovered villager name
        for (int row = 0; row < endIdx - startIdx; row++) {
            int rowY = gridY + row * (SHIFT_CELL_H + 2);
            if (mouseX >= leftX && mouseX < gridX && mouseY >= rowY && mouseY < rowY + SHIFT_CELL_H) {
                UUID uuid = townstead$shiftVillagerUuids.get(startIdx + row);
                VillageResidentClientStore.Resident resident = VillageResidentClientStore.get(uuid);
                if (resident != null) {
                    String profName = townstead$profDisplayName(resident.professionId());
                    int level = resident.professionLevel();
                    String levelKey = "townstead.profession.level." + Math.min(Math.max(level, 1), 5);
                    String levelName = Component.translatable(levelKey).getString();
                    context.renderTooltip(this.font,
                            Component.literal(profName + " - " + levelName),
                            mouseX, mouseY);
                }
                break;
            }
        }

        // Tooltip for hovered cell
        int totalGridW = ShiftData.HOURS_PER_DAY * cellW;
        if (mouseX >= gridX && mouseX < gridX + totalGridW) {
            int h = (mouseX - gridX) / cellW;
            if (h >= 0 && h < ShiftData.HOURS_PER_DAY) {
                int hoveredRow = -1;
                for (int row = 0; row < endIdx - startIdx; row++) {
                    int rowY = gridY + row * (SHIFT_CELL_H + 2);
                    if (mouseY >= rowY && mouseY < rowY + SHIFT_CELL_H) {
                        hoveredRow = row;
                        break;
                    }
                }
                if (hoveredRow >= 0) {
                    UUID uuid = townstead$shiftVillagerUuids.get(startIdx + hoveredRow);
                    int[] shifts = townstead$shiftEdits.containsKey(uuid)
                            ? townstead$shiftEdits.get(uuid)
                            : ShiftClientStore.get(uuid);
                    int displayHour = ShiftData.toDisplayHour(h);
                    String hourStr = ShiftData.formatHour(displayHour);
                    int ord = shifts[h];
                    if (ord < 0 || ord >= ShiftData.ORDINAL_TO_KEY.length) ord = ShiftData.ORD_IDLE;
                    String activityName = Component.translatable(ShiftData.ORDINAL_TO_KEY[ord]).getString();
                    String villagerName = townstead$shiftVillagerNames.getOrDefault(uuid, "???");
                    context.renderTooltip(this.font,
                            Component.literal(villagerName + " @ " + hourStr + ": " + activityName),
                            mouseX, mouseY);
                }
            }
        }
    }

    //? if neoforge {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_6375_", remap = false, at = @At("HEAD"), cancellable = true)
    *///?}
    private void townstead$shiftMouseClicked(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (!TOWNSTEAD_SHIFT_PAGE.equals(this.page) || button != 0)
            return;

        // Check legend clicks (toggle paint mode)
        int leftX = this.width / 2 - 80;
        int refreshBottom = this.height / 2 - 56 + 22 * 5 + 20;
        int legendY = refreshBottom - 11;
        if (mouseY >= legendY - 2 && mouseY <= legendY + 11) {
            for (int i = 0; i < ShiftData.ORDINAL_COLORS.length; i++) {
                int lx = leftX + i * 42;
                if (mouseX >= lx - 2 && mouseX <= lx + 40) {
                    townstead$shiftPaintOrdinal = (townstead$shiftPaintOrdinal == i) ? -1 : i;
                    cir.setReturnValue(true);
                    cir.cancel();
                    return;
                }
            }
        }

        // Grid cell click
        if (townstead$shiftApplyCell(mouseX, mouseY)) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }

    @Unique
    private boolean townstead$shiftApplyCell(double mouseX, double mouseY) {
        int gridX = townstead$shiftGridLeft();
        int cellW = townstead$shiftCellW();
        int gridY = this.height / 2 - 48;

        if (mouseX < gridX || mouseX >= gridX + ShiftData.HOURS_PER_DAY * cellW)
            return false;

        int h = (int) ((mouseX - gridX) / cellW);
        if (h < 0 || h >= ShiftData.HOURS_PER_DAY)
            return false;

        int startIdx = townstead$shiftPage * SHIFT_ROWS_PER_PAGE;
        int endIdx = Math.min(startIdx + SHIFT_ROWS_PER_PAGE, townstead$shiftVillagerUuids.size());

        for (int row = 0; row < endIdx - startIdx; row++) {
            int rowY = gridY + row * (SHIFT_CELL_H + 2);
            if (mouseY >= rowY && mouseY < rowY + SHIFT_CELL_H) {
                UUID uuid = townstead$shiftVillagerUuids.get(startIdx + row);
                int[] existing = townstead$shiftEdits.containsKey(uuid)
                        ? townstead$shiftEdits.get(uuid)
                        : ShiftClientStore.get(uuid);
                int[] shifts = Arrays.copyOf(existing, existing.length);

                if (townstead$shiftPaintOrdinal >= 0) {
                    // Paint mode: set to selected activity
                    if (shifts[h] == townstead$shiftPaintOrdinal) return true; // already painted
                    shifts[h] = townstead$shiftPaintOrdinal;
                } else {
                    // Cycle mode: IDLE -> WORK -> MEET -> REST -> IDLE
                    shifts[h] = (shifts[h] + 1) % ShiftData.ORDINAL_TO_ACTIVITY.length;
                }

                townstead$shiftEdits.put(uuid, shifts);
                //? if neoforge {
                PacketDistributor.sendToServer(new ShiftSetPayload(uuid, Arrays.copyOf(shifts, shifts.length)));
                //?} else if forge {
                /*TownsteadNetwork.sendToServer(new ShiftSetPayload(uuid, Arrays.copyOf(shifts, shifts.length)));
                *///?}
                return true;
            }
        }
        return false;
    }

    //? if neoforge {
    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_7979_", remap = false, at = @At("HEAD"), cancellable = true)
    *///?}
    private void townstead$shiftMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY,
            CallbackInfoReturnable<Boolean> cir) {
        if (!TOWNSTEAD_SHIFT_PAGE.equals(this.page) || button != 0)
            return;
        // Only paint while dragging if in paint mode
        if (townstead$shiftPaintOrdinal < 0)
            return;
        if (townstead$shiftApplyCell(mouseX, mouseY)) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }

    @Unique
    private boolean townstead$handleShiftScroll(double mouseX, double mouseY, double verticalAmount) {
        if (!TOWNSTEAD_SHIFT_PAGE.equals(this.page))
            return false;
        int gridX = townstead$shiftGridLeft();
        int cellW = townstead$shiftCellW();
        int gridY = this.height / 2 - 48;
        int gridRight = gridX + ShiftData.HOURS_PER_DAY * cellW;
        int gridBottom = gridY + SHIFT_ROWS_PER_PAGE * (SHIFT_CELL_H + 2);
        if (mouseX < gridX || mouseX > gridRight || mouseY < gridY || mouseY > gridBottom)
            return false;
        if (verticalAmount < 0) {
            townstead$shiftPageDelta(1);
        } else if (verticalAmount > 0) {
            townstead$shiftPageDelta(-1);
        } else {
            return false;
        }
        return true;
    }

    // =====================================================================
    // Profession Manager page
    // =====================================================================

    @Unique
    private void townstead$initProfessionPage() {
        townstead$profPage = 0;
        townstead$profSelectedVillager = null;
        townstead$profScroll = 0;
        townstead$refreshProfVillagers();

        int leftX = this.width / 2 - 80;
        int topY = this.height / 2 - 74;

        // Back button
        addRenderableWidget(new ButtonWidget(
                leftX, topY, 40, 14,
                Component.translatable("townstead.gui.back"),
                b -> setPage("villagers")));

        // Villager list pagination (right-aligned with profession panel)
        int profRight = this.width / 2 + 176;
        addRenderableWidget(new ButtonWidget(
                profRight - 20, topY, 20, 14,
                Component.literal(">"),
                b -> townstead$profPageDelta(1)));
        addRenderableWidget(new ButtonWidget(
                profRight - 42, topY, 20, 14,
                Component.literal("<"),
                b -> townstead$profPageDelta(-1)));

        // Profession list scroll buttons — bottom-aligned with Refresh button
        int profPanelX = this.width / 2 + 48;
        int refreshBottom = this.height / 2 - 56 + 22 * 5 + 20;
        int scrollBtnY = refreshBottom - 14;
        addRenderableWidget(new ButtonWidget(
                profPanelX, scrollBtnY, 20, 14,
                Component.literal("\u25B2"),
                b -> townstead$profScroll = Math.max(0, townstead$profScroll - 1)));
        addRenderableWidget(new ButtonWidget(
                profRight - 20, scrollBtnY, 20, 14,
                Component.literal("\u25BC"),
                b -> townstead$profScroll++));

        // Query available professions from server
        //? if neoforge {
        PacketDistributor.sendToServer(new ProfessionQueryPayload());
        //?} else if forge {
        /*TownsteadNetwork.sendToServer(new ProfessionQueryPayload());
        *///?}
    }

    @Unique
    private void townstead$populateProfVillagers() {
        townstead$refreshProfVillagers();
    }

    @Unique
    private void townstead$refreshProfVillagers() {
        townstead$profVillagerUuids = new ArrayList<>();
        townstead$profVillagerNames.clear();
        townstead$profVillagerEntityIds.clear();

        for (VillageResidentClientStore.Resident resident : VillageResidentClientStore.getResidents()) {
            UUID uuid = resident.villagerUuid();
            townstead$profVillagerUuids.add(uuid);
            townstead$profVillagerNames.put(uuid, resident.name());
        }

        townstead$profVillagerUuids.sort(Comparator.comparing(
                uuid -> townstead$profVillagerNames.getOrDefault(uuid, uuid.toString())));
        int totalPages = Math.max(1, (int) Math.ceil(townstead$profVillagerUuids.size() / (double) PROF_ROWS_PER_PAGE));
        townstead$profPage = Math.max(0, Math.min(townstead$profPage, totalPages - 1));
        if (townstead$profSelectedVillager != null && VillageResidentClientStore.get(townstead$profSelectedVillager) == null) {
            townstead$profSelectedVillager = null;
        }
    }

    @Unique
    private void townstead$profPageDelta(int delta) {
        int totalPages = Math.max(1, (int) Math.ceil(townstead$profVillagerUuids.size() / (double) PROF_ROWS_PER_PAGE));
        townstead$profPage = Math.max(0, Math.min(townstead$profPage + delta, totalPages - 1));
    }

    @Unique
    private String townstead$profDisplayName(String professionId) {
        if ("minecraft:none".equals(professionId)) {
            return Component.translatable("townstead.profession.none").getString();
        }
        // Try standard villager profession translation key patterns
        //? if >=1.21 {
        ResourceLocation id = ResourceLocation.parse(professionId);
        //?} else {
        /*ResourceLocation id = new ResourceLocation(professionId);
        *///?}
        // Vanilla: "entity.minecraft.villager.farmer"
        // Modded: "entity.mca.villager.guard"
        String key = "entity." + id.getNamespace() + ".villager." + id.getPath();
        String translated = Component.translatable(key).getString();
        if (!translated.equals(key)) return translated;
        // Fallback: capitalize the path
        String path = id.getPath();
        if (path.isEmpty()) return professionId;
        return path.substring(0, 1).toUpperCase(Locale.ROOT) + path.substring(1);
    }

    @Unique
    private String townstead$currentProfessionId(VillagerEntityMCA mca) {
        VillagerProfession prof = mca.getVillagerData().getProfession();
        ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(prof);
        return key != null ? key.toString() : "minecraft:none";
    }

    @Unique
    private String townstead$currentProfessionId(UUID villagerUuid) {
        VillageResidentClientStore.Resident resident = VillageResidentClientStore.get(villagerUuid);
        return resident != null ? resident.professionId() : "minecraft:none";
    }

    //? if neoforge {
    @Inject(method = "render", at = @At("TAIL"))
    //?} else {
    /*@Inject(method = "m_88315_", remap = false, at = @At("TAIL"))
    *///?}
    private void townstead$renderProfessionPage(GuiGraphics context, int mouseX, int mouseY, float partialTicks,
            CallbackInfo ci) {
        if (!TOWNSTEAD_PROFESSION_PAGE.equals(this.page))
            return;
        townstead$refreshProfVillagers();

        int leftX = this.width / 2 - 80;
        int topY = this.height / 2 - 74;
        int listRight = this.width / 2 + 40;
        int profPanelX = listRight + 8;
        int profPanelRight = this.width / 2 + 176;

        // Title (centered between back button and pagination)
        int titleCenterX = (leftX + 42 + profPanelRight - 44) / 2;
        context.drawCenteredString(this.font, Component.translatable("townstead.profession.title"),
                titleCenterX, topY + 3, 0xFFFFFF);

        // Page indicator (to the left of < > buttons)
        int totalPages = Math.max(1, (int) Math.ceil(townstead$profVillagerUuids.size() / (double) PROF_ROWS_PER_PAGE));
        String pageText = String.format("%d/%d", townstead$profPage + 1, totalPages);
        context.drawString(this.font, Component.literal(pageText),
                profPanelRight - 44 - this.font.width(pageText) - 4, topY + 4, 0xA0A0A0, false);

        // Villager list
        int listY = this.height / 2 - 48;
        int rowH = 14;
        int startIdx = townstead$profPage * PROF_ROWS_PER_PAGE;
        int endIdx = Math.min(startIdx + PROF_ROWS_PER_PAGE, townstead$profVillagerUuids.size());

        for (int row = 0; row < endIdx - startIdx; row++) {
            UUID uuid = townstead$profVillagerUuids.get(startIdx + row);
            String name = townstead$profVillagerNames.getOrDefault(uuid, "???");
            int rowY = listY + row * (rowH + 1);

            // Highlight selected
            boolean selected = uuid.equals(townstead$profSelectedVillager);
            if (selected) {
                context.fill(leftX - 1, rowY - 1, listRight + 1, rowY + rowH, 0x40FFFFFF);
            }

            // Hover highlight
            if (mouseX >= leftX && mouseX < listRight && mouseY >= rowY && mouseY < rowY + rowH) {
                context.fill(leftX, rowY, listRight, rowY + rowH, 0x20FFFFFF);
            }

            // Name (left)
            String truncName = name;
            int maxNameW = 54;
            while (this.font.width(truncName) > maxNameW && truncName.length() > 1) {
                truncName = truncName.substring(0, truncName.length() - 1);
            }
            if (!truncName.equals(name)) truncName += "..";
            context.drawString(this.font, truncName,
                    leftX + 2, rowY + (rowH - this.font.lineHeight) / 2 + 1, 0xFFFFFF, false);

            // Current profession (right, smaller)
            String profText = townstead$profDisplayName(townstead$currentProfessionId(uuid));
            context.pose().pushPose();
            int profTextX = leftX + 58;
            context.pose().translate(profTextX, rowY + (rowH - this.font.lineHeight * 0.7f) / 2 + 1, 0);
            context.pose().scale(0.7f, 0.7f, 1.0f);
            context.drawString(this.font, profText, 0, 0, 0xA0A0A0, false);
            context.pose().popPose();
        }

        // Right panel: available professions for selected villager
        if (townstead$profSelectedVillager != null) {
            List<String> available = ProfessionClientStore.getProfessions();

            // Get the selected villager's current profession
            String currentProfId = townstead$currentProfessionId(townstead$profSelectedVillager);

            // Draw profession buttons with scroll support
            int btnH = 14;
            int btnW = profPanelRight - profPanelX;
            int panelBottom = this.height / 2 - 56 + 22 * 5 + 20 - 16;
            int maxVisible = (panelBottom - listY) / (btnH + 1);
            int maxScroll = Math.max(0, available.size() - maxVisible);
            townstead$profScroll = Math.max(0, Math.min(townstead$profScroll, maxScroll));

            context.enableScissor(profPanelX, listY, profPanelRight, panelBottom);
            for (int i = 0; i < available.size(); i++) {
                String profId = available.get(i);
                int by = listY + (i - townstead$profScroll) * (btnH + 1);
                if (by + btnH < listY || by > panelBottom) continue;

                boolean isCurrent = profId.equals(currentProfId);
                boolean isFull = ProfessionClientStore.isFull(i) && !isCurrent;
                int maxS = ProfessionClientStore.getMax(i);
                int usedS = ProfessionClientStore.getUsed(i);

                // Background: green=current, red=full, gray=available
                int bgColor;
                if (isCurrent) {
                    bgColor = 0xFF3A6A3A;
                } else if (isFull) {
                    bgColor = 0xFF5A2A2A;
                } else {
                    bgColor = 0xFF333333;
                }
                if (!isFull && mouseX >= profPanelX && mouseX < profPanelRight && mouseY >= by && mouseY < by + btnH) {
                    bgColor = isCurrent ? 0xFF4A8A4A : 0xFF555555;
                }
                context.fill(profPanelX, by, profPanelRight, by + btnH, bgColor);

                // Label with slot count for limited professions
                String displayName = townstead$profDisplayName(profId);
                if (maxS >= 0) {
                    displayName += " (" + usedS + "/" + maxS + ")";
                }
                String truncDisplay = displayName;
                while (this.font.width(truncDisplay) > btnW - 4 && truncDisplay.length() > 1) {
                    truncDisplay = truncDisplay.substring(0, truncDisplay.length() - 1);
                }
                if (!truncDisplay.equals(displayName)) truncDisplay += "..";
                int textColor = isFull ? 0xFF6666 : (isCurrent ? 0xFFFFFF : 0xC0C0C0);
                context.drawString(this.font, truncDisplay,
                        profPanelX + 2, by + (btnH - this.font.lineHeight) / 2 + 1,
                        textColor, false);
            }
            context.disableScissor();
        } else {
            // No villager selected - show hint
            context.drawCenteredString(this.font, Component.translatable("townstead.profession.select"),
                    (profPanelX + profPanelRight) / 2, this.height / 2, 0x808080);
        }
    }

    //? if neoforge {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_6375_", remap = false, at = @At("HEAD"), cancellable = true)
    *///?}
    private void townstead$professionMouseClicked(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (!TOWNSTEAD_PROFESSION_PAGE.equals(this.page) || button != 0)
            return;

        int leftX = this.width / 2 - 80;
        int listRight = this.width / 2 + 40;
        int profPanelX = listRight + 8;
        int profPanelRight = this.width / 2 + 176;
        int listY = this.height / 2 - 48;
        int rowH = 14;

        // Check villager list clicks
        int startIdx = townstead$profPage * PROF_ROWS_PER_PAGE;
        int endIdx = Math.min(startIdx + PROF_ROWS_PER_PAGE, townstead$profVillagerUuids.size());

        if (mouseX >= leftX && mouseX < listRight) {
            for (int row = 0; row < endIdx - startIdx; row++) {
                int rowY = listY + row * (rowH + 1);
                if (mouseY >= rowY && mouseY < rowY + rowH) {
                    UUID uuid = townstead$profVillagerUuids.get(startIdx + row);
                    townstead$profSelectedVillager = uuid.equals(townstead$profSelectedVillager) ? null : uuid;
                    townstead$profScroll = 0;
                    cir.setReturnValue(true);
                    cir.cancel();
                    return;
                }
            }
        }

        // Check profession button clicks (only above the scroll buttons)
        int profPanelBottom = this.height / 2 - 56 + 22 * 5 + 20 - 16;
        if (townstead$profSelectedVillager != null && mouseX >= profPanelX && mouseX < profPanelRight
                && mouseY < profPanelBottom) {
            List<String> available = ProfessionClientStore.getProfessions();
            int btnH = 14;
            for (int i = 0; i < available.size(); i++) {
                int by = listY + (i - townstead$profScroll) * (btnH + 1);
                if (by + btnH < listY || by > this.height / 2 - 56 + 22 * 5 + 20 - 16) continue;
                if (mouseY >= by && mouseY < by + btnH) {
                    String profId = available.get(i);
                    //? if neoforge {
                    PacketDistributor.sendToServer(new ProfessionSetPayload(townstead$profSelectedVillager, profId));
                    //?} else if forge {
                    /*TownsteadNetwork.sendToServer(new ProfessionSetPayload(townstead$profSelectedVillager, profId));
                    *///?}
                    cir.setReturnValue(true);
                    cir.cancel();
                    return;
                }
            }
        }
    }

    @Unique
    private boolean townstead$handleProfessionScroll(double mouseX, double mouseY, double verticalAmount) {
        if (!TOWNSTEAD_PROFESSION_PAGE.equals(this.page))
            return false;
        int leftX = this.width / 2 - 80;
        int listRight = this.width / 2 + 40;
        int profPanelX = listRight + 8;
        int profPanelRight = this.width / 2 + 176;
        int listY = this.height / 2 - 48;
        int listBottom = listY + PROF_ROWS_PER_PAGE * 15;

        if (mouseX >= leftX && mouseX <= listRight && mouseY >= listY && mouseY <= listBottom) {
            if (verticalAmount < 0) {
                townstead$profPageDelta(1);
            } else if (verticalAmount > 0) {
                townstead$profPageDelta(-1);
            } else {
                return false;
            }
            return true;
        }

        if (mouseX >= profPanelX && mouseX <= profPanelRight && mouseY >= listY && mouseY <= this.height / 2 + 76) {
            if (verticalAmount < 0) {
                townstead$profScroll++;
            } else if (verticalAmount > 0) {
                townstead$profScroll = Math.max(0, townstead$profScroll - 1);
            } else {
                return false;
            }
            return true;
        }
        return false;
    }
}
