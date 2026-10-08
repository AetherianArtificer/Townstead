package com.aetherianartificer.townstead.social;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SocialFoundationContentTest {
    @Test void bundledValuesAndInitialImpressionsResolve() throws Exception {
        Path resources=Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("data/townstead")).toURI()).getParent().getParent();
        Set<ResourceLocation> values=new HashSet<>(),qualities=new HashSet<>();
        try(var files=Files.list(resources.resolve("data/townstead/social_inclination"))){for(Path file:files.filter(p->p.toString().endsWith(".json")).toList()){
            ResourceLocation id=ResourceLocation.tryParse("townstead:"+file.getFileName().toString().replace(".json",""));
            SocialInclinationDefinition.parse(id,JsonParser.parseString(Files.readString(file)).getAsJsonObject(),Map.of());values.add(id);
        }}
        try(var files=Files.list(resources.resolve("data/townstead/relationship_quality"))){for(Path file:files.filter(p->p.toString().endsWith(".json")).toList())qualities.add(ResourceLocation.tryParse("townstead:"+file.getFileName().toString().replace(".json","")));}
        int rules=0;try(var files=Files.list(resources.resolve("data/townstead/initial_impression"))){for(Path file:files.filter(p->p.toString().endsWith(".json")).toList()){
            var json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();assertEquals(InitialImpressions.SCHEMA,json.get("schema").getAsString());
            assertTrue(values.contains(ResourceLocation.tryParse(json.get("inclination").getAsString())));assertTrue(qualities.contains(ResourceLocation.tryParse(json.get("quality").getAsString())));rules++;
        }}
        assertTrue(values.size()>=4);assertTrue(rules>=8);
    }

    @Test void bundledSocialMemoriesParse() throws Exception {
        Path resources=Path.of(Objects.requireNonNull(getClass().getClassLoader()
                .getResource("data/townstead_social/social_memory")).toURI());
        int count=0;
        try(var files=Files.list(resources)){
            for(Path file:files.filter(p->p.toString().endsWith(".json")).toList()){
                String name=file.getFileName().toString().replace(".json","");
                var json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                assertDoesNotThrow(()->SocialMemoryDefinition.parse(
                        ResourceLocation.tryParse("townstead_social:"+name),json,Map.of()),name);
                count++;
            }
        }
        assertTrue(count>=17);
    }
}
