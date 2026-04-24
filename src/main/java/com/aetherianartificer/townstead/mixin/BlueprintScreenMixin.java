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
    private static final String TOWNSTEAD_SPIRIT_PAGE = "townstead_spirit";
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
    // Tiered building type names follow `<family>_l<digits>`. Auto-detection
    // of the family prefix drives tier layout and group labeling generically
    // — no per-family hardcoded constants.
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
    private final Set<String> townstead$builtTypes = new HashSet<>();
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
    private float townstead$spiritScrollCurrent = 0f; // interpolated pixel offset
    @Unique
    private int townstead$spiritScrollTarget = 0;     // desired pixel offset
    @Unique
    private boolean townstead$spiritRadarMode = false;
    @Unique
    private final java.util.Map<Integer, java.util.Set<String>> townstead$spiritCollapsedByVillage = new java.util.HashMap<>();
    @Unique
    private final java.util.Map<Integer, Integer> townstead$spiritScrollByVillage = new java.util.HashMap<>();
    @Unique
    private final java.util.Map<Integer, ContribCacheEntry> townstead$spiritContribCache = new java.util.HashMap<>();
    @Unique
    private long townstead$lastNarrationMs = 0L;
    @Unique
    private int townstead$lastHoverMouseX = Integer.MIN_VALUE;
    @Unique
    private int townstead$lastHoverMouseY = Integer.MIN_VALUE;
    @Unique
    private String townstead$lastHoverSpirit = null;
    @Unique
    private int townstead$spiritSortMode = 0;   // 0=points, 1=count, 2=alpha
    @Unique
    private boolean townstead$spiritFilterTop3 = false;
    @Unique
    private boolean townstead$spiritFilterThreshold = false; // hide under 10% share
    @Unique
    private String townstead$pendingCatalogBuildingType = null;
    @Unique
    private String townstead$lastNarratedSpirit = null;
    @Unique
    private ButtonWidget townstead$spiritSortBtn;
    @Unique
    private ButtonWidget townstead$spiritTop3Btn;
    @Unique
    private ButtonWidget townstead$spiritThresholdBtn;

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

        if (TOWNSTEAD_SPIRIT_PAGE.equals(this.page)) {
            townstead$initSpiritPage();
            // Full-panel takeover: hide MCA's nav column (same as catalog)
            // so the spirit panel can own the screen width without the
            // nav buttons bleeding through.
            townstead$setNavVisible(false);
        } else if (TOWNSTEAD_SHIFT_PAGE.equals(this.page)) {
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
            // Honor a pending "jump-to-building" request from another page
            // (e.g., clicking a contributor on the Spirit page).
            if (townstead$pendingCatalogBuildingType != null) {
                for (int i = 0; i < townstead$catalogEntries.size(); i++) {
                    if (townstead$catalogEntries.get(i).name().equals(townstead$pendingCatalogBuildingType)) {
                        townstead$catalogSelected = i;
                        break;
                    }
                }
                townstead$pendingCatalogBuildingType = null;
            }
        } else if ("map".equals(this.page)) {
            townstead$addUpgradeBuildingControl();
            townstead$setNavVisible(true);
        } else if ("rank".equals(this.page)) {
            townstead$addSpiritButtonOnStatusPage();
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

            // Item rendering is handled by townstead$drawCompatBuildingIcon
            // (HEAD-injected on drawBuildingIcon): it resolves the node item
            // by (iconU, iconV) and cancels MCA's atlas draw. Here we just
            // call drawBuildingIcon so the inject runs with the right UV, and
            // it takes over if a node item is configured for this type.
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
        townstead$drawCatalogNodes(context, graphX, graphY, graphW, graphH, mouseX, mouseY, partialTicks);
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
        // Community Spirit contributions — colored "+N" pill tags under the
        // tier/mod line. Hidden when the building doesn't contribute to any
        // spirit (e.g., neutral housing types). Each chip pairs the spirit's
        // icon-item with a "+N" label tinted with the spirit color so the
        // building's identity is readable at a glance.
        java.util.Map<String, Integer> spiritPts =
                com.aetherianartificer.townstead.spirit.BuildingSpiritIndex.contributionsFor(selected.name());
        if (!spiritPts.isEmpty()) {
            int chipY = detailsTextY;
            int chipX = detailsTextX;
            int chipMaxRight = detailsX + CATALOG_DETAILS_W - 4;
            int chipH = 11;
            com.aetherianartificer.townstead.spirit.SpiritRegistry.Spirit hoveredSpirit = null;
            int hoveredPts = 0;
            for (com.aetherianartificer.townstead.spirit.SpiritRegistry.Spirit s :
                    com.aetherianartificer.townstead.spirit.SpiritRegistry.ordered()) {
                Integer pts = spiritPts.get(s.id());
                if (pts == null || pts <= 0) continue;
                String label = "+" + pts;
                int textW = this.font.width(label);
                int chipW = 12 + textW + 4;
                // Wrap to next row if the chip would overflow the panel.
                if (chipX + chipW > chipMaxRight) {
                    chipX = detailsTextX;
                    chipY += chipH + 2;
                }
                // Pill background — spirit color at low alpha + outline.
                int bg = (s.color() & 0x00FFFFFF) | 0x40000000;
                int border = (s.color() & 0x00FFFFFF) | 0xC0000000;
                context.fill(chipX, chipY, chipX + chipW, chipY + chipH, bg);
                context.fill(chipX, chipY, chipX + chipW, chipY + 1, border);
                context.fill(chipX, chipY + chipH - 1, chipX + chipW, chipY + chipH, border);
                context.fill(chipX, chipY, chipX + 1, chipY + chipH, border);
                context.fill(chipX + chipW - 1, chipY, chipX + chipW, chipY + chipH, border);
                // Spirit icon (10x10 via 0.625 scale).
                context.pose().pushPose();
                context.pose().translate(chipX + 1, chipY + 1, 0);
                context.pose().scale(0.625f, 0.625f, 1f);
                context.renderItem(new net.minecraft.world.item.ItemStack(s.icon()), 0, 0);
                context.pose().popPose();
                // "+N" text after the icon, tinted with spirit color.
                context.drawString(this.font, label, chipX + 12, chipY + 2, s.color(), false);
                // Capture hover for tooltip rendering after the loop.
                if (mouseX >= chipX && mouseX < chipX + chipW
                        && mouseY >= chipY && mouseY < chipY + chipH) {
                    hoveredSpirit = s;
                    hoveredPts = pts;
                }
                chipX += chipW + 3;
            }
            detailsTextY = chipY + chipH + 3;

            // Tooltip on the hovered chip — spirit name (in its color) + a
            // terse one-liner clarifying that the +N is per-building.
            if (hoveredSpirit != null) {
                java.util.List<net.minecraft.network.chat.Component> tooltip = new java.util.ArrayList<>();
                tooltip.add(net.minecraft.network.chat.Component.translatable(hoveredSpirit.displayKey())
                        .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(
                                net.minecraft.network.chat.TextColor.fromRgb(hoveredSpirit.color() & 0x00FFFFFF))));
                tooltip.add(net.minecraft.network.chat.Component.translatable(
                        "townstead.spirit.chip.tooltip", hoveredPts)
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
                context.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
            }
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
            float pageScale = 0.72f;
            String pageText = (townstead$catalogNeedsPage + 1) + " / " + totalPages;
            int pageVisualW = (int) Math.ceil(this.font.width(pageText) * pageScale);
            int pageVisualH = (int) Math.ceil(this.font.lineHeight * pageScale);
            int pageVisualX = prevX - 4 - pageVisualW;
            int pageVisualY = buttonY + (14 - pageVisualH) / 2 - 1;
            context.pose().pushPose();
            context.pose().scale(pageScale, pageScale, 1.0f);
            context.drawString(
                    this.font,
                    Component.literal(pageText),
                    Math.round(pageVisualX / pageScale),
                    Math.round(pageVisualY / pageScale),
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
        if (townstead$handleSpiritScroll(mouseX, mouseY, verticalAmount))
            return true;
        return townstead$handleNavScroll(mouseX, mouseY, verticalAmount);
    }

    @Unique
    private boolean townstead$handleSpiritScroll(double mouseX, double mouseY, double verticalAmount) {
        if (!TOWNSTEAD_SPIRIT_PAGE.equals(this.page)) return false;
        if (verticalAmount == 0) return false;
        // Pixel-based scroll; 18 px per wheel tick feels about right for a
        // 10 px bar + 8 px header/footer row.
        int step = 18;
        townstead$setSpiritScrollTarget(townstead$spiritScrollTarget
                + (verticalAmount > 0 ? -step : step));
        return true;
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

    /**
     * Adds the Community Spirit entry button to MCA's "Rank" (renamed
     * "Status" in our lang override) page. The status page is the natural
     * home for village-scoped identity info — population, taxes, reputation
     * already live here, so spirit fits.
     */
    @Unique
    private void townstead$addSpiritButtonOnStatusPage() {
        int bx = this.width / 2 + 180 - 64 - 16;
        // Top of MCA's button column — same slot 0 as the side-nav "Map" tab,
        // so the Spirit entry reads as the page's primary action.
        int by = this.height / 2 - 56;
        addRenderableWidget(new TooltipButtonWidget(
                bx, by, 96, 20,
                Component.translatable("gui.blueprint.spirit"),
                Component.translatable("gui.blueprint.spirit.tooltip"),
                b -> setPage(TOWNSTEAD_SPIRIT_PAGE)));
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
        townstead$builtTypes.clear();
        BlueprintScreenAccessor accessor = (BlueprintScreenAccessor) (Object) this;
        if (accessor.townstead$getVillage() != null) {
            for (Building building : accessor.townstead$getVillage().getBuildings().values()) {
                townstead$builtTypes.add(building.getType());
            }
        }
        all = all.stream()
                .filter(BuildingType::visible)
                .filter(bt -> ModCompat.isCompatAvailable(bt.name()))
                .filter(bt -> !com.aetherianartificer.townstead.client.catalog.CatalogDataLoader
                        .overrideFor(bt.name()).hide())
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
                Optional<com.aetherianartificer.townstead.client.catalog.CatalogDataLoader.GroupDef> match =
                        com.aetherianartificer.townstead.client.catalog.CatalogDataLoader.matchGroup(name);
                String tierPrefix = null;
                if (match.isPresent() && "tiered".equals(match.get().layout())
                        && !match.get().tierPrefix().isEmpty()
                        && name.startsWith(match.get().tierPrefix())) {
                    tierPrefix = match.get().tierPrefix();
                } else {
                    tierPrefix = townstead$autoTierPrefix(name);
                }
                int nodeX;
                int nodeY;
                if (tierPrefix != null) {
                    int tier = 1;
                    try {
                        tier = Integer.parseInt(name.substring(tierPrefix.length()));
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
        java.util.Set<String> tierPrefixes = new java.util.LinkedHashSet<>();
        for (com.aetherianartificer.townstead.client.catalog.CatalogDataLoader.GroupDef g
                : com.aetherianartificer.townstead.client.catalog.CatalogDataLoader.groups()) {
            if ("tiered".equals(g.layout()) && !g.tierPrefix().isEmpty())
                tierPrefixes.add(g.tierPrefix());
        }
        // Auto-detect additional tier prefixes from node names. Handles any
        // <family>_lN building type without needing an explicit group JSON.
        for (NodeData node : townstead$catalogNodes) {
            String auto = townstead$autoTierPrefix(node.type().name());
            if (auto != null) tierPrefixes.add(auto);
        }
        for (String prefix : tierPrefixes) {
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
    private void townstead$drawCatalogNodes(GuiGraphics context, int insideX, int insideY, int insideW, int insideH,
            int mouseX, int mouseY, float partialTicks) {
        for (NodeData node : townstead$catalogNodes) {
            int screenX = insideX + (int) Math.round((node.worldX() + townstead$catalogPanX) * townstead$catalogZoom);
            int screenY = insideY + (int) Math.round((node.worldY() + townstead$catalogPanY) * townstead$catalogZoom);
            int nodeW = Math.max(16, (int) Math.round(26 * townstead$catalogZoom));
            int nodeH = Math.max(16, (int) Math.round(26 * townstead$catalogZoom));

            boolean hovered = mouseX >= screenX && mouseX <= screenX + nodeW
                    && mouseY >= screenY && mouseY <= screenY + nodeH;
            boolean selected = node.index() == townstead$catalogSelected;
            boolean built = townstead$builtTypes.contains(node.type().name());

            int border;
            int fill;
            if (built) {
                border = selected ? 0xFFCDEBD0 : (hovered ? 0xFFA8D9AE : 0xFF5F9466);
                fill = selected ? 0xFF2F5C3A : (hovered ? 0xFF295236 : 0xFF1F4029);
            } else {
                border = selected ? 0xFFD9E9FF : (hovered ? 0xFFB8C7DB : 0xFF6D7A8D);
                fill = selected ? 0xFF3A4D66 : (hovered ? 0xFF34435A : 0xFF2A3342);
            }
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
        Optional<ResourceLocation> datapack =
                com.aetherianartificer.townstead.client.catalog.CatalogDataLoader.overrideFor(buildingTypeName).nodeItem();
        if (datapack.isPresent()) {
            townstead$nodeItemIconCache.put(buildingTypeName, datapack);
            return datapack;
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
        float iconScale = Math.max(0.55f, (float) townstead$catalogZoom);
        int centerX = screenX + nodeW / 2;
        int centerY = screenY + nodeH / 2;
        // Prefer the item-icon path whenever a townsteadNodeItem is declared,
        // regardless of whether the type lives under compat/ or a root-level
        // namespace (e.g., dock_l1). Only fall back to the MCA atlas sprite
        // when no node item is configured.
        Optional<ResourceLocation> nodeItem = townstead$nodeItemForType(type.name());
        if (nodeItem.isEmpty()) {
            context.pose().pushPose();
            context.pose().translate(centerX, centerY, 0);
            context.pose().scale(iconScale, iconScale, 1.0f);
            this.drawBuildingIcon(context, MCA_BUILDING_ICONS, 0, 0, type.iconU(), type.iconV());
            context.pose().popPose();
            return;
        }
        ItemStack icon = townstead$resolveNodeIcon(type);
        if (icon.isEmpty())
            return;
        context.pose().pushPose();
        context.pose().translate(centerX, centerY, 0);
        context.pose().scale(iconScale, iconScale, 1.0f);
        context.renderItem(icon, -8, -8);
        context.pose().popPose();
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
        Optional<com.aetherianartificer.townstead.client.catalog.CatalogDataLoader.GroupDef> match =
                com.aetherianartificer.townstead.client.catalog.CatalogDataLoader.matchGroup(name);
        if (match.isPresent())
            return match.get().label();
        // Auto-label derived from the tier family prefix, so any `<family>_lN`
        // building types cluster under a "<Family>" heading even if no catalog
        // group JSON is loaded. Prettier variants ("Docks" vs. "Dock", etc.)
        // still come from an explicit group JSON when one is provided.
        String autoTier = townstead$autoTierPrefix(name);
        if (autoTier != null) {
            String family = autoTier.substring(0, autoTier.length() - 2); // strip trailing "_l"
            int lastSlash = family.lastIndexOf('/');
            String leaf = lastSlash >= 0 ? family.substring(lastSlash + 1) : family;
            if (!leaf.isEmpty()) {
                return leaf.substring(0, 1).toUpperCase(Locale.ROOT) + leaf.substring(1);
            }
        }
        if (!name.startsWith("compat/"))
            return "Core";
        String[] parts = name.split("/");
        if (parts.length < 2)
            return "Compat";
        String mod = parts[1];
        return mod.substring(0, 1).toUpperCase(Locale.ROOT) + mod.substring(1);
    }

    /**
     * Return the tier family prefix for a building type name of the form
     * {@code <family>_l<digits>}, or null if the name doesn't follow the
     * tiered convention. The returned prefix includes the trailing "_l"
     * so it can be passed directly to the tier-layout math that does
     * {@code name.substring(prefix.length())}.
     *
     * Examples:
     *   dock_l1                                   -> "dock_l"
     *   compat/farmersdelight/kitchen_l3          -> "compat/farmersdelight/kitchen_l"
     *   house, graveyard, compat/x/y              -> null
     */
    @Unique
    private static String townstead$autoTierPrefix(String name) {
        if (name == null) return null;
        int idx = name.lastIndexOf("_l");
        if (idx <= 0 || idx >= name.length() - 2) return null;
        String suffix = name.substring(idx + 2);
        if (suffix.isEmpty() || !suffix.chars().allMatch(Character::isDigit)) return null;
        return name.substring(0, idx + 2);
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

    // =====================================================================
    // Community Spirit page
    // =====================================================================

    @Unique
    private int townstead$spiritWindowW() {
        return Math.min(this.width - 40, 420);
    }

    @Unique
    private int townstead$spiritWindowH() {
        return Math.min(this.height - 60, 280);
    }

    @Unique
    private int townstead$spiritWindowX() {
        return (this.width - townstead$spiritWindowW()) / 2;
    }

    @Unique
    private int townstead$spiritWindowY() {
        return (this.height - townstead$spiritWindowH()) / 2;
    }

    @Unique
    private void townstead$initSpiritPage() {
        // Restore per-village scroll position so re-opening the page lands
        // where the player left off.
        int saved = townstead$spiritScrollByVillage.getOrDefault(
                townstead$currentSpiritVillageId(), 0);
        townstead$spiritScrollTarget = saved;
        townstead$spiritScrollCurrent = saved;
        // Entrance stinger — a soft page-turn when the Spirit dashboard opens.
        // Uses the UI sound channel so it respects the player's master volume.
        try {
            net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.0f));
        } catch (Throwable ignored) {}
        // Back button sits inside the panel's title strip, top-left corner.
        int windowX = townstead$spiritWindowX();
        int windowY = townstead$spiritWindowY();
        int windowW = townstead$spiritWindowW();
        addRenderableWidget(new ButtonWidget(
                windowX + 4, windowY + 3, 40, 14,
                Component.translatable("townstead.gui.back"),
                b -> setPage("map")));
        // Sort + filter controls below the header divider. Kept as widgets so
        // MC handles their click/hover chrome; visibility is toggled each
        // frame from the render path so they disappear in radar mode.
        int controlsY = windowY + 22 + 26 + 4; // mirrors render's contentTop math
        int controlsLeft = windowX + 10;
        townstead$spiritSortBtn = new ButtonWidget(
                controlsLeft, controlsY, 60, 12,
                Component.translatable(townstead$spiritSortLabelKey()),
                b -> {
                    townstead$spiritSortMode = (townstead$spiritSortMode + 1) % 3;
                    b.setMessage(Component.translatable(townstead$spiritSortLabelKey()));
                });
        addRenderableWidget(townstead$spiritSortBtn);

        townstead$spiritTop3Btn = new ButtonWidget(
                controlsLeft + 64, controlsY, 52, 12,
                townstead$filterLabel("townstead.spirit.filter.top3", townstead$spiritFilterTop3),
                b -> {
                    townstead$spiritFilterTop3 = !townstead$spiritFilterTop3;
                    b.setMessage(townstead$filterLabel("townstead.spirit.filter.top3",
                            townstead$spiritFilterTop3));
                });
        addRenderableWidget(townstead$spiritTop3Btn);

        townstead$spiritThresholdBtn = new ButtonWidget(
                controlsLeft + 120, controlsY, 52, 12,
                townstead$filterLabel("townstead.spirit.filter.threshold", townstead$spiritFilterThreshold),
                b -> {
                    townstead$spiritFilterThreshold = !townstead$spiritFilterThreshold;
                    b.setMessage(townstead$filterLabel("townstead.spirit.filter.threshold",
                            townstead$spiritFilterThreshold));
                });
        addRenderableWidget(townstead$spiritThresholdBtn);
    }

    @Unique
    private Component townstead$filterLabel(String baseKey, boolean on) {
        // \u25CF = filled circle, \u25CB = empty circle — reads as a checkbox
        // toggle while keeping the button fully clickable (setting active=false
        // would disable clicks).
        return Component.literal(on ? "\u25CF " : "\u25CB ").append(Component.translatable(baseKey));
    }

    // --------------- Per-village state + cache helpers ---------------
    @Unique
    private int townstead$currentSpiritVillageId() {
        net.conczin.mca.server.world.data.Village v =
                ((BlueprintScreenAccessor) (Object) this).townstead$getVillage();
        return v != null ? v.getId() : 0;
    }

    @Unique
    private java.util.Set<String> townstead$collapsedSet() {
        return townstead$spiritCollapsedByVillage.computeIfAbsent(
                townstead$currentSpiritVillageId(), k -> new java.util.HashSet<>());
    }

    @Unique
    private void townstead$setSpiritScrollTarget(int target) {
        townstead$spiritScrollTarget = Math.max(0, target);
        townstead$spiritScrollByVillage.put(
                townstead$currentSpiritVillageId(), townstead$spiritScrollTarget);
    }

    @Unique
    private java.util.Map<String, java.util.List<ContributorEntry>> townstead$contribsFor(
            net.conczin.mca.server.world.data.Village village,
            com.aetherianartificer.townstead.spirit.VillageSpiritSyncPayload snapshot) {
        int vid = village.getId();
        int bct = village.getBuildings().size();
        int total = snapshot.total();
        ContribCacheEntry cached = townstead$spiritContribCache.get(vid);
        if (cached != null && cached.buildingCount() == bct && cached.payloadTotal() == total) {
            return cached.data();
        }
        java.util.Map<String, java.util.List<ContributorEntry>> fresh =
                townstead$aggregateContributors(village);
        townstead$spiritContribCache.put(vid, new ContribCacheEntry(bct, total, fresh));
        return fresh;
    }

    // --------------- A11y config accessors + row scaling helpers ---------------
    @Unique
    private boolean townstead$a11yColorblind() {
        try { return com.aetherianartificer.townstead.TownsteadConfig.SPIRIT_COLORBLIND_PATTERNS.get(); }
        catch (Throwable t) { return false; }
    }
    @Unique
    private boolean townstead$a11yNarration() {
        try { return com.aetherianartificer.townstead.TownsteadConfig.SPIRIT_NARRATION.get(); }
        catch (Throwable t) { return false; }
    }
    @Unique
    private boolean townstead$a11yLargerHit() {
        try { return com.aetherianartificer.townstead.TownsteadConfig.SPIRIT_LARGER_HIT_TARGETS.get(); }
        catch (Throwable t) { return false; }
    }
    @Unique
    private boolean townstead$a11yHighContrast() {
        try { return com.aetherianartificer.townstead.TownsteadConfig.SPIRIT_HIGH_CONTRAST.get(); }
        catch (Throwable t) { return false; }
    }
    @Unique
    private double townstead$a11yFontScale() {
        try { return com.aetherianartificer.townstead.TownsteadConfig.SPIRIT_FONT_SCALE.get(); }
        catch (Throwable t) { return 1.0; }
    }

    /**
     * Composite "row scale" used to size chrome (heights, bar thickness,
     * contributor line height). Pulls up with either larger-hit-targets or
     * font-scale so the layout keeps its proportions.
     */
    @Unique
    private double townstead$rowScale() {
        double fs = townstead$a11yFontScale();
        double hit = townstead$a11yLargerHit() ? 1.35 : 1.0;
        return Math.max(fs, hit);
    }

    @Unique
    private void townstead$drawScaledString(GuiGraphics ctx, Component text, int x, int y,
                                            int color, double scale) {
        if (Math.abs(scale - 1.0) < 0.001) {
            ctx.drawString(this.font, text, x, y, color, false);
            return;
        }
        ctx.pose().pushPose();
        ctx.pose().translate(x, y, 0);
        ctx.pose().scale((float) scale, (float) scale, 1f);
        ctx.drawString(this.font, text, 0, 0, color, false);
        ctx.pose().popPose();
    }

    @Unique
    private void townstead$drawScaledString(GuiGraphics ctx, String text, int x, int y,
                                            int color, double scale) {
        if (Math.abs(scale - 1.0) < 0.001) {
            ctx.drawString(this.font, text, x, y, color, false);
            return;
        }
        ctx.pose().pushPose();
        ctx.pose().translate(x, y, 0);
        ctx.pose().scale((float) scale, (float) scale, 1f);
        ctx.drawString(this.font, text, 0, 0, color, false);
        ctx.pose().popPose();
    }

    @Unique
    private void townstead$narrateHover(String hoveredSpiritId,
            com.aetherianartificer.townstead.spirit.VillageSpiritSyncPayload snapshot,
            java.util.Map<String, java.util.List<ContributorEntry>> contribs) {
        if (!townstead$a11yNarration()) return;
        if (java.util.Objects.equals(hoveredSpiritId, townstead$lastNarratedSpirit)) return;
        // Debounce — rapid flyover across rows shouldn't fire narration for
        // every one. 200ms floor lets the user "settle" on a row first.
        long nowMs = System.currentTimeMillis();
        if (nowMs - townstead$lastNarrationMs < 200L) return;
        townstead$lastNarrationMs = nowMs;
        townstead$lastNarratedSpirit = hoveredSpiritId;
        if (hoveredSpiritId == null) return;
        var opt = com.aetherianartificer.townstead.spirit.SpiritRegistry.get(hoveredSpiritId);
        if (opt.isEmpty()) return;
        int pts = snapshot.perSpirit().getOrDefault(hoveredSpiritId, 0);
        int total = snapshot.total();
        int sharePct = total > 0 ? (int) Math.round(100.0 * pts / total) : 0;
        int tier = com.aetherianartificer.townstead.spirit.VillageSpiritAggregator.tierForSpirit(pts);
        int[] thresholds = com.aetherianartificer.townstead.spirit.VillageSpiritAggregator.tierThresholds();
        String displayName = Component.translatable(opt.get().displayKey()).getString();
        StringBuilder sb = new StringBuilder();
        sb.append(displayName).append(": ").append(pts);
        if (tier < thresholds.length) {
            sb.append(" of ").append(thresholds[tier]).append(" points");
        } else {
            sb.append(" points, max tier");
        }
        sb.append(", ").append(sharePct).append(" percent share");
        if (tier >= 1) {
            String tierKey = "townstead.spirit.tier." + hoveredSpiritId + "." + tier;
            sb.append(", ").append(Component.translatable(tierKey).getString()).append(" tier");
        }
        int contribCount = contribs.getOrDefault(hoveredSpiritId, java.util.List.of()).size();
        if (contribCount > 0) {
            sb.append(", ").append(contribCount).append(" contributing buildings");
        }
        try {
            com.mojang.text2speech.Narrator.getNarrator().say(sb.toString(), true);
        } catch (Throwable ignored) {}
    }

    /**
     * Per-spirit colorblind hatching pattern. Each spirit gets a unique
     * pattern so the bars can be told apart without relying on color. Pattern
     * pixels are dark overlaid on the filled region.
     */
    @Unique
    private void townstead$applyHatching(GuiGraphics context, String spiritId,
                                         int x1, int y1, int x2, int y2) {
        if (!townstead$a11yColorblind()) return;
        int idx = com.aetherianartificer.townstead.spirit.SpiritRegistry.indexOf(spiritId);
        if (idx < 0) return;
        int darkened = 0xB0000000;
        for (int y = y1; y < y2; y++) {
            for (int x = x1; x < x2; x++) {
                int lx = x - x1;
                int ly = y - y1;
                boolean mark = switch (idx) {
                    case 0 -> ((lx + ly) % 4) == 0;                     // diagonal stripes
                    case 1 -> (lx % 3 == 0) && (ly % 2 == 0);            // sparse dots
                    case 2 -> (lx % 4 == 0) || (ly % 4 == 0);            // grid
                    case 3 -> (ly == 0) || (ly == (y2 - y1) / 2);        // horizontal bands
                    case 4 -> (lx % 3 == 0);                             // vertical lines
                    case 5 -> ((lx + ly) % 3 == 0) && (lx + ly) % 6 != 0;// dashed diagonal
                    default -> ((lx ^ ly) & 3) == 0;                     // checker blend
                };
                if (mark) {
                    context.fill(x, y, x + 1, y + 1, darkened);
                }
            }
        }
    }

    /**
     * Spirit-themed ambient particles drifting inside the panel. A handful
     * of animated sprites whose path + color matches the dominant spirit:
     * bubbles for Nautical, sparks for Industrious, motes for Scholar, etc.
     */
    @Unique
    private void townstead$drawSpiritParticles(GuiGraphics context, int x1, int y1, int x2, int y2,
                                               String spiritId, long nowMs) {
        if (spiritId == null) return;
        int idx = com.aetherianartificer.townstead.spirit.SpiritRegistry.indexOf(spiritId);
        if (idx < 0) return;
        int w = x2 - x1;
        int h = y2 - y1;
        if (w < 8 || h < 8) return;
        int count = 14;
        double t = nowMs / 1000.0;
        for (int i = 0; i < count; i++) {
            double phase = i * 0.7731;
            double cycle = 6.0 + (i % 4) * 1.2; // seconds per full vertical sweep
            double progress = ((t / cycle) + phase) % 1.0;
            if (progress < 0) progress += 1.0;
            // Base horizontal position — deterministic per index, slight wobble.
            double baseX = (i * 29 + 13) % Math.max(1, w - 4);
            double wobble = Math.sin(t * 1.3 + phase * 3) * 2.5;
            int px = x1 + 2 + (int) Math.round(baseX + wobble);
            int py;
            switch (idx) {
                case 0 -> { // Nautical — bubbles rising
                    py = y2 - 2 - (int) Math.round(progress * (h - 6));
                    int alpha = (int) (0x40 * (1.0 - progress)) & 0xFF;
                    int col = (alpha << 24) | 0xCCE8FF;
                    context.fill(px, py, px + 2, py + 2, col);
                    if ((i & 1) == 0) context.fill(px + 1, py - 1, px + 2, py, col);
                }
                case 1 -> { // Pastoral — falling flecks
                    py = y1 + 2 + (int) Math.round(progress * (h - 6));
                    int alpha = (int) (0x40 * (1.0 - Math.abs(progress - 0.5) * 2)) & 0xFF;
                    int col = (alpha << 24) | 0x9FC870;
                    context.fill(px, py, px + 2, py + 1, col);
                }
                case 2 -> { // Martial — flickering embers
                    double life = (t * 3 + phase) % 1.0;
                    py = y1 + 2 + ((int) ((i * 37 + 11) % Math.max(1, h - 6)));
                    int alpha = life < 0.3 ? (int) (0x80 * (life / 0.3)) : (int) (0x80 * (1.0 - (life - 0.3) / 0.7));
                    int col = ((alpha & 0xFF) << 24) | 0xFF7020;
                    context.fill(px, py, px + 2, py + 2, col);
                }
                case 3 -> { // Scholar — slow floating motes
                    py = y2 - 2 - (int) Math.round(progress * (h - 6));
                    int alpha = (int) (0x55 * Math.sin(progress * Math.PI)) & 0xFF;
                    int col = (alpha << 24) | 0xC9B2FF;
                    context.fill(px, py, px + 1, py + 1, col);
                    context.fill(px + 1, py, px + 2, py + 1, (col & 0xFFFFFF) | ((alpha / 2) << 24));
                }
                case 4 -> { // Industrious — upward sparks
                    py = y2 - 2 - (int) Math.round(progress * (h - 6));
                    int alpha = (int) (0x90 * (1.0 - progress)) & 0xFF;
                    int hot = progress < 0.4 ? 0xFFC060 : 0xFF8030;
                    int col = (alpha << 24) | hot;
                    context.fill(px, py, px + 1, py + 1, col);
                    if (progress < 0.3) context.fill(px, py + 1, px + 1, py + 2, (col & 0xFFFFFF) | ((alpha / 2) << 24));
                }
                case 5 -> { // Commercial — twinkling glints
                    double life = (t * 1.5 + phase) % 1.0;
                    py = y1 + 3 + (int) ((i * 53 + 19) % Math.max(1, h - 8));
                    int alpha = (int) (0xA0 * Math.pow(Math.sin(life * Math.PI), 6)) & 0xFF;
                    if (alpha > 6) {
                        int col = (alpha << 24) | 0xFFE89A;
                        // Tiny cross glint
                        context.fill(px - 1, py, px + 2, py + 1, col);
                        context.fill(px, py - 1, px + 1, py + 2, col);
                    }
                }
                default -> { // Tourism — drifting petals
                    py = y1 + 2 + (int) Math.round(progress * (h - 6));
                    double drift = Math.sin(t * 0.8 + phase * 2) * 4;
                    int alpha = (int) (0x60 * Math.sin(progress * Math.PI)) & 0xFF;
                    int col = (alpha << 24) | 0xFFB0D0;
                    int fx = px + (int) Math.round(drift);
                    context.fill(fx, py, fx + 2, py + 1, col);
                    context.fill(fx + 1, py + 1, fx + 2, py + 2, col);
                }
            }
        }
    }

    @Unique
    private int townstead$spiritTogglePillW() { return 28; }
    @Unique
    private int townstead$spiritTogglePillH() { return 10; }
    @Unique
    private int townstead$spiritTogglePillX(int windowX, int windowW) {
        return windowX + windowW - townstead$spiritTogglePillW() - 4;
    }
    @Unique
    private int townstead$spiritTogglePillY(int windowY) { return windowY + 4; }

    @Unique
    private void townstead$drawListIcon(GuiGraphics context, int x, int y, int color) {
        // Three horizontal bars, 6×1 each, 2 px apart. Fits in 6×5.
        context.fill(x,     y,     x + 6, y + 1, color);
        context.fill(x,     y + 2, x + 6, y + 3, color);
        context.fill(x,     y + 4, x + 6, y + 5, color);
    }

    @Unique
    private void townstead$drawRadarIcon(GuiGraphics context, int x, int y, int color) {
        // Small diamond/target, 7×7.
        context.fill(x + 3, y,     x + 4, y + 1, color);
        context.fill(x + 2, y + 1, x + 5, y + 2, color);
        context.fill(x + 1, y + 2, x + 6, y + 3, color);
        context.fill(x,     y + 3, x + 7, y + 4, color);
        context.fill(x + 1, y + 4, x + 6, y + 5, color);
        context.fill(x + 2, y + 5, x + 5, y + 6, color);
        context.fill(x + 3, y + 6, x + 4, y + 7, color);
        // Center dot hole to make it read as a radar reticle, not a solid diamond.
        context.fill(x + 3, y + 3, x + 4, y + 4, 0xFF000000);
    }

    @Unique
    private String townstead$spiritSortLabelKey() {
        return switch (townstead$spiritSortMode) {
            case 1 -> "townstead.spirit.sort.count";
            case 2 -> "townstead.spirit.sort.alpha";
            default -> "townstead.spirit.sort.points";
        };
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void townstead$renderSpiritPage(GuiGraphics context, int mouseX, int mouseY, float partialTicks,
                                            CallbackInfo ci) {
        if (!TOWNSTEAD_SPIRIT_PAGE.equals(this.page)) return;
        BlueprintScreenAccessor accessor = (BlueprintScreenAccessor) (Object) this;
        net.conczin.mca.server.world.data.Village village = accessor.townstead$getVillage();
        if (village == null) return;

        java.util.Optional<com.aetherianartificer.townstead.spirit.VillageSpiritSyncPayload> snapshotOpt =
                com.aetherianartificer.townstead.spirit.ClientVillageSpiritStore.get(village.getId());

        // Full-screen panel — sized to claim most of the blueprint workspace
        // so the page can house a richer per-spirit breakdown with contributor
        // lists beneath each bar. Bounds are shared with the init method so
        // the back button (placed in init) sits inside the rendered panel.
        int windowW = townstead$spiritWindowW();
        int windowH = townstead$spiritWindowH();
        int windowX = townstead$spiritWindowX();
        int windowY = townstead$spiritWindowY();

        // Chrome: outer light border, dark interior, slightly lighter title strip.
        context.fill(windowX, windowY, windowX + windowW, windowY + windowH, 0xFFDEDEDE);
        context.fill(windowX + 1, windowY + 1, windowX + windowW - 1, windowY + windowH - 1, 0xFF2B2F38);
        context.fill(windowX + 3, windowY + 3, windowX + windowW - 3, windowY + 16, 0xFF3A3F47);

        // Title, centered over the title strip.
        Component title = Component.translatable("townstead.spirit.title");
        context.drawCenteredString(this.font, title, windowX + windowW / 2, windowY + 6, 0xFFFFFF);

        if (snapshotOpt.isEmpty()) {
            Component pending = Component.translatable("townstead.spirit.subtitle.loading");
            context.drawCenteredString(this.font, pending,
                    windowX + windowW / 2, windowY + windowH / 2 - 4, 0xA0A0A0);
            return;
        }

        com.aetherianartificer.townstead.spirit.VillageSpiritSyncPayload snapshot = snapshotOpt.get();
        com.aetherianartificer.townstead.spirit.SpiritReadout readout = snapshot.toReadout();
        int readoutColor = townstead$spiritAccentColor(readout);

        // Visual spirit — drives chrome theming. Prefer the readout's
        // primary (SINGLE / BLEND), but fall back to the spirit with the
        // most points so Outpost and MIXED villages still get themed chrome.
        String visualSpiritId = readout.primarySpiritId();
        int visualColor = readoutColor;
        if (visualSpiritId == null) {
            String topId = null;
            int topPts = 0;
            for (var entry : snapshot.perSpirit().entrySet()) {
                if (entry.getValue() > topPts) {
                    topPts = entry.getValue();
                    topId = entry.getKey();
                }
            }
            if (topId != null) {
                visualSpiritId = topId;
                var topSpirit = com.aetherianartificer.townstead.spirit.SpiritRegistry.get(topId);
                if (topSpirit.isPresent()) visualColor = topSpirit.get().color();
            }
        }

        if (visualSpiritId != null) {
            // Title strip tint — dominant spirit's color blended into the
            // header bar. Everything below gets per-row tinting in
            // drawSpiritSection, so each spirit row reads with its own color.
            int tintedTitle = townstead$blend(0xFF3A3F47, visualColor, 0.35f);
            context.fill(windowX + 3, windowY + 3, windowX + windowW - 3, windowY + 16, tintedTitle);

            // Ambient particles behind content.
            townstead$drawSpiritParticles(context,
                    windowX + 3, windowY + 16,
                    windowX + windowW - 3, windowY + windowH - 2,
                    visualSpiritId, System.currentTimeMillis());

            // Title text on top of the tint.
            context.drawCenteredString(this.font,
                    Component.translatable("townstead.spirit.title"),
                    windowX + windowW / 2, windowY + 6, 0xFFFFFF);

            // Soft edge tint — gradient bleeding inward top + bottom.
            int accent = visualColor & 0x00FFFFFF;
            int edgeBand = 8;
            for (int i = 0; i < edgeBand; i++) {
                int alpha = (edgeBand - i) * 4;
                int color = (alpha << 24) | accent;
                context.fill(windowX + 3, windowY + 17 + i,
                        windowX + windowW - 3, windowY + 18 + i, color);
                context.fill(windowX + 3, windowY + windowH - 2 - i,
                        windowX + windowW - 3, windowY + windowH - 1 - i, color);
            }
        }

        // Filter widgets are list-mode only — hide them when the radar view
        // is active so the chart isn't competing with irrelevant chrome.
        boolean listMode = !townstead$spiritRadarMode;
        if (townstead$spiritSortBtn != null) townstead$spiritSortBtn.visible = listMode;
        if (townstead$spiritTop3Btn != null) townstead$spiritTop3Btn.visible = listMode;
        if (townstead$spiritThresholdBtn != null) townstead$spiritThresholdBtn.visible = listMode;

        // View-mode toggle — a pixel-art segmented pill at the top-right of
        // the title strip. Two halves (list / radar), active half tinted with
        // the spirit accent color + light icon, inactive half dark grey with
        // dim icon. Click dispatch lives in the spirit mouseClicked hook.
        int pillX = townstead$spiritTogglePillX(windowX, windowW);
        int pillY = townstead$spiritTogglePillY(windowY);
        int pillW = townstead$spiritTogglePillW();
        int pillH = townstead$spiritTogglePillH();
        int halfW = pillW / 2;
        int activeBg = townstead$blend(0xFF3A3F47, readoutColor, 0.55f);
        int inactiveBg = 0xFF1E2128;
        // Outline + halves.
        context.fill(pillX - 1, pillY - 1, pillX + pillW + 1, pillY + pillH + 1, 0xFF000000);
        context.fill(pillX, pillY, pillX + halfW, pillY + pillH,
                townstead$spiritRadarMode ? inactiveBg : activeBg);
        context.fill(pillX + halfW, pillY, pillX + pillW, pillY + pillH,
                townstead$spiritRadarMode ? activeBg : inactiveBg);
        // Inner divider.
        context.fill(pillX + halfW, pillY, pillX + halfW + 1, pillY + pillH, 0xFF000000);
        // Icons.
        int listIconCol = townstead$spiritRadarMode ? 0xFF70747C : 0xFFFFFFFF;
        int radarIconCol = townstead$spiritRadarMode ? 0xFFFFFFFF : 0xFF70747C;
        // List icon is 6 wide, radar icon 7 wide; center each within its half.
        townstead$drawListIcon(context, pillX + (halfW - 6) / 2, pillY + 2, listIconCol);
        townstead$drawRadarIcon(context, pillX + halfW + (halfW - 7) / 2, pillY + 1, radarIconCol);

        // Header area: big readout line rendered at 2.0 scale (integer scale
        // keeps the pixel font crisp; 1.5 produces sub-pixel blur). Font-scale
        // a11y setting further multiplies this for users who want bigger text.
        // headerTop is set so that top + bottom padding around the readout
        // (between title strip and divider) is balanced at ~8px each.
        int headerTop = windowY + 24;
        Component readoutLine = readout.asComponent();
        float headerScale = (float) (2.0 * townstead$a11yFontScale());
        int centerX = windowX + windowW / 2;
        int headerTextColor = townstead$a11yHighContrast() ? 0xFFFFFFFF : readoutColor;
        context.pose().pushPose();
        context.pose().translate(centerX, headerTop, 0);
        context.pose().scale(headerScale, headerScale, 1.0f);
        context.drawString(this.font, readoutLine, -this.font.width(readoutLine) / 2, 0,
                headerTextColor, false);
        context.pose().popPose();

        // Divider between header and the scrollable spirit list.
        int dividerY = headerTop + 26;
        context.fill(windowX + 8, dividerY, windowX + windowW - 8, dividerY + 1, 0xFF404048);

        // Content bounds. In list mode the control row (sort + filter buttons,
        // 12 px tall + 6 px gap) sits between the divider and the list; radar
        // mode claims the full band below the divider.
        int contentLeft = windowX + 10;
        int contentRight = windowX + windowW - 10;
        int contentBottom = windowY + windowH - 6;
        int contentTop = townstead$spiritRadarMode ? dividerY + 4 : dividerY + 22;

        if (townstead$spiritRadarMode) {
            townstead$renderSpiritRadar(context, snapshot, readoutColor,
                    contentLeft, contentTop, contentRight, contentBottom);
            return;
        }

        // Active spirits — zero-point filtered, then user-applied sort/filter.
        // Contributor aggregation is cached keyed on (villageId, buildingCount,
        // payloadTotal) so we don't walk all buildings every frame.
        java.util.Map<String, java.util.List<ContributorEntry>> preContribs =
                townstead$contribsFor(village, snapshot);
        java.util.List<com.aetherianartificer.townstead.spirit.SpiritRegistry.Spirit> active =
                townstead$buildActiveSpirits(snapshot, preContribs);

        java.util.Map<String, java.util.List<ContributorEntry>> contributorsBySpirit = preContribs;

        // Measure each section's height so we can compute max scroll and
        // render only what's visible in the content band.
        int[] heights = new int[active.size()];
        int totalHeight = 0;
        int[] thresholds = com.aetherianartificer.townstead.spirit.VillageSpiritAggregator.tierThresholds();
        for (int i = 0; i < active.size(); i++) {
            heights[i] = townstead$spiritSectionHeight(active.get(i).id(),
                    contributorsBySpirit.getOrDefault(active.get(i).id(), java.util.List.of()));
            totalHeight += heights[i];
        }
        int viewport = contentBottom - contentTop;
        int maxScroll = Math.max(0, totalHeight - viewport);
        if (townstead$spiritScrollTarget > maxScroll) townstead$setSpiritScrollTarget(maxScroll);
        // Ease current toward target — roughly 10 frames to cover any distance.
        townstead$spiritScrollCurrent += (townstead$spiritScrollTarget
                - townstead$spiritScrollCurrent) * 0.25f;
        if (Math.abs(townstead$spiritScrollTarget - townstead$spiritScrollCurrent) < 0.5f) {
            townstead$spiritScrollCurrent = townstead$spiritScrollTarget;
        }

        // Hover detection for narration — which section is under the mouse
        // right now? Used to speak alt text when the hovered row changes.
        // Cached on mouse-unchanged frames to avoid rescanning every frame.
        String hoveredSpiritId;
        if (mouseX == townstead$lastHoverMouseX && mouseY == townstead$lastHoverMouseY) {
            hoveredSpiritId = townstead$lastHoverSpirit;
        } else {
            hoveredSpiritId = null;
            int hy = contentTop - Math.round(townstead$spiritScrollCurrent);
            for (int i = 0; i < active.size(); i++) {
                int secBot = hy + heights[i];
                if (mouseY >= hy && mouseY < secBot && mouseY >= contentTop && mouseY < contentBottom) {
                    hoveredSpiritId = active.get(i).id();
                    break;
                }
                hy += heights[i];
            }
            townstead$lastHoverMouseX = mouseX;
            townstead$lastHoverMouseY = mouseY;
            townstead$lastHoverSpirit = hoveredSpiritId;
        }
        townstead$narrateHover(hoveredSpiritId, snapshot, contributorsBySpirit);

        // Scissor + draw. Sections fully outside the viewport are skipped
        // entirely; scissor still clips the edges for sections that straddle.
        context.enableScissor(contentLeft - 2, contentTop, contentRight + 2, contentBottom);
        int y = contentTop - Math.round(townstead$spiritScrollCurrent);
        String dominantId = readout.primarySpiritId();
        long animNow = System.currentTimeMillis();
        for (int i = 0; i < active.size(); i++) {
            int sectionTop = y;
            int sectionBot = y + heights[i];
            if (sectionBot < contentTop || sectionTop > contentBottom) {
                y += heights[i];
                continue;
            }
            com.aetherianartificer.townstead.spirit.SpiritRegistry.Spirit s = active.get(i);
            int pts = snapshot.perSpirit().getOrDefault(s.id(), 0);
            java.util.List<ContributorEntry> contributors =
                    contributorsBySpirit.getOrDefault(s.id(), java.util.List.of());
            boolean isDominant = s.id().equals(dominantId);
            boolean isLast = i == active.size() - 1;
            townstead$drawSpiritSection(context, s, pts, snapshot.total(),
                    contributors, thresholds, contentLeft, y, contentRight,
                    isDominant, animNow, mouseX, mouseY, heights[i], isLast);
            y += heights[i];
        }
        context.disableScissor();

        // Scroll indicator on the right edge if content overflows.
        if (totalHeight > viewport) {
            int trackLeft = windowX + windowW - 6;
            context.fill(trackLeft, contentTop, trackLeft + 2, contentBottom, 0xFF1F2230);
            int thumbH = Math.max(12, viewport * viewport / Math.max(1, totalHeight));
            int thumbY = contentTop + (viewport - thumbH)
                    * Math.round(townstead$spiritScrollCurrent) / Math.max(1, maxScroll);
            context.fill(trackLeft, thumbY, trackLeft + 2, thumbY + thumbH, 0xFF8A8A8A);
        }
    }

    @Unique
    private record ContributorEntry(String buildingType, int count, int points) {}

    @Unique
    private record ContribCacheEntry(int buildingCount, int payloadTotal,
            java.util.Map<String, java.util.List<ContributorEntry>> data) {}

    @Unique
    private java.util.Map<String, java.util.List<ContributorEntry>> townstead$aggregateContributors(
            net.conczin.mca.server.world.data.Village village) {
        // Map: spirit id -> (building type -> [count, total points])
        java.util.Map<String, java.util.Map<String, int[]>> bySpirit = new java.util.HashMap<>();
        for (net.conczin.mca.server.world.data.Building b : village.getBuildings().values()) {
            if (!b.isComplete()) continue;
            String type = b.getType();
            java.util.Map<String, Integer> contributions =
                    com.aetherianartificer.townstead.spirit.BuildingSpiritIndex.contributionsFor(type);
            if (contributions.isEmpty()) continue;
            for (java.util.Map.Entry<String, Integer> e : contributions.entrySet()) {
                int pts = e.getValue();
                if (pts <= 0) continue;
                if (!com.aetherianartificer.townstead.spirit.SpiritRegistry.contains(e.getKey())) continue;
                bySpirit.computeIfAbsent(e.getKey(), k -> new java.util.HashMap<>())
                        .computeIfAbsent(type, k -> new int[]{0, 0});
                int[] agg = bySpirit.get(e.getKey()).get(type);
                agg[0]++;          // count
                agg[1] += pts;     // total points
            }
        }
        java.util.Map<String, java.util.List<ContributorEntry>> out = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, java.util.Map<String, int[]>> spiritEntry : bySpirit.entrySet()) {
            java.util.List<ContributorEntry> list = new java.util.ArrayList<>();
            for (java.util.Map.Entry<String, int[]> typeEntry : spiritEntry.getValue().entrySet()) {
                list.add(new ContributorEntry(typeEntry.getKey(),
                        typeEntry.getValue()[0], typeEntry.getValue()[1]));
            }
            // Sort by points desc so the biggest contributor reads first.
            list.sort((a, b) -> Integer.compare(b.points(), a.points()));
            out.put(spiritEntry.getKey(), list);
        }
        return out;
    }

    @Unique
    private java.util.List<com.aetherianartificer.townstead.spirit.SpiritRegistry.Spirit>
            townstead$buildActiveSpirits(
                    com.aetherianartificer.townstead.spirit.VillageSpiritSyncPayload snapshot,
                    java.util.Map<String, java.util.List<ContributorEntry>> contribs) {
        java.util.List<com.aetherianartificer.townstead.spirit.SpiritRegistry.Spirit> list = new java.util.ArrayList<>();
        for (com.aetherianartificer.townstead.spirit.SpiritRegistry.Spirit s :
                com.aetherianartificer.townstead.spirit.SpiritRegistry.ordered()) {
            if (snapshot.perSpirit().getOrDefault(s.id(), 0) > 0) list.add(s);
        }
        int total = snapshot.total();
        // Filter: hide under 10% share.
        if (townstead$spiritFilterThreshold && total > 0) {
            list.removeIf(s -> snapshot.perSpirit().getOrDefault(s.id(), 0) * 10 < total);
        }
        // Sort.
        switch (townstead$spiritSortMode) {
            case 1 -> list.sort((a, b) -> Integer.compare(
                    contribs.getOrDefault(b.id(), java.util.List.of()).size(),
                    contribs.getOrDefault(a.id(), java.util.List.of()).size()));
            case 2 -> list.sort((a, b) -> Component.translatable(a.displayKey()).getString()
                    .compareToIgnoreCase(Component.translatable(b.displayKey()).getString()));
            default -> list.sort((a, b) -> Integer.compare(
                    snapshot.perSpirit().getOrDefault(b.id(), 0),
                    snapshot.perSpirit().getOrDefault(a.id(), 0)));
        }
        // Filter: top 3 only (after sort if sort was by points, top 3 are biggest).
        if (townstead$spiritFilterTop3 && list.size() > 3) {
            // If the user picked alpha/count sort, "top 3" should still mean 3 by points.
            if (townstead$spiritSortMode != 0) {
                java.util.List<com.aetherianartificer.townstead.spirit.SpiritRegistry.Spirit> byPts =
                        new java.util.ArrayList<>(list);
                byPts.sort((a, b) -> Integer.compare(
                        snapshot.perSpirit().getOrDefault(b.id(), 0),
                        snapshot.perSpirit().getOrDefault(a.id(), 0)));
                java.util.Set<String> keep = new java.util.HashSet<>();
                for (int i = 0; i < 3 && i < byPts.size(); i++) keep.add(byPts.get(i).id());
                list.removeIf(s -> !keep.contains(s.id()));
            } else {
                list.subList(3, list.size()).clear();
            }
        }
        return list;
    }

    @Unique
    private int townstead$spiritSectionHeight(String spiritId, java.util.List<ContributorEntry> contributors) {
        double rs = townstead$rowScale();
        int headerH = (int) Math.round(18 * rs); // ~5 above text, 9 text, ~4 below
        if (townstead$collapsedSet().contains(spiritId)) {
            return headerH + 2;
        }
        int barH = (int) Math.round(10 * rs);
        int barGap = (int) Math.round(8 * rs);   // breathing room between bar and contributor list
        int contribH = (int) Math.round(10 * rs);
        int footer = (int) Math.round(8 * rs);
        return headerH + barH + barGap + Math.max(1, contributors.size()) * contribH + footer;
    }

    @Unique
    private void townstead$drawSpiritSection(GuiGraphics context,
                                             com.aetherianartificer.townstead.spirit.SpiritRegistry.Spirit s,
                                             int pts, int totalPts,
                                             java.util.List<ContributorEntry> contributors,
                                             int[] thresholds, int left, int y, int right,
                                             boolean isDominant, long animNowMs,
                                             int mouseX, int mouseY, int sectionHeight,
                                             boolean isLast) {
        int maxThreshold = thresholds[thresholds.length - 1];
        int tier = com.aetherianartificer.townstead.spirit.VillageSpiritAggregator.tierForSpirit(pts);
        boolean maxed = tier >= thresholds.length;
        boolean collapsed = townstead$collapsedSet().contains(s.id());
        boolean hc = townstead$a11yHighContrast();
        double rs = townstead$rowScale();
        double fs = townstead$a11yFontScale();
        int headerH = (int) Math.round(18 * rs);
        int textY = y + (int) Math.round(5 * rs); // balanced top/bottom padding around the 9px text

        // Per-row background tint in the spirit's own color. Inset top + bottom
        // by a few px so adjacent rows don't visually bleed into each other —
        // the panel's dark body reads as a clean break between sections.
        int tintTop = y + 1;
        int tintBot = y + sectionHeight - 5;
        int rowTintAlpha = hc ? 0x22000000 : 0x14000000; // ~13% / ~8%
        int rowTint = (s.color() & 0x00FFFFFF) | rowTintAlpha;
        context.fill(left - 4, tintTop, right + 4, tintBot, rowTint);

        // Hover highlight — additional wash on top when the mouse is inside.
        boolean hovered = mouseX >= left - 4 && mouseX <= right + 4
                && mouseY >= tintTop && mouseY < tintBot;
        if (hovered) {
            int tintAlpha = hc ? 0x60000000 : 0x20000000;
            int tintColor = (s.color() & 0x00FFFFFF) | tintAlpha;
            context.fill(left - 4, tintTop, right + 4, tintBot, tintColor);
        }

        // Chevron indicating collapse state — aligned with the spirit name text.
        int chevColor = hc ? 0xFFFFFFFF : 0xFF8A8A92;
        int chevX = left;
        int chevY = textY + 1;
        if (collapsed) {
            context.fill(chevX,     chevY,     chevX + 1, chevY + 5, chevColor);
            context.fill(chevX + 1, chevY + 1, chevX + 2, chevY + 4, chevColor);
            context.fill(chevX + 2, chevY + 2, chevX + 3, chevY + 3, chevColor);
        } else {
            context.fill(chevX,     chevY + 1, chevX + 5, chevY + 2, chevColor);
            context.fill(chevX + 1, chevY + 2, chevX + 4, chevY + 3, chevColor);
            context.fill(chevX + 2, chevY + 3, chevX + 3, chevY + 4, chevColor);
        }

        // Spirit icon — scales with row scale so it doesn't look tiny when
        // larger-hit-targets is on.
        float iconScale = (float) (0.625 * rs);
        context.pose().pushPose();
        context.pose().translate(left + 7, textY - 1, 0);
        context.pose().scale(iconScale, iconScale, 1f);
        context.renderItem(new net.minecraft.world.item.ItemStack(s.icon()), 0, 0);
        context.pose().popPose();

        int textLeft = left + (int) Math.round(19 * rs);
        Component label = Component.translatable(s.displayKey());
        int labelColor = hc ? 0xFFFFFFFF : s.color();
        townstead$drawScaledString(context, label, textLeft, textY, labelColor, fs);
        int labelW = (int) Math.round(this.font.width(label) * fs);
        if (tier >= 1) {
            String tierKey = "townstead.spirit.tier." + s.id() + "." + tier;
            String tierName = Component.translatable(tierKey).getString();
            int tierColor = hc ? 0xFFCCCCCC : 0xFF808890;
            townstead$drawScaledString(context, "\u00B7 " + tierName,
                    textLeft + labelW + 4, textY, tierColor, fs);
        }
        int sharePct = totalPts > 0 ? (int) Math.round(100.0 * pts / totalPts) : 0;
        String num;
        if (maxed) {
            num = pts + " pts \u00B7 " + sharePct + "% \u00B7 max";
        } else {
            int nextThreshold = thresholds[tier];
            int remain = nextThreshold - pts;
            String nextTierName = Component.translatable(
                    "townstead.spirit.tier." + s.id() + "." + (tier + 1)).getString();
            num = pts + " pts \u00B7 " + sharePct + "% \u00B7 " + remain + " \u2192 " + nextTierName;
        }
        int numW = (int) Math.round(this.font.width(num) * fs);
        int numColor = hc ? 0xFFFFFFFF : 0xC0C0C0;
        townstead$drawScaledString(context, num, right - numW, textY, numColor, fs);

        if (collapsed) {
            if (!isLast) {
                int divY = y + sectionHeight - 2;
                int divCol = hc ? 0xFF909090 : 0xFF2A2D35;
                context.fill(left - 2, divY, right + 2, divY + 1, divCol);
            }
            return;
        }

        // Bar — recessed dark trough with colored fill + 3-band gradient
        // (lighter top, mid body, deeper shadow at bottom).
        int barY = y + headerH;
        int barH = (int) Math.round(10 * rs);
        int barLeft = left;
        int barRight = right;
        int troughBorder = hc ? 0xFFFFFFFF : 0xFF454545;
        int troughBg = hc ? 0xFF000000 : 0xFF0E1014;
        context.fill(barLeft - 1, barY - 1, barRight + 1, barY + barH + 1, troughBorder);
        context.fill(barLeft, barY, barRight, barY + barH, troughBg);
        int fillWidth = Math.min(barRight - barLeft,
                (int) Math.round((double) pts / maxThreshold * (barRight - barLeft)));
        if (fillWidth > 0) {
            int fillEnd = barLeft + fillWidth;
            int base = s.color();
            context.fill(barLeft, barY, fillEnd, barY + barH, base);
            // Top 2px sheen — noticeably lighter.
            context.fill(barLeft, barY, fillEnd, barY + 2, townstead$lighten(base, 0.45f));
            // Row 2-3 — soft highlight transition.
            context.fill(barLeft, barY + 2, fillEnd, barY + 3, townstead$lighten(base, 0.2f));
            // Bottom 2px — deep shadow.
            context.fill(barLeft, barY + barH - 2, fillEnd, barY + barH,
                    townstead$lighten(base, -0.4f));
            // Row above that — mid shadow.
            context.fill(barLeft, barY + barH - 3, fillEnd, barY + barH - 2,
                    townstead$lighten(base, -0.15f));

            // A11y colorblind hatching overlay — distinct pattern per spirit.
            townstead$applyHatching(context, s.id(), barLeft, barY, fillEnd, barY + barH);

            // Dominant spirit shimmer — a bright band sweeping across the fill.
            if (isDominant) {
                double cycle = (animNowMs % 2600) / 2600.0;
                int sweep = (int) Math.round(cycle * (fillWidth + 30)) - 15;
                int peakX = barLeft + sweep;
                for (int dx = -5; dx <= 5; dx++) {
                    int sx = peakX + dx;
                    if (sx < barLeft || sx >= fillEnd) continue;
                    int falloff = 110 - Math.abs(dx) * 22;
                    if (falloff <= 0) continue;
                    int shimmerColor = (falloff << 24) | 0xFFFFFF;
                    context.fill(sx, barY + 1, sx + 1, barY + barH - 1, shimmerColor);
                }
            }
        }
        for (int t : thresholds) {
            int tx = barLeft + (int) Math.round((double) t / maxThreshold * (barRight - barLeft));
            boolean reached = pts >= t;
            context.fill(tx, barY, tx + 1, barY + barH, 0xFF000000);
            context.fill(tx, barY - 2, tx + 1, barY, reached ? 0xFFFFFFFF : 0xFF606068);
        }

        // Contributor list — small item icons followed by count · name · +pts.
        // Extra breathing room below the bar so the two sections read as
        // distinct groups (eventual pagination target).
        int barGap = (int) Math.round(8 * rs);
        int listY = barY + barH + barGap;
        int contribLineH = (int) Math.round(10 * rs);
        float contribIconScale = (float) (0.625 * rs);
        int contribTextX = left + (int) Math.round(14 * rs);
        int contribColor = hc ? 0xFFFFFFFF : 0xFFB0B0B0;
        int emptyColor = hc ? 0xFFCCCCCC : 0xFF707078;
        if (contributors.isEmpty()) {
            townstead$drawScaledString(context,
                    Component.translatable("townstead.spirit.no_contributors"),
                    left, listY, emptyColor, fs);
        } else {
            for (ContributorEntry c : contributors) {
                net.minecraft.world.item.ItemStack iconStack = townstead$catalogIconFor(c.buildingType());
                if (!iconStack.isEmpty()) {
                    context.pose().pushPose();
                    context.pose().translate(left + 2, listY - 1, 0);
                    context.pose().scale(contribIconScale, contribIconScale, 1f);
                    context.renderItem(iconStack, 0, 0);
                    context.pose().popPose();
                }
                String displayName = Component.translatable("buildingType." + c.buildingType()).getString();
                String line = c.count() + "x " + displayName + "  +" + c.points();
                townstead$drawScaledString(context, line, contribTextX, listY, contribColor, fs);
                listY += contribLineH;
            }
        }

        // Subtle divider between sections (skip the last one).
        if (!isLast) {
            int dividerY = y + sectionHeight - 3;
            int divCol = hc ? 0xFF909090 : 0xFF2A2D35;
            context.fill(left - 2, dividerY, right + 2, dividerY + 1, divCol);
        }
    }

    @Unique
    private int townstead$lighten(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.max(0, Math.min(255, (int) (((argb >>> 16) & 0xFF) * (1 + factor))));
        int g = Math.max(0, Math.min(255, (int) (((argb >>> 8) & 0xFF) * (1 + factor))));
        int b = Math.max(0, Math.min(255, (int) ((argb & 0xFF) * (1 + factor))));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Unique
    private void townstead$renderSpiritRadar(
            GuiGraphics context,
            com.aetherianartificer.townstead.spirit.VillageSpiritSyncPayload snapshot,
            int readoutColor,
            int contentLeft, int contentTop, int contentRight, int contentBottom) {
        java.util.List<com.aetherianartificer.townstead.spirit.SpiritRegistry.Spirit> spirits =
                com.aetherianartificer.townstead.spirit.SpiritRegistry.ordered();
        int n = spirits.size();
        int cx = (contentLeft + contentRight) / 2;
        int cy = (contentTop + contentBottom) / 2;
        int radius = Math.min((contentRight - contentLeft) / 2 - 46,
                (contentBottom - contentTop) / 2 - 20);
        if (radius < 20) radius = 20;
        int[] thresholds = com.aetherianartificer.townstead.spirit.VillageSpiritAggregator.tierThresholds();
        int maxPts = thresholds[thresholds.length - 1];

        double[] angles = new double[n];
        int[] axisX = new int[n];
        int[] axisY = new int[n];
        for (int i = 0; i < n; i++) {
            angles[i] = -Math.PI / 2 + 2 * Math.PI * i / n;
            axisX[i] = cx + (int) Math.round(Math.cos(angles[i]) * radius);
            axisY[i] = cy + (int) Math.round(Math.sin(angles[i]) * radius);
        }

        // Grid rings at each tier threshold as a heptagon outline.
        for (int t = 0; t < thresholds.length; t++) {
            double frac = (double) thresholds[t] / maxPts;
            int ringColor = t == thresholds.length - 1 ? 0x80707078 : 0x40606068;
            int[] rx = new int[n];
            int[] ry = new int[n];
            for (int i = 0; i < n; i++) {
                rx[i] = cx + (int) Math.round(Math.cos(angles[i]) * radius * frac);
                ry[i] = cy + (int) Math.round(Math.sin(angles[i]) * radius * frac);
            }
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                townstead$drawLine(context, rx[i], ry[i], rx[j], ry[j], ringColor);
            }
        }
        // Radial axes from center to each spirit tip.
        for (int i = 0; i < n; i++) {
            townstead$drawLine(context, cx, cy, axisX[i], axisY[i], 0x50606068);
        }

        // Data polygon — each spirit's share of tier 5 threshold maps to its
        // distance along that axis. Fill-ish effect done by drawing from the
        // center to each data point with accent color (cheap approximation).
        int[] dx = new int[n];
        int[] dy = new int[n];
        for (int i = 0; i < n; i++) {
            int pts = snapshot.perSpirit().getOrDefault(spirits.get(i).id(), 0);
            double frac = Math.min(1.0, (double) pts / maxPts);
            dx[i] = cx + (int) Math.round(Math.cos(angles[i]) * radius * frac);
            dy[i] = cy + (int) Math.round(Math.sin(angles[i]) * radius * frac);
        }
        int fillColor = (readoutColor & 0x00FFFFFF) | 0x30000000;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            // Two lines offset by 1px give a slightly thicker outline.
            townstead$drawLine(context, dx[i], dy[i], dx[j], dy[j], (readoutColor & 0x00FFFFFF) | 0xE0000000);
            townstead$drawLine(context, dx[i] + 1, dy[i], dx[j] + 1, dy[j], fillColor);
        }
        // Data point markers.
        for (int i = 0; i < n; i++) {
            com.aetherianartificer.townstead.spirit.SpiritRegistry.Spirit s = spirits.get(i);
            context.fill(dx[i] - 2, dy[i] - 2, dx[i] + 3, dy[i] + 3, 0xFF000000);
            context.fill(dx[i] - 1, dy[i] - 1, dx[i] + 2, dy[i] + 2, s.color());
        }

        // Tip labels — icon + name + points just outside each axis end.
        for (int i = 0; i < n; i++) {
            com.aetherianartificer.townstead.spirit.SpiritRegistry.Spirit s = spirits.get(i);
            int pts = snapshot.perSpirit().getOrDefault(s.id(), 0);
            int ox = (int) Math.round(Math.cos(angles[i]) * 14);
            int oy = (int) Math.round(Math.sin(angles[i]) * 14);
            int iconX = axisX[i] + ox - 5;
            int iconY = axisY[i] + oy - 5;
            context.pose().pushPose();
            context.pose().translate(iconX, iconY, 0);
            context.pose().scale(0.625f, 0.625f, 1f);
            context.renderItem(new net.minecraft.world.item.ItemStack(s.icon()), 0, 0);
            context.pose().popPose();
            String lbl = Component.translatable(s.displayKey()).getString() + " " + pts;
            context.pose().pushPose();
            context.pose().scale(0.75f, 0.75f, 1f);
            int lw = this.font.width(lbl);
            int fx = (int) Math.round((axisX[i] + ox) / 0.75) - lw / 2;
            int fy = (int) Math.round((axisY[i] + oy + 6) / 0.75);
            context.drawString(this.font, lbl, fx, fy, s.color(), false);
            context.pose().popPose();
        }
    }

    //? if neoforge {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_7933_", remap = false, at = @At("HEAD"), cancellable = true)
    *///?}
    private void townstead$spiritKeyPressed(int keyCode, int scanCode, int modifiers,
            CallbackInfoReturnable<Boolean> cir) {
        if (!TOWNSTEAD_SPIRIT_PAGE.equals(this.page)) return;
        // Esc — bounce back to the map page.
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            setPage("map");
            cir.setReturnValue(true);
            cir.cancel();
            return;
        }
        // Arrows — pixel-step scroll in list mode.
        if (!townstead$spiritRadarMode) {
            if (keyCode == GLFW.GLFW_KEY_UP) {
                townstead$setSpiritScrollTarget(townstead$spiritScrollTarget - 18);
                cir.setReturnValue(true);
                cir.cancel();
                return;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                townstead$setSpiritScrollTarget(townstead$spiritScrollTarget + 18);
                cir.setReturnValue(true);
                cir.cancel();
                return;
            }
            // Numeric 1..7 — jump to the n-th active spirit.
            int idx = -1;
            if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
                idx = keyCode - GLFW.GLFW_KEY_1;
            }
            if (idx >= 0) {
                BlueprintScreenAccessor accessor = (BlueprintScreenAccessor) (Object) this;
                net.conczin.mca.server.world.data.Village village = accessor.townstead$getVillage();
                if (village == null) return;
                java.util.Optional<com.aetherianartificer.townstead.spirit.VillageSpiritSyncPayload> snapshotOpt =
                        com.aetherianartificer.townstead.spirit.ClientVillageSpiritStore.get(village.getId());
                if (snapshotOpt.isEmpty()) return;
                com.aetherianartificer.townstead.spirit.VillageSpiritSyncPayload snapshot = snapshotOpt.get();
                java.util.Map<String, java.util.List<ContributorEntry>> contribs =
                        townstead$contribsFor(village, snapshot);
                java.util.List<com.aetherianartificer.townstead.spirit.SpiritRegistry.Spirit> active =
                        townstead$buildActiveSpirits(snapshot, contribs);
                if (idx >= active.size()) return;
                String targetId = active.get(idx).id();
                int offset = 0;
                for (com.aetherianartificer.townstead.spirit.SpiritRegistry.Spirit s : active) {
                    if (s.id().equals(targetId)) break;
                    offset += townstead$spiritSectionHeight(s.id(),
                            contribs.getOrDefault(s.id(), java.util.List.of()));
                }
                // Expand if collapsed so user sees the bar + contributors after jump.
                townstead$collapsedSet().remove(targetId);
                townstead$setSpiritScrollTarget(offset);
                cir.setReturnValue(true);
                cir.cancel();
            }
        }
    }

    //? if neoforge {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_6375_", remap = false, at = @At("HEAD"), cancellable = true)
    *///?}
    private void townstead$spiritMouseClicked(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (!TOWNSTEAD_SPIRIT_PAGE.equals(this.page) || button != 0) return;

        // View-mode pill — highest priority, works in both modes.
        int windowX0 = townstead$spiritWindowX();
        int windowY0 = townstead$spiritWindowY();
        int windowW0 = townstead$spiritWindowW();
        int pillX = townstead$spiritTogglePillX(windowX0, windowW0);
        int pillY = townstead$spiritTogglePillY(windowY0);
        int pillW = townstead$spiritTogglePillW();
        int pillH = townstead$spiritTogglePillH();
        if (mouseX >= pillX && mouseX < pillX + pillW
                && mouseY >= pillY && mouseY < pillY + pillH) {
            boolean wantRadar = mouseX >= pillX + pillW / 2;
            if (wantRadar != townstead$spiritRadarMode) {
                townstead$spiritRadarMode = wantRadar;
            }
            cir.setReturnValue(true);
            cir.cancel();
            return;
        }

        if (townstead$spiritRadarMode) return;

        BlueprintScreenAccessor accessor = (BlueprintScreenAccessor) (Object) this;
        net.conczin.mca.server.world.data.Village village = accessor.townstead$getVillage();
        if (village == null) return;
        java.util.Optional<com.aetherianartificer.townstead.spirit.VillageSpiritSyncPayload> snapshotOpt =
                com.aetherianartificer.townstead.spirit.ClientVillageSpiritStore.get(village.getId());
        if (snapshotOpt.isEmpty()) return;
        com.aetherianartificer.townstead.spirit.VillageSpiritSyncPayload snapshot = snapshotOpt.get();

        int windowX = townstead$spiritWindowX();
        int windowY = townstead$spiritWindowY();
        int windowW = townstead$spiritWindowW();
        int windowH = townstead$spiritWindowH();
        int headerTop = windowY + 22;
        int dividerY = headerTop + 26;
        int contentTop = dividerY + 22; // below sort/filter controls row
        int contentBottom = windowY + windowH - 6;
        int contentLeft = windowX + 10;
        int contentRight = windowX + windowW - 10;
        if (mouseX < contentLeft - 4 || mouseX > contentRight + 4) return;
        if (mouseY < contentTop || mouseY > contentBottom) return;

        java.util.Map<String, java.util.List<ContributorEntry>> contribs =
                townstead$contribsFor(village, snapshot);
        java.util.List<com.aetherianartificer.townstead.spirit.SpiritRegistry.Spirit> active =
                townstead$buildActiveSpirits(snapshot, contribs);

        double rs = townstead$rowScale();
        int headerH = (int) Math.round(18 * rs);
        int barH = (int) Math.round(10 * rs);
        int barGap = (int) Math.round(8 * rs);
        int contribLineH = (int) Math.round(10 * rs);
        int barBlock = headerH + barH + barGap; // header + bar + spacing
        int y = contentTop - Math.round(townstead$spiritScrollCurrent);
        for (com.aetherianartificer.townstead.spirit.SpiritRegistry.Spirit s : active) {
            java.util.List<ContributorEntry> list = contribs.getOrDefault(s.id(), java.util.List.of());
            int h = townstead$spiritSectionHeight(s.id(), list);
            int sectionTop = y;
            int sectionBot = y + h;
            if (mouseY >= sectionTop && mouseY < sectionBot) {
                int rel = (int) mouseY - sectionTop;
                if (rel < headerH) {
                    if (townstead$collapsedSet().contains(s.id())) {
                        townstead$collapsedSet().remove(s.id());
                    } else {
                        townstead$collapsedSet().add(s.id());
                    }
                    cir.setReturnValue(true);
                    cir.cancel();
                    return;
                }
                if (townstead$collapsedSet().contains(s.id())) break;
                if (rel < barBlock) {
                    int desired = sectionTop + Math.round(townstead$spiritScrollCurrent) - contentTop;
                    townstead$setSpiritScrollTarget(desired);
                    cir.setReturnValue(true);
                    cir.cancel();
                    return;
                }
                int contribIdx = (rel - barBlock) / Math.max(1, contribLineH);
                if (contribIdx >= 0 && contribIdx < list.size()) {
                    String buildingType = list.get(contribIdx).buildingType();
                    townstead$pendingCatalogBuildingType = buildingType;
                    setPage(TOWNSTEAD_CATALOG_PAGE);
                    cir.setReturnValue(true);
                    cir.cancel();
                    return;
                }
                break;
            }
            y += h;
        }
    }

    @Unique
    private void townstead$drawLine(GuiGraphics context, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1);
        int sx = x1 < x2 ? 1 : -1;
        int dy = -Math.abs(y2 - y1);
        int sy = y1 < y2 ? 1 : -1;
        int err = dx + dy;
        int x = x1;
        int y = y1;
        int guard = dx - dy + 2;
        while (guard-- > 0) {
            context.fill(x, y, x + 1, y + 1, color);
            if (x == x2 && y == y2) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x += sx; }
            if (e2 <= dx) { err += dx; y += sy; }
        }
    }

    @Unique
    private net.minecraft.world.item.ItemStack townstead$catalogIconFor(String buildingTypeName) {
        if (buildingTypeName == null || buildingTypeName.isEmpty()) return net.minecraft.world.item.ItemStack.EMPTY;
        if (!BuildingTypes.getInstance().getBuildingTypes().containsKey(buildingTypeName)) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        BuildingType bt = BuildingTypes.getInstance().getBuildingType(buildingTypeName);
        if (bt == null) return net.minecraft.world.item.ItemStack.EMPTY;
        return townstead$resolveNodeIcon(bt);
    }

    @Unique
    private int townstead$blend(int base, int accent, float mix) {
        int ba = (base >>> 24) & 0xFF;
        int br = (base >>> 16) & 0xFF;
        int bg = (base >>> 8) & 0xFF;
        int bb = base & 0xFF;
        int aa = (accent >>> 24) & 0xFF;
        int ar = (accent >>> 16) & 0xFF;
        int ag = (accent >>> 8) & 0xFF;
        int ab = accent & 0xFF;
        int r = (int) (br + (ar - br) * mix);
        int g = (int) (bg + (ag - bg) * mix);
        int b = (int) (bb + (ab - bb) * mix);
        int a = (int) (ba + (aa - ba) * mix);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Unique
    private int townstead$spiritAccentColor(com.aetherianartificer.townstead.spirit.SpiritReadout readout) {
        if (readout.primarySpiritId() != null) {
            var spirit = com.aetherianartificer.townstead.spirit.SpiritRegistry.get(readout.primarySpiritId());
            if (spirit.isPresent()) return spirit.get().color();
        }
        return 0xFFE3D18A;
    }
}
