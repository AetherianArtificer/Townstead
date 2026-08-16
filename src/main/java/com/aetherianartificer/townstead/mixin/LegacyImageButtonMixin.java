package com.aetherianartificer.townstead.mixin;

//? if neoforge {
import com.aetherianartificer.townstead.compat.BuildingIconResolver;
import net.conczin.mca.client.gui.widget.LegacyImageButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Replace the broken atlas-blit icons shown by MCA's vanilla catalog for any
 * Townstead-added building type with a proper item icon. Works for Butcher
 * Shop tiers, peripheral butchery buildings, Farmer's Delight kitchens,
 * Rustic Delight cafes, Dock tiers, and any future compat building that sets
 * {@code townsteadNodeItem} plus a unique {@code iconU/iconV} slot.
 *
 * <p>Detection is by the button's {@code xTexStart/yTexStart} fields (which
 * MCA sets from the type's {@code iconU/iconV}), resolved through
 * {@link BuildingIconResolver}. If the UV does not map to a Townstead-known
 * type, the vanilla render path is left untouched so the built-in sprites
 * for vanilla MCA building types keep working.
 */
@Mixin(LegacyImageButton.class)
public abstract class LegacyImageButtonMixin {
    private static final int BG_NORMAL = 0xFF9B9B9B;
    private static final int BG_HOVER = 0xFF5B86C8;
    private static final int BG_DISABLED = 0xFF6D6D6D;
    private static final int BORDER_OUTER = 0xFF2A2A2A;
    private static final int BORDER_INNER = 0xBFEAEAEA;

    /**
     * MCA constructs catalog buttons with {@code yTexStart = buildingType.iconV() + 20}
     * so the atlas blit skips a 20-pixel header row. To match against the
     * raw {@code iconV} value declared in the building-type JSON, subtract
     * the same offset before looking up.
     */
    private static final int MCA_CATALOG_V_OFFSET = 20;

    @Shadow(remap = false)
    private int xTexStart;

    @Shadow(remap = false)
    private int yTexStart;

    @Inject(method = "renderTexture", at = @At("HEAD"), cancellable = true)
    private void townstead$renderCompatBackground(
            GuiGraphics context,
            ResourceLocation texture,
            int x,
            int y,
            int xTexStart,
            int yTexStart,
            int yDiffTex,
            int width,
            int height,
            int textureWidth,
            int textureHeight,
            CallbackInfo ci
    ) {
        Optional<ResourceLocation> nodeItem =
                BuildingIconResolver.nodeItemForIconUv(xTexStart, yTexStart - MCA_CATALOG_V_OFFSET);
        if (nodeItem.isEmpty()) return;

        LegacyImageButton self = (LegacyImageButton) (Object) this;
        int fill = !self.active ? BG_DISABLED : (self.isHoveredOrFocused() ? BG_HOVER : BG_NORMAL);
        context.fill(x, y, x + width, y + height, BORDER_OUTER);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
        context.fill(x + 1, y + 1, x + width - 1, y + 2, BORDER_INNER);
        context.fill(x + 1, y + 1, x + 2, y + height - 1, BORDER_INNER);
        ci.cancel();
    }

    @Inject(method = "renderWidget", at = @At("TAIL"))
    private void townstead$renderCompatIconOverlay(GuiGraphics context, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        Optional<ResourceLocation> nodeItem =
                BuildingIconResolver.nodeItemForIconUv(xTexStart, yTexStart - MCA_CATALOG_V_OFFSET);
        if (nodeItem.isEmpty() || !BuiltInRegistries.ITEM.containsKey(nodeItem.get())) return;

        Item item = BuiltInRegistries.ITEM.get(nodeItem.get());
        if (item == null) return;
        ItemStack icon = new ItemStack(item);
        if (icon.isEmpty()) return;

        LegacyImageButton self = (LegacyImageButton) (Object) this;
        int x = self.getX() + ((self.getWidth() - 16) / 2);
        int y = self.getY() + ((self.getHeight() - 16) / 2);
        context.renderItem(icon, x, y);
    }
}
//?} else {
/*import com.aetherianartificer.townstead.compat.BuildingIconResolver;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

// Forge 1.20.1 counterpart for MCA's vanilla ImageButton catalog entries.
// MCA passes the building type's icon UV directly into the button, so Townstead's
// reserved off-atlas slots can be resolved back to their configured node items.
@Mixin(ImageButton.class)
public abstract class LegacyImageButtonMixin {
    private static final int BG_NORMAL = 0xFF9B9B9B;
    private static final int BG_HOVER = 0xFF5B86C8;
    private static final int BG_DISABLED = 0xFF6D6D6D;
    private static final int BORDER_OUTER = 0xFF2A2A2A;
    private static final int BORDER_INNER = 0xBFEAEAEA;
    private static final int MCA_CATALOG_V_OFFSET = 20;

    @Unique private int townstead$xTexStart;
    @Unique private int townstead$yTexStart;

    @Inject(
            method = "<init>(IIIIIIILnet/minecraft/resources/ResourceLocation;IILnet/minecraft/client/gui/components/Button$OnPress;Lnet/minecraft/network/chat/Component;)V",
            remap = false,
            at = @At("RETURN")
    )
    private void townstead$captureCompatCatalogUv(
            int x, int y, int width, int height,
            int xTexStart, int yTexStart, int yDiffTex,
            ResourceLocation texture, int textureWidth, int textureHeight,
            Button.OnPress onPress, Component message, CallbackInfo ci) {
        this.townstead$xTexStart = xTexStart;
        this.townstead$yTexStart = yTexStart;
    }

    @Inject(method = "m_87963_", remap = false, at = @At("HEAD"), cancellable = true)
    private void townstead$renderCompatCatalogButton(
            GuiGraphics context, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        Optional<ResourceLocation> nodeItem =
                BuildingIconResolver.nodeItemForIconUv(
                        townstead$xTexStart, townstead$yTexStart - MCA_CATALOG_V_OFFSET);
        if (nodeItem.isEmpty() || !BuiltInRegistries.ITEM.containsKey(nodeItem.get())) return;

        Item item = BuiltInRegistries.ITEM.get(nodeItem.get());
        if (item == null) return;
        ItemStack icon = new ItemStack(item);
        if (icon.isEmpty()) return;

        ImageButton self = (ImageButton) (Object) this;
        int x = self.getX();
        int y = self.getY();
        int width = self.getWidth();
        int height = self.getHeight();
        int fill = !self.active ? BG_DISABLED : (self.isHoveredOrFocused() ? BG_HOVER : BG_NORMAL);
        context.fill(x, y, x + width, y + height, BORDER_OUTER);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
        context.fill(x + 1, y + 1, x + width - 1, y + 2, BORDER_INNER);
        context.fill(x + 1, y + 1, x + 2, y + height - 1, BORDER_INNER);
        context.renderItem(icon, x + ((width - 16) / 2), y + ((height - 16) / 2));
        ci.cancel();
    }
}
*///?}
