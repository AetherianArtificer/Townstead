package com.aetherianartificer.townstead.profession;

import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.data.skin.Clothing;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfessionClothingSelectionTest {

    @Test
    void orderedSourceDoesNotTreatCivilianClothingAsProfessionWorkwear() {
        Clothing civilian = clothing("test:civilian", null, Gender.NEUTRAL);
        Clothing beekeeper = clothing("test:beekeeper", "example:beekeeper", Gender.NEUTRAL);
        Clothing dotted = clothing("test:beekeeper_dotted", "example.beekeeper", Gender.NEUTRAL);
        Clothing farmer = clothing("test:farmer", "minecraft:farmer", Gender.NEUTRAL);

        List<String> ids = ProfessionClothing.compatible(
                        List.of(civilian, farmer, dotted, beekeeper),
                        Gender.FEMALE,
                        ResourceLocation.tryParse("example:beekeeper"))
                .stream()
                .map(Clothing::getIdentifier)
                .toList();

        assertEquals(List.of("test:beekeeper", "test:beekeeper_dotted"), ids);
    }

    @Test
    void explicitWorkwearStillRespectsGender() {
        Clothing male = clothing("test:male", "example:beekeeper", Gender.MALE);
        Clothing female = clothing("test:female", "example:beekeeper", Gender.FEMALE);
        Clothing neutral = clothing("test:neutral", "example:beekeeper", Gender.NEUTRAL);

        List<String> ids = ProfessionClothing.compatible(
                        List.of(neutral, male, female),
                        Gender.FEMALE,
                        ResourceLocation.tryParse("example:beekeeper"))
                .stream()
                .map(Clothing::getIdentifier)
                .toList();

        assertEquals(List.of("test:female", "test:neutral"), ids);
    }

    @Test
    void missingProviderTextureFallsThroughToNextIdentity() {
        Clothing absentProvider = clothing(
                "optional:skins/clothing/normal/neutral/beekeeper.png",
                "forestry:beekeeper", Gender.NEUTRAL);
        Clothing farmer = clothing(
                "mca:skins/clothing/normal/female/farmer/0.png",
                "minecraft:farmer", Gender.FEMALE);

        Optional<String> selected = ProfessionClothing.firstAvailable(
                List.of(absentProvider, farmer), Gender.FEMALE,
                List.of(ResourceLocation.tryParse("forestry:beekeeper"),
                        ResourceLocation.tryParse("minecraft:farmer")),
                "optional:skins/clothing/normal/neutral/beekeeper.png",
                id -> id.startsWith("mca:"));

        assertEquals(Optional.of("mca:skins/clothing/normal/female/farmer/0.png"), selected);
    }

    private static Clothing clothing(String id, String profession, Gender gender) {
        return new Clothing(id, profession, 0, false, gender);
    }
}
