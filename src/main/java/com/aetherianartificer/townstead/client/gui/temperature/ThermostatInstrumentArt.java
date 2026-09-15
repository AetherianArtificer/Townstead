package com.aetherianartificer.townstead.client.gui.temperature;

import com.aetherianartificer.townstead.client.gui.common.Palette;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Pixel-aligned instrument furniture; the needle and scale remain live GUI geometry. */
final class ThermostatInstrumentArt {
    private ThermostatInstrumentArt() {}

    static void housing(GuiGraphics g,int x,int y,int w,int h) {
        g.fill(x-2,y-2,x+w+2,y+h+3,Palette.BRASS_DEEP);
        g.fill(x,y,x+w,y+h,0xFFC4A165);
        g.fill(x,y,x+w,y+2,Palette.LABEL_LIGHT);
        g.fill(x,y,x+2,y+h,Palette.LABEL_LIGHT);
        g.fill(x+w-2,y,x+w,y+h,Palette.BRASS_DEEP);
        g.fill(x,y+h-2,x+w,y+h,Palette.BRASS_DEEP);
        for(int dx:new int[]{4,w-8}) for(int dy:new int[]{4,h-8}) {
            g.fill(x+dx,y+dy,x+dx+4,y+dy+4,Palette.BRASS_DEEP);
            g.fill(x+dx,y+dy,x+dx+3,y+dy+1,Palette.LABEL_LIGHT);
            g.fill(x+dx+1,y+dy+2,x+dx+3,y+dy+3,Palette.INK_TEXT);
        }
    }

    private static void octagon(GuiGraphics g,int cx,int cy,int radius,int cut,int color) {
        for(int row=-radius;row<radius;row++) {
            int inset=Math.max(0,Math.abs(row<0?row:row+1)-(radius-cut));
            g.fill(cx-radius+inset,cy+row,cx+radius-inset,cy+row+1,color);
        }
    }

    static void dial(GuiGraphics g,Font font,int cx,int cy,int target,boolean fahrenheit) {
        octagon(g,cx+1,cy+2,64,19,Palette.BRASS_DEEP);
        octagon(g,cx,cy,64,19,Palette.BRASS);
        octagon(g,cx,cy,60,18,Palette.CARD);
        for(int t=5;t<=35;t++) {
            double a=Math.toRadians(angle(t));
            boolean major=t%5==0;
            int ink=t>=30?Palette.INK_BAD:Palette.INK_DIM;
            for(int r=major?49:52;r<=55;r++) {
                int px=cx+(int)Math.round(Math.sin(a)*r),py=cy-(int)Math.round(Math.cos(a)*r);
                g.fill(px,py,px+1,py+1,ink);
            }
            if(major) {
                String label=Integer.toString(fahrenheit?(int)Math.round(t*1.8+32):t);
                int tx=cx+(int)Math.round(Math.sin(a)*39),ty=cy-(int)Math.round(Math.cos(a)*39);
                g.drawString(font,label,tx-font.width(label)/2,ty-4,Palette.INK_TEXT,false);
            }
        }
        String unit=fahrenheit?"°F":"°C";
        g.drawString(font,unit,cx-font.width(unit)/2,cy+22,Palette.INK_DIM,false);
        g.pose().pushPose();
        g.pose().translate(cx,cy,0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(angle(target)));
        g.fill(-1,-31,1,7,Palette.INK_BAD);
        g.pose().popPose();
        g.fill(cx-4,cy-4,cx+4,cy+4,Palette.BRASS_DEEP);
        g.fill(cx-2,cy-2,cx+2,cy+2,Palette.BRASS);
    }

    static float angle(int target) { return -135+(target-5)*9; }
}
