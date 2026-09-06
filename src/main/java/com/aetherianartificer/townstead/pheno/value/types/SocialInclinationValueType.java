package com.aetherianartificer.townstead.pheno.value.types;

import com.aetherianartificer.townstead.chronicle.ChronicleSocialKnowledge;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.ValueType;
import com.aetherianartificer.townstead.social.SocialInclinations;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/** Reads an intrinsic personal value for Pheno conditions, evaluations, dialogue, and rituals. */
public final class SocialInclinationValueType implements ValueType {
    public static final String KEY="pheno:social_inclination";
    @Override public String key(){return KEY;}
    @Override public Value parse(JsonObject json){
        String raw=GsonHelper.getAsString(json,"inclination","");ResourceLocation id=ResourceLocation.tryParse(raw);
        SocialTarget target=SocialTarget.parse(GsonHelper.getAsString(json,"person","self"),false);
        if(id==null||target==null)return null;
        return Value.subjectAware(context->{
            if(target.name().equals("self")){
                var knowledge=ChronicleSocialKnowledge.of(context);
                return knowledge==null?Double.NaN:knowledge.socialInclination(raw);
            }
            if(target.name().equals("other")&&context.other() instanceof VillagerEntityMCA villager){
                var definition=SocialInclinations.all().get(id);return definition==null?Double.NaN:SocialInclinations.score(villager,definition);
            }
            return Double.NaN;
        });
    }
}
