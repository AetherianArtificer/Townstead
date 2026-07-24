package com.aetherianartificer.townstead.compat.jei;

import com.aetherianartificer.townstead.compat.ModCompat;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

/**
 * JEI integration. Today this exists to make Just Enough Professions tell the truth about
 * careers: without it, JEP documents the incidental acquisition surfaces (chefsdelight's chef
 * at the pot, its cook at the skillet) as separate professions while the canonical POI-less
 * career shows nothing. When JEP is installed, one consolidated entry per hierarchy-bearing
 * career is added to JEP's own category and the absorbed flavor entries are hidden. This class
 * is only ever loaded by JEI's plugin scan, so it is safe when JEI is absent; all JEP class
 * access lives behind reflection in {@link JepConsolidation}.
 */
@JeiPlugin
public class TownsteadJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = ResourceLocation.tryParse("townstead:jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (!ModCompat.isLoaded("justenoughprofessions")) return;
        JepConsolidation.addConsolidatedCareers(registration);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        if (!ModCompat.isLoaded("justenoughprofessions")) return;
        JepConsolidation.hideAbsorbedFlavors(jeiRuntime);
    }
}
