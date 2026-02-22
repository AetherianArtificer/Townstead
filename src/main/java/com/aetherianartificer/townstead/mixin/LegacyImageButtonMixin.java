package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.compat.ModCompat;
import net.conczin.mca.client.gui.widget.LegacyImageButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LegacyImageButton.class)
public abstract class LegacyImageButtonMixin {
    private static final ResourceLocation COOKING_POT_ID = ResourceLocation.fromNamespaceAndPath("farmersdelight", "cooking_pot");
    private static final String KITCHEN_KEY_PREFIX = "buildingType.compat/farmersdelight/kitchen_l";
    private static final int BG_NORMAL = 0xFF9B9B9B;
    private static final int BG_HOVER = 0xFF5B86C8;
    private static final int BG_DISABLED = 0xFF6D6D6D;
    private static final int BORDER_OUTER = 0xFF2A2A2A;
    private static final int BORDER_INNER = 0xBFEAEAEA;

    @Inject(method = "renderTexture", at = @At("HEAD"), cancellable = true)
    private void townstead$renderKitchenCustomBackground(
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
        LegacyImageButton self = (LegacyImageButton) (Object) this;
        if (!townstead$isKitchenBuildingButton(self.getMessage())) return;

        int fill = !self.active ? BG_DISABLED : (self.isHoveredOrFocused() ? BG_HOVER : BG_NORMAL);
        context.fill(x, y, x + width, y + height, BORDER_OUTER);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
        context.fill(x + 1, y + 1, x + width - 1, y + 2, BORDER_INNER);
        context.fill(x + 1, y + 1, x + 2, y + height - 1, BORDER_INNER);
        ci.cancel();
    }

    @Inject(method = "renderWidget", at = @At("TAIL"))
    private void townstead$renderKitchenIconOverlay(GuiGraphics context, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (!ModCompat.isLoaded("farmersdelight")) return;

        LegacyImageButton self = (LegacyImageButton) (Object) this;
        if (!townstead$isKitchenBuildingButton(self.getMessage())) return;

        Item item = BuiltInRegistries.ITEM.get(COOKING_POT_ID);
        if (item == null) return;
        ItemStack icon = new ItemStack(item);
        if (icon.isEmpty()) return;

        int x = self.getX() + ((self.getWidth() - 16) / 2);
        int y = self.getY() + ((self.getHeight() - 16) / 2);
        context.renderItem(icon, x, y);
    }

    private static boolean townstead$isKitchenBuildingButton(Component message) {
        if (message == null) return false;
        if (!(message.getContents() instanceof TranslatableContents translatable)) return false;
        return translatable.getKey().startsWith(KITCHEN_KEY_PREFIX);
    }
}
