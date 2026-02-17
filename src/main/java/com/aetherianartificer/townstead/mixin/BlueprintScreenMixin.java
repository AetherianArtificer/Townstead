package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.farming.FarmingPolicyClientStore;
import com.aetherianartificer.townstead.farming.FarmingPolicySetPayload;
import com.aetherianartificer.townstead.farming.pattern.FarmPatternDefinition;
import com.aetherianartificer.townstead.farming.pattern.FarmPatternRegistry;
import com.aetherianartificer.townstead.mixin.accessor.BlueprintScreenAccessor;
import net.conczin.mca.client.gui.BlueprintScreen;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.util.compat.ButtonWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Mixin(BlueprintScreen.class)
public abstract class BlueprintScreenMixin extends Screen {
    @Shadow private String page;
    @Shadow private void setPage(String page) {}

    @Unique private static final String TOWNSTEAD_FARMING_PAGE = "townstead_farming";
    @Unique private static final int NAV_BUTTON_WIDTH = 80;
    @Unique private static final int NAV_BUTTON_HEIGHT = 20;
    @Unique private static final int NAV_BUTTON_STEP = 22;
    @Unique private static final int NAV_VISIBLE_ROWS = 6;
    @Unique private static final long POLICY_DEBOUNCE_NANOS = 150_000_000L; // 150ms
    @Unique private static final String KITCHEN_TYPE_PREFIX = "compat/farmersdelight/kitchen_l";
    @Unique private static final int KITCHEN_ICON_U = 240;
    @Unique private static final int KITCHEN_ICON_V = 180;
    @Unique private static final ResourceLocation KITCHEN_MAP_ICON_ITEM = ResourceLocation.fromNamespaceAndPath("farmersdelight", "cooking_pot");

    @Unique private final List<Button> townstead$navButtons = new ArrayList<>();
    @Unique private final Map<Button, Integer> townstead$navBaseY = new IdentityHashMap<>();
    @Unique private int townstead$navScrollPx = 0;
    @Unique private Button townstead$farmingNavButton;

    @Unique private List<String> townstead$farmingFamilies = List.of();
    @Unique private int townstead$farmingFamilyIndex = 0;
    @Unique private int townstead$farmingTier = 3;
    @Unique private boolean townstead$pendingPatternChange = false;
    @Unique private String townstead$pendingFamily = "starter_rows";
    @Unique private boolean townstead$pendingPolicySend = false;
    @Unique private long townstead$lastPolicyInputNanos = 0L;
    @Unique private Button townstead$farmPatternValue;

    private BlueprintScreenMixin() {
        super(Component.empty());
    }

