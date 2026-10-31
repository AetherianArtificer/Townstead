package com.aetherianartificer.townstead.social;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** A stable personal inclination. Culture can later add an overlay without changing this contract. */
public record SocialInclinationDefinition(ResourceLocation id, String displayLangKey, String displayLiteral,
                                      float base, float variation, Map<String, Float> personalityModifiers) {
    public static final String SCHEMA="townstead:social_inclination/v1";
    public SocialInclinationDefinition {
        if(!Float.isFinite(base)||base<0||base>100) throw new IllegalArgumentException("base must be 0..100");
        if(!Float.isFinite(variation)||variation<0||variation>100) throw new IllegalArgumentException("variation must be 0..100");
        personalityModifiers=Map.copyOf(personalityModifiers);
    }
    public static SocialInclinationDefinition parse(ResourceLocation id, JsonObject json, Map<String,String> lang) {
        only(json,"schema","display","base","variation","personality_modifiers"); TownsteadSchema.validateRequired(json,SCHEMA);
        String key="social_inclination."+id.getNamespace()+"."+id.getPath().replace('/','.');
        String literal=id.getPath().replace('_',' ');
        if(json.has("display")){ JsonObject display=json.getAsJsonObject("display"); only(display,"translate","text");
            if(display.has("translate")) key=display.get("translate").getAsString(); else literal=display.get("text").getAsString(); }
        literal=lang.getOrDefault(key,DataPackLang.resolveFallback(key,"en_us",literal));
        Map<String,Float> modifiers=new LinkedHashMap<>();
        if(json.has("personality_modifiers")) for(var entry:json.getAsJsonObject("personality_modifiers").entrySet()) {
            float amount=entry.getValue().getAsFloat(); if(!Float.isFinite(amount)||amount < -100||amount>100)
                throw new IllegalArgumentException("personality_modifiers."+entry.getKey()+": must be -100..100");
            modifiers.put(normalize(entry.getKey()),amount);
        }
        return new SocialInclinationDefinition(id,key,literal,GsonHelper.getAsFloat(json,"base",50),
                GsonHelper.getAsFloat(json,"variation",15),modifiers);
    }
    static String normalize(String value){ String out=value.trim().toLowerCase(java.util.Locale.ROOT); return out.indexOf(':')<0?"mca:"+out:out; }
    private static void only(JsonObject json,String...fields){Set<String> allowed=Set.of(fields);for(String field:json.keySet())if(!allowed.contains(field))throw new IllegalArgumentException(field+": unknown field");}
}
