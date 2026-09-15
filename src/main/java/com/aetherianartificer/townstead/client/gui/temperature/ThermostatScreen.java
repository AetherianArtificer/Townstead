package com.aetherianartificer.townstead.client.gui.temperature;

import com.aetherianartificer.townstead.block.RoomThermostatBlock;
import com.aetherianartificer.townstead.temperature.*;
import net.minecraft.client.Minecraft;
import com.aetherianartificer.townstead.client.gui.common.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** A local draft with explicit Apply; sensor updates never overwrite unsaved choices. */
public final class ThermostatScreen extends Screen {
    private final BlockPos pos;
    private ThermostatSnapshotPayload live;
    private int target,mode,age,pendingTicks;
    private boolean pending;
    private int left,top;
    private Button apply,minus,plus,discard;
    private final ParchmentButton[] modes=new ParchmentButton[3];
    private static final int WIDTH=300,HEIGHT=230;
    private ThermostatScreen(ThermostatSnapshotPayload snapshot) {
        super(Component.translatable("block.townstead.room_thermostat"));
        pos=snapshot.pos(); live=snapshot; target=snapshot.target(); mode=snapshot.mode();
    }
    public static void accept(ThermostatSnapshotPayload snapshot) {
        var mc=Minecraft.getInstance();
        if (mc.level==null || !ThermostatSettingsPolicy.valid(snapshot.mode(),snapshot.target())) return;
        if (snapshot.open()) {
            if (mc.level.isLoaded(snapshot.pos()) && mc.level.getBlockState(snapshot.pos()).getBlock() instanceof RoomThermostatBlock)
                mc.setScreen(new ThermostatScreen(snapshot));
        } else if (mc.screen instanceof ThermostatScreen screen && screen.pos.equals(snapshot.pos())) {
            screen.live=snapshot; screen.age=0;
            if (screen.pending && snapshot.mode()==screen.mode && snapshot.target()==screen.target) screen.pending=false;
        }
    }
    private static Component text(String key,Object... args) { return Component.translatable("thermostat.townstead.ui."+key,args); }
    private boolean dirty() { return target!=live.target() || mode!=live.mode(); }
    @Override protected void init() {
        left=(width-WIDTH)/2; top=(height-HEIGHT)/2;
        minus=addRenderableWidget(new ParchmentButton(left+156,top+31,24,22,Component.literal("-"),b->target=Math.max(5,target-1)));
        plus=addRenderableWidget(new ParchmentButton(left+264,top+31,24,22,Component.literal("+"),b->target=Math.min(35,target+1)));
        for (int i=0;i<3;i++) {
            int selected=new int[]{1,2,0}[i];
            modes[selected]=addRenderableWidget(new ParchmentButton(left+156,top+64+i*24,132,20,Component.translatable("thermostat.townstead.mode."+
                    RoomThermostatBlock.Mode.values()[selected].getSerializedName()),b->mode=selected));
        }
        apply=addRenderableWidget(new ParchmentButton(left+156,top+204,132,20,text("apply"),b->{pending=true;pendingTicks=0;send(mode,target);}));
        discard=addRenderableWidget(new ParchmentButton(left+12,top+204,132,20,text("discard"),b->onClose()));
        updateButtons();
    }
    private void updateButtons() {
        if (apply==null) return;
        boolean editable=live.editable() && !pending && age<60;
        apply.active=editable && dirty();
        discard.active=true;
        minus.active=editable && target>5; plus.active=editable && target<35;
        for(int i=0;i<3;i++) { modes[i].active=editable; modes[i].setSelected(mode==i); }
    }
    private void send(int newMode,int newTarget) {
        var request=new ThermostatRequestPayload(pos,newMode,newTarget);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(request);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(request);
        *///?}
    }
    @Override public void tick() {
        super.tick(); age++;
        if (minecraft==null || minecraft.level==null || minecraft.player==null
                || !minecraft.level.isLoaded(pos) || !(minecraft.level.getBlockState(pos).getBlock() instanceof RoomThermostatBlock)
                || minecraft.player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>64) { onClose(); return; }
        if (pending && ++pendingTicks>100) pending=false;
        if (!pending && age%20==0) send(-1,0);
        updateButtons();
    }
    private String degrees(float value) { return ThermometerClient.format(value,ThermometerClient.fahrenheit(live.coldSweat())); }
    private void large(GuiGraphics g,String value,int center,int y,int color) {
        // Native pixel scale, without a dark text shadow over dark ink.
        g.drawString(font,value,center-font.width(value)/2,y,color,false);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial) {
        g.fill(0,0,width,height,0x990F1213);
        ThermostatInstrumentArt.housing(g,left,top,WIDTH,HEIGHT);
        boolean fahrenheit=ThermometerClient.fahrenheit(live.coldSweat());
        ThermostatInstrumentArt.dial(g,font,left+78,top+77,target,fahrenheit);
        Controls.fieldLabel(g,font,text("target").getString(),left+160,top+16);
        large(g,degrees(target),left+222,top+38,Palette.INK_TEXT);
        boolean fresh=age<60 && Float.isFinite(live.air());
        large(g,text("room_air").getString(),left+78,top+148,Palette.INK_DIM);
        large(g,fresh?degrees(live.air()):"--",left+78,top+162,Palette.INK_TEXT);
        Component band=mode==0?text("off_help"):text(mode==1?"heat_band":"cool_band",degrees(target-1),degrees(target+1));
        g.drawWordWrap(font,band,left+156,top+148,132,Palette.INK_DIM);
        FrameRenderer.drawWell(g,left+10,top+182,WIDTH-20,18);
        String status=!fresh?"waiting":live.mode()==0?"off":live.powered()?(live.mode()==1?"heating":"cooling"):"holding";
        int light=!fresh?Palette.LABEL_DIM:live.powered()?(live.mode()==1?0xFFC7623D:0xFF4A8995):Palette.LABEL_DIM;
        g.fill(left+16,top+188,left+22,top+194,light);
        g.drawString(font,text(status),left+29,top+187,Palette.LABEL_LIGHT,false);
        String state=pending?"saving":!live.editable()?"readonly":dirty()?"unsaved":"";
        if(!state.isEmpty())g.drawString(font,text(state),left+WIDTH-16-font.width(text(state)),top+187,Palette.LABEL_WARM,false);
        super.render(g,mouseX,mouseY,partial);
    }
    //? if >=1.21 {
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial) {}
    //?} else {
    /*@Override public void renderBackground(GuiGraphics g) {}
    *///?}
    @Override public boolean isPauseScreen() { return false; }
}