    @Inject(method = "setPage", at = @At("TAIL"))
    private void townstead$injectFarmingPage(String pageName, CallbackInfo ci) {
        townstead$collectNavButtons();
        townstead$ensureFarmingNavButton();
        townstead$applyNavScroll();

        if (TOWNSTEAD_FARMING_PAGE.equals(this.page)) {
            townstead$refreshFamilies();
            townstead$syncFromClientStore();
            PacketDistributor.sendToServer(new FarmingPolicySetPayload("", -1));
            townstead$addFarmingPageControls();
        } else {
            townstead$farmPatternValue = null;
        }
        if ("catalog".equals(this.page)) {
            townstead$applyKitchenTierCatalogVisibility();
        }
        if (townstead$farmingNavButton != null) {
            townstead$farmingNavButton.active = !TOWNSTEAD_FARMING_PAGE.equals(this.page);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void townstead$renderFarmingPage(GuiGraphics context, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (!TOWNSTEAD_FARMING_PAGE.equals(this.page)) return;

        townstead$syncFromClientStore();
        townstead$flushDebouncedPolicyIfReady();
        townstead$refreshFarmingLabels();

        int cx = this.width / 2 + 38;
        int cy = this.height / 2 - 52;
        context.drawCenteredString(this.font, Component.translatable("gui.blueprint.farming"), cx, cy, 0xFFFFFF);
        context.drawCenteredString(this.font, Component.translatable("townstead.blueprint.farming.pattern"), cx, cy + 14, 0xA0A0A0);
        context.drawCenteredString(this.font, Component.translatable("townstead.blueprint.farming.tier.auto"), cx, cy + 38, 0xA0A0A0);
    }

    @Inject(method = "drawBuildingIcon", at = @At("HEAD"), cancellable = true)
    private void townstead$drawKitchenBuildingIcon(
            GuiGraphics context,
            ResourceLocation texture,
            int x,
            int y,
            int u,
            int v,
            CallbackInfo ci
    ) {
        if (u != KITCHEN_ICON_U || v != KITCHEN_ICON_V) return;
        Item item = BuiltInRegistries.ITEM.get(KITCHEN_MAP_ICON_ITEM);
        if (item == null) return;
        ItemStack stack = new ItemStack(item);
        if (stack.isEmpty()) return;
        // Match MCA map icon visual weight (smaller than full 16x16 item render).
        context.pose().pushPose();
        context.pose().translate(x - 6.0, y - 6.0, 0.0);
        context.pose().scale(0.75f, 0.75f, 1.0f);
        context.renderItem(stack, 0, 0);
        context.pose().popPose();
        ci.cancel();
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void townstead$scrollNav(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
        if (townstead$navButtons.isEmpty()) return;
        int left = this.width / 2 - 180;
        int top = this.height / 2 - 56;
        int right = left + NAV_BUTTON_WIDTH;
        int bottom = top + (NAV_VISIBLE_ROWS * NAV_BUTTON_STEP);
        if (!(mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom)) return;

        int overflowRows = Math.max(0, townstead$navButtons.size() - NAV_VISIBLE_ROWS);
        if (overflowRows <= 0) return;
        int maxScroll = overflowRows * NAV_BUTTON_STEP;

        if (verticalAmount < 0) {
            townstead$navScrollPx = Math.max(-maxScroll, townstead$navScrollPx - NAV_BUTTON_STEP);
        } else if (verticalAmount > 0) {
            townstead$navScrollPx = Math.min(0, townstead$navScrollPx + NAV_BUTTON_STEP);
        }
        townstead$applyNavScroll();
        cir.setReturnValue(true);
    }

    @Unique
    private void townstead$collectNavButtons() {
        townstead$navButtons.clear();
        townstead$navBaseY.clear();

        int navX = this.width / 2 - 180;
        for (GuiEventListener listener : this.children()) {
            if (!(listener instanceof Button b)) continue;
            if (b.getWidth() != NAV_BUTTON_WIDTH) continue;
            if (b.getHeight() != NAV_BUTTON_HEIGHT) continue;
            if (b.getX() != navX) continue;
            townstead$navButtons.add(b);
        }
        townstead$navButtons.sort(Comparator.comparingInt(Button::getY));
        for (Button b : townstead$navButtons) {
            townstead$navBaseY.put(b, b.getY());
        }
    }

    @Unique
    private void townstead$ensureFarmingNavButton() {
        if (townstead$farmingNavButton != null && this.children().contains(townstead$farmingNavButton)) {
            if (!townstead$navButtons.contains(townstead$farmingNavButton)) {
                townstead$navButtons.add(townstead$farmingNavButton);
            }
            return;
        }

        int navX = this.width / 2 - 180;
        int navYStart = this.height / 2 - 56;
        int y = navYStart + (NAV_BUTTON_STEP * townstead$navButtons.size());

        townstead$farmingNavButton = addRenderableWidget(new ButtonWidget(
                navX, y, NAV_BUTTON_WIDTH, NAV_BUTTON_HEIGHT,
                Component.translatable("gui.blueprint.farming"),
                b -> setPage(TOWNSTEAD_FARMING_PAGE)
        ));
        townstead$navButtons.add(townstead$farmingNavButton);
        townstead$navBaseY.put(townstead$farmingNavButton, y);
    }

    @Unique
    private void townstead$applyNavScroll() {
        for (Button b : townstead$navButtons) {
            Integer baseY = townstead$navBaseY.get(b);
            if (baseY == null) continue;
            b.setY(baseY + townstead$navScrollPx);
        }
    }

    @Unique
    private void townstead$addFarmingPageControls() {
        int x = this.width / 2 - 26;
        int y = this.height / 2 - 32;

        addRenderableWidget(new ButtonWidget(x, y, 20, 20, Component.literal("<"), b -> townstead$cycleFamily(-1)));
        townstead$farmPatternValue = addRenderableWidget(new ButtonWidget(x + 22, y, 84, 20, Component.empty(), b -> {}));
        townstead$farmPatternValue.active = false;
        addRenderableWidget(new ButtonWidget(x + 108, y, 20, 20, Component.literal(">"), b -> townstead$cycleFamily(1)));

        townstead$refreshFarmingLabels();
    }

    @Unique
    private void townstead$refreshFamilies() {
        List<String> families = FarmPatternRegistry.all().stream()
                .map(FarmPatternDefinition::family)
                .filter(f -> f != null && !f.isBlank())
                .distinct()
                .sorted(Comparator.comparing((String f) -> !"starter_rows".equals(f)).thenComparing(String::compareTo))
                .toList();
        if (families.isEmpty()) families = List.of("starter_rows");
        townstead$farmingFamilies = new ArrayList<>(families);
    }

    @Unique
    private void townstead$syncFromClientStore() {
        String currentFamily = FarmingPolicyClientStore.getPatternId();
        townstead$farmingTier = Math.max(1, Math.min(5, FarmingPolicyClientStore.getTier()));
        if (townstead$pendingPatternChange) {
            if (currentFamily.equals(townstead$pendingFamily)) {
                townstead$pendingPatternChange = false;
            } else {
                // Keep optimistic UI selection until server confirms.
                return;
            }
        }
        int idx = townstead$farmingFamilies.indexOf(currentFamily);
        if (idx >= 0) {
            townstead$farmingFamilyIndex = idx;
        } else if (!townstead$farmingFamilies.isEmpty()) {
            townstead$farmingFamilyIndex = 0;
        }
    }

    @Unique
    private void townstead$cycleFamily(int delta) {
        if (townstead$farmingFamilies.isEmpty()) return;
        int size = townstead$farmingFamilies.size();
        int next = (townstead$farmingFamilyIndex + delta) % size;
        if (next < 0) next += size;
        townstead$farmingFamilyIndex = next;
        townstead$pendingFamily = townstead$farmingFamilies.get(townstead$farmingFamilyIndex);
        townstead$pendingPatternChange = true;
        townstead$queuePolicySend();
        townstead$refreshFarmingLabels();
    }

    @Unique
    private void townstead$sendPolicy() {
        String family = townstead$farmingFamilies.isEmpty()
                ? "starter_rows"
                : townstead$farmingFamilies.get(townstead$farmingFamilyIndex);
        // Keep policy tier cap at max so per-villager progression drives effective unlocks.
        PacketDistributor.sendToServer(new FarmingPolicySetPayload(family, 5));
    }

    @Unique
    private void townstead$queuePolicySend() {
        townstead$pendingPolicySend = true;
        townstead$lastPolicyInputNanos = System.nanoTime();
    }

    @Unique
    private void townstead$flushDebouncedPolicyIfReady() {
        if (!townstead$pendingPolicySend) return;
        long now = System.nanoTime();
        if (now - townstead$lastPolicyInputNanos < POLICY_DEBOUNCE_NANOS) return;
        townstead$pendingPolicySend = false;
        townstead$sendPolicy();
    }

    @Unique
    private void townstead$refreshFarmingLabels() {
        if (townstead$farmPatternValue != null) {
            String family = townstead$farmingFamilies.isEmpty()
                    ? "starter_rows"
                    : townstead$farmingFamilies.get(townstead$farmingFamilyIndex);
            townstead$farmPatternValue.setMessage(Component.literal(townstead$displayFamilyName(family)));
        }
    }

    @Unique
    private String townstead$displayFamilyName(String family) {
        String key = "townstead.farming.family." + family;
        String translated = Component.translatable(key).getString();
        if (!translated.equals(key)) return translated;
        String[] parts = family.split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(part.substring(0, 1).toUpperCase(Locale.ROOT))
                    .append(part.substring(1));
        }
        return out.toString();
    }

    @Unique
    private void townstead$applyKitchenTierCatalogVisibility() {
        BlueprintScreenAccessor accessor = (BlueprintScreenAccessor) (Object) this;
        Set<String> builtTypes = new HashSet<>();
        if (accessor.townstead$getVillage() != null) {
            for (Building building : accessor.townstead$getVillage().getBuildings().values()) {
                builtTypes.add(building.getType());
            }
        }
        for (Button button : accessor.townstead$getCatalogButtons()) {
            int tier = townstead$kitchenTierFromButton(button);
            if (tier <= 0) continue;
            button.visible = townstead$isKitchenTierUnlocked(tier, builtTypes);
        }
    }

    @Unique
    private int townstead$kitchenTierFromButton(Button button) {
        if (button == null) return -1;
        Component message = button.getMessage();
        if (message == null) return -1;
        if (!(message.getContents() instanceof TranslatableContents translatable)) return -1;
        String key = translatable.getKey();
        String expectedPrefix = "buildingType." + KITCHEN_TYPE_PREFIX;
        if (!key.startsWith(expectedPrefix)) return -1;
        String suffix = key.substring(expectedPrefix.length());
        try {
            return Integer.parseInt(suffix);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    @Unique
    private boolean townstead$isKitchenTierUnlocked(int tier, Set<String> builtTypes) {
        if (tier <= 1) return true;
        return builtTypes.contains(KITCHEN_TYPE_PREFIX + (tier - 1));
    }
}
