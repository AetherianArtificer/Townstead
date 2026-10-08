package com.aetherianartificer.townstead.social;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.lang.PhenoDiagnostics;
import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostic;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import java.util.*;

public final class SocialInclinationJsonLoader extends SimpleJsonResourceReloadListener {
    public SocialInclinationJsonLoader(){super(new Gson(),"social_inclination");}
    @Override protected void apply(Map<ResourceLocation,JsonElement> entries,ResourceManager manager,ProfilerFiller profiler){
        Map<String,String> lang=DataPackLang.loadLangIndex(manager);Map<ResourceLocation,SocialInclinationDefinition> parsed=new LinkedHashMap<>();List<Diagnostic> diagnostics=new ArrayList<>();
        entries.forEach((id,element)->{try{parsed.put(id,SocialInclinationDefinition.parse(id,GsonHelper.convertToJsonObject(element,id.toString()),lang));}catch(RuntimeException ex){diagnostics.add(Diagnostic.error(id,"$",ex.getMessage()));Townstead.LOGGER.warn("Social inclination rejected: {} $.{}",id,ex.getMessage());}});
        SocialInclinations.replaceAll(parsed);PhenoDiagnostics.replace("social_inclination",diagnostics);Townstead.LOGGER.info("Loaded {} social inclinations ({} rejected)",parsed.size(),diagnostics.size());
    }
}
