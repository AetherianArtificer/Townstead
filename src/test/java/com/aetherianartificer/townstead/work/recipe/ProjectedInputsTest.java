package com.aetherianartificer.townstead.work.recipe;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ProjectedInputsTest {
    private RecipeProjections.View view(boolean succeeded, Object inputs) {
        return new RecipeProjections.View(succeeded, "beverage.purification", Map.of("inputs", inputs),
                Map.of(), Map.of(), null, List.of());
    }
    @Test void acceptsScalarInputWithoutVanillaIngredientList() {
        var id = ResourceLocation.parse("test:dirty_water");
        var result = ProjectedStationPlan.declaredInputs(view(true, id));
        assertEquals(1, result.size());
        assertEquals(List.of(id), result.getFirst().itemIds());
        assertEquals(1, result.getFirst().count());
    }
    @Test void unresolvedInputRejectsWholeRecipeRatherThanMakingItFree() {
        assertTrue(ProjectedStationPlan.declaredInputs(view(true,
                List.of(ResourceLocation.parse("test:water"), new Object()))).isEmpty());
        assertTrue(ProjectedStationPlan.declaredInputs(view(false, ResourceLocation.parse("test:water"))).isEmpty());
    }
}
