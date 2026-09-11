package com.aetherianartificer.townstead.client.gui.character;

import com.mojang.blaze3d.systems.RenderSystem;
import net.conczin.mca.client.gui.widget.ColorPickerWidget;
import net.conczin.mca.client.gui.widget.HorizontalColorPickerWidget;
import net.conczin.mca.client.gui.widget.WidgetUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * One authored hair gradient as a horizontal shade bar: MCA's 1-D picker (the control its own HSV
 * sliders use) drawn from a gradient strip, showing its cursor only while this bar holds the
 * current dye. Stacked bars then read as "pick a family, then a shade".
 */
public class HairBarWidget extends HorizontalColorPickerWidget {
    private final ResourceLocation strip;
    private boolean current;

    public HairBarWidget(int x, int y, int width, int height, double value, ResourceLocation strip,
            ColorPickerWidget.DualConsumer<Double, Double> consumer) {
        super(x, y, width, height, value, strip, consumer);
        this.strip = strip;
    }

    public void setCurrent(boolean current) {
        this.current = current;
    }

    @Override
    public void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        RenderSystem.setShaderColor(1, 1, 1, alpha);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        context.blit(strip, getX(), getY(), width, height, 0f, 0f, width, height, width, height);
        WidgetUtils.drawRectangle(context, getX(), getY(), getX() + width, getY() + height,
                current ? 0xffffffff : 0xaaffffff);
        if (current) {
            context.blit(MCA_GUI_ICONS_TEXTURE, (int) (getX() + getValueX() * width) - 8,
                    getY() + height / 2 - 8, 16, 16, 240f, 0f, 16, 16, 256, 256);
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }
}
