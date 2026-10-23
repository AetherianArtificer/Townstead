package com.aetherianartificer.townstead.mixin.compat.mca;

import com.aetherianartificer.townstead.naming.NameLists;
import com.google.gson.JsonElement;
import net.conczin.mca.resources.Names;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Reinstalls Townstead's name lists after MCA rebuilds its name map on a datapack reload.
 *
 * <p>MCA's reload clears the map and then rebuilds its region list from whatever is left, so
 * installing at the tail of that puts Townstead's lists back where names can be picked from
 * them while keeping them out of the region list, which is what makes them safe to add. Townstead's
 * own loader installs as well, so the lists survive either reload order.</p>
 */
@Mixin(Names.class)
public abstract class NamesRegisterInstallMixin {

    @Inject(method = "apply", at = @At("TAIL"), remap = false)
    private void townstead$installNamingRegisters(Map<ResourceLocation, JsonElement> prepared,
                                                  ResourceManager manager, ProfilerFiller profiler,
                                                  CallbackInfo ci) {
        NameLists.install();
    }
}
