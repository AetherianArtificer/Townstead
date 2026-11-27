package com.aetherianartificer.townstead.skillcheck;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Forge needs a mod class. Written by tools/skills/port_forge.py. */
@Mod("townstead_skill_check")
public final class SkillCheckMod {
    public SkillCheckMod() {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        MinecraftForge.EVENT_BUS.addListener(SkillScenes::onTick);
        if (Boolean.getBoolean("townstead.scene.tests")) {
            SkillScenes.testRun = ForgeTestRun::start;
            MinecraftForge.EVENT_BUS.addListener(ForgeTestRun::tick);
            SkillScenes.testsDone = () -> ForgeTestRun.done(net.minecraft.client.Minecraft.getInstance());
        }
    }
}
