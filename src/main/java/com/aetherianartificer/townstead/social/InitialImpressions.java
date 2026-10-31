package com.aetherianartificer.townstead.social;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.chronicle.store.ChronicleSavedData;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.aetherianartificer.townstead.pheno.lang.PhenoDiagnostics;
import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostic;
import com.google.gson.*;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import java.util.*;

/** Data-authored first-read rules. Applying them repeatedly is safe and never rewrites an established impression. */
public final class InitialImpressions {
    public static final String SCHEMA="townstead:initial_impression/v1";
    public enum Mode { SIMILAR, DIFFERENT, TARGET_AT_LEAST, TARGET_AT_MOST }
    public record Rule(ResourceLocation id,ResourceLocation inclination,String quality,Mode mode,float threshold,float amount,int halfLifeDays){}
    private static volatile List<Rule> rules=List.of();
    private InitialImpressions(){}
    public static List<Rule> all(){return rules;}
    public static void apply(ChronicleSavedData data,VillagerEntityMCA observer,VillagerEntityMCA target,long today){
        for(Rule rule:rules){SocialInclinationDefinition definition=SocialInclinations.all().get(rule.inclination());if(definition==null)continue;
            double own=SocialInclinations.score(observer,definition),seen=SocialInclinations.score(target,definition),difference=Math.abs(own-seen);
            boolean match=switch(rule.mode()){case SIMILAR->difference<=rule.threshold();case DIFFERENT->difference>=rule.threshold();case TARGET_AT_LEAST->seen>=rule.threshold();case TARGET_AT_MOST->seen<=rule.threshold();};
            if(match)data.applyRelationship(observer.getUUID(),target.getUUID(),new RelationshipLedger.Contribution(
                    "initial_impression:"+rule.id()+":"+observer.getUUID()+":"+target.getUUID(),rule.quality(),rule.amount(),today,
                    rule.halfLifeDays()<0?RelationshipQualities.byId(rule.quality()).defaultHalfLifeDays():rule.halfLifeDays(),rule.id().toString()));
        }
    }
    public static final class Loader extends SimpleJsonResourceReloadListener{
        public Loader(){super(new Gson(),"initial_impression");}
        @Override protected void apply(Map<ResourceLocation,JsonElement> entries,ResourceManager manager,ProfilerFiller profiler){List<Rule> parsed=new ArrayList<>();List<Diagnostic> diagnostics=new ArrayList<>();
            entries.forEach((id,element)->{try{JsonObject json=element.getAsJsonObject();only(json,"schema","inclination","quality","mode","threshold","amount","half_life_days");TownsteadSchema.validateRequired(json,SCHEMA);
                ResourceLocation inclination=ResourceLocation.tryParse(GsonHelper.getAsString(json,"inclination"));String quality=GsonHelper.getAsString(json,"quality");if(inclination==null||ResourceLocation.tryParse(quality)==null)throw new IllegalArgumentException("inclination and quality must be resource ids");
                Mode mode=Mode.valueOf(GsonHelper.getAsString(json,"mode").toUpperCase(Locale.ROOT));float threshold=GsonHelper.getAsFloat(json,"threshold");float amount=GsonHelper.getAsFloat(json,"amount");int halfLife=GsonHelper.getAsInt(json,"half_life_days",-1);
                if(!Float.isFinite(threshold)||threshold<0||threshold>100)throw new IllegalArgumentException("threshold must be 0..100");if(!Float.isFinite(amount)||amount==0||amount< -100||amount>100)throw new IllegalArgumentException("amount must be nonzero and -100..100");if(halfLife< -1)throw new IllegalArgumentException("half_life_days must be >= 0");
                parsed.add(new Rule(id,inclination,quality,mode,threshold,amount,halfLife));}catch(RuntimeException ex){diagnostics.add(Diagnostic.error(id,"$",ex.getMessage()));Townstead.LOGGER.warn("Initial impression rejected: {} $.{}",id,ex.getMessage());}});
            rules=List.copyOf(parsed);PhenoDiagnostics.replace("initial_impression",diagnostics);Townstead.LOGGER.info("Loaded {} initial impression rules ({} rejected)",rules.size(),diagnostics.size());}
        private static void only(JsonObject json,String...fields){Set<String> allowed=Set.of(fields);for(String field:json.keySet())if(!allowed.contains(field))throw new IllegalArgumentException(field+": unknown field");}
    }
}
