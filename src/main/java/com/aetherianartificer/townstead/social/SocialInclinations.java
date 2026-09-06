package com.aetherianartificer.townstead.social;

import com.aetherianartificer.townstead.compat.mca.McaPersonalityCompat;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.UUID;

/** Deterministic intrinsic profiles; later belief/culture layers can decorate this read boundary. */
public final class SocialInclinations {
    private static volatile Map<ResourceLocation,SocialInclinationDefinition> entries=Map.of();
    private SocialInclinations(){}
    public static void replaceAll(Map<ResourceLocation,SocialInclinationDefinition> next){entries=Map.copyOf(next);}
    public static Map<ResourceLocation,SocialInclinationDefinition> all(){return entries;}
    public static double score(UUID person,String personality,SocialInclinationDefinition definition){
        long mixed=person.getMostSignificantBits()^Long.rotateLeft(person.getLeastSignificantBits(),17)^definition.id().hashCode();
        double unit=Math.floorMod(mixed,10001)/10000D;
        double modifier=definition.personalityModifiers().getOrDefault(SocialInclinationDefinition.normalize(personality),0F);
        return Math.max(0,Math.min(100,definition.base()+(unit*2-1)*definition.variation()+modifier));
    }
    public static double score(VillagerEntityMCA person,SocialInclinationDefinition definition){
        String exact=personality(person),normalized=SocialInclinationDefinition.normalize(exact);
        if(definition.personalityModifiers().containsKey(normalized))return score(person.getUUID(),exact,definition);
        return score(person.getUUID(),McaPersonalityCompat.id(person.getVillagerBrain().getPersonality()),definition);
    }
    public static String personality(VillagerEntityMCA person){
        String exact=TownsteadVillagers.get(person).life().personalityId();
        return exact==null||exact.isBlank()?McaPersonalityCompat.id(person.getVillagerBrain().getPersonality()):exact;
    }
}
