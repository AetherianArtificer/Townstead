package com.aetherianartificer.townstead.root.gene;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.*;
import com.aetherianartificer.townstead.pheno.condition.types.DimensionConditionType;
import com.aetherianartificer.townstead.pheno.condition.types.BiomeConditionType;
import com.aetherianartificer.townstead.root.gene.types.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.aetherianartificer.townstead.TestBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class GeneExpressionTest {
    @BeforeAll static void bootstrap() { TestBootstrap.ensure(); }

    private static final ResourceLocation NORMAL = id("test:eyes");
    private static final ResourceLocation NATIVE = id("test:nether_native");
    private static final ResourceLocation ZOMBIE = id("test:nether_native/eyes");
    private static final ResourceLocation STERILE = id("test:nether_native/sterile");
    private static final ResourceLocation LOCUS = id("test:eye_style");
    private static ResourceLocation id(String text) { return DataPackLang.parseId(text); }

    @AfterEach void clear() {
        GeneExpression.replaceAll(Map.of());
        GeneRegistry.replaceAll(Map.of(), Map.of());
    }

    private static Gene gene(ResourceLocation id, ResourceLocation locus) {
        var instance = new CannibalGeneType.Instance();
        return new Gene(id, Component.literal("test"), null, null, "test", Dominance.DOMINANT,
                locus, 1, List.of(new GeneVariant("round", Component.literal("round"), 1, instance),
                new GeneVariant("tall", Component.literal("tall"), 1, instance)));
    }

    private static void registry() {
        GeneRegistry.replaceAll(Map.of(NORMAL, gene(NORMAL, LOCUS), NATIVE, gene(NATIVE, null),
                ZOMBIE, gene(ZOMBIE, LOCUS), STERILE, gene(STERILE, null)),
                Map.of(NATIVE, List.of(ZOMBIE, STERILE)));
    }

    @Test void parentConditionSwitchesTheBundleAndPreservesDormantEyeStyle() {
        registry();
        AtomicBoolean overworld = new AtomicBoolean(false);
        GeneExpression.replaceAll(Map.of(NORMAL, ctx -> !overworld.get(), NATIVE, ctx -> overworld.get()));
        Allele eyes = Allele.of(NORMAL, "tall|length=1.250");
        List<Allele> inherited = List.of(eyes, Allele.of(NATIVE, null));
        assertEquals(List.of(eyes), GeneExpression.activeAlleles(inherited, null));
        overworld.set(true);
        List<Allele> zombie = GeneExpression.activeAlleles(inherited, null);
        assertFalse(zombie.contains(eyes));
        assertTrue(zombie.contains(Allele.of(ZOMBIE, eyes.variantId())));
        assertTrue(zombie.contains(Allele.of(STERILE, null)));
        overworld.set(false);
        assertEquals(List.of(eyes), GeneExpression.activeAlleles(inherited, null));
        assertEquals(NORMAL, eyes.geneId());
    }

    @Test void childCanAddAConditionButCannotBypassItsParent() {
        registry();
        GeneExpression.replaceAll(Map.of(NATIVE, ctx -> false, STERILE, Conditions.ALWAYS));
        assertTrue(GeneExpression.activeAlleles(List.of(Allele.of(NATIVE, null)), null).isEmpty());
        GeneExpression.replaceAll(Map.of(NATIVE, Conditions.ALWAYS, STERILE, ctx -> false));
        assertFalse(GeneExpression.activeAlleles(List.of(Allele.of(NATIVE, null)), null)
                .contains(Allele.of(STERILE, null)));
    }

    @Test void editingCompanionStyleTargetsTheDormantInheritedGene() {
        registry();
        GeneExpression.replaceAll(Map.of(NORMAL, ctx -> false));
        List<Allele> inherited = List.of(Allele.of(NORMAL, "round"), Allele.of(NATIVE, null));
        assertEquals(NORMAL, GeneExpression.variantEditSource(GeneRegistry.byId(ZOMBIE), inherited, "tall").id());
        assertNull(GeneExpression.variantEditSource(GeneRegistry.byId(ZOMBIE), inherited, "missing"));
        assertNull(GeneExpression.variantEditSource(GeneRegistry.byId(ZOMBIE), List.of(), "tall"));
        assertEquals(NORMAL, GeneExpression.variantEditSource(GeneRegistry.byId(NORMAL), inherited, "tall").id());
    }

    @Test void companionWithoutMatchingStyleUsesItsDefaultAndReloadClearsGates() {
        registry();
        assertEquals(Allele.of(ZOMBIE, null), GeneExpression.companionAllele(ZOMBIE,
                List.of(Allele.of(NORMAL, "missing"))));
        Allele eyes = Allele.of(NORMAL, "round");
        GeneExpression.replaceAll(Map.of(NORMAL, ctx -> false));
        assertTrue(GeneExpression.resolve(eyes, null).isWild());
        GeneExpression.replaceAll(Map.of());
        assertEquals(eyes, GeneExpression.resolve(eyes, null));
    }

    @Test void standardConditionParsesExistingDimensionsAndBiomes() {
        ConditionTypes.register(new DimensionConditionType());
        ConditionTypes.register(new BiomeConditionType());
        assertNotNull(GeneExpression.parse(json("""
                {"condition":{"type":"pheno:biome","biome_tag":"minecraft:is_forest"}}
                """), new EyesGeneType()));
        assertNotNull(GeneExpression.parse(json("""
                {"condition":{"type":"pheno:dimension","dimension":"minecraft:overworld","inverted":true}}
                """), new CosmeticFeatureGeneType()));
        assertThrows(IllegalArgumentException.class, () -> GeneExpression.parse(json("""
                {"condition":{"type":"test:unknown"}}
                """), new AttachmentGeneType()));
    }

    @Test void companionLoaderPreservesVariantsAndDeclaredLocus() {
        GeneTypes.register(new EyesGeneType());
        Map<ResourceLocation, Gene> genes = new java.util.LinkedHashMap<>();
        Map<ResourceLocation, List<ResourceLocation>> companions = new java.util.LinkedHashMap<>();
        Map<ResourceLocation, Condition> gates = new java.util.LinkedHashMap<>();
        GeneJsonLoader.registerCompanions(NATIVE, Map.of(ZOMBIE, json("""
                {"type":"townstead_roots:eyes","category":"Eyes","locus":"test:eye_style","variants":{
                  "round":{"texture":"test:round.png","weight":3,"visible_half":"right"},
                  "tall":{"texture":"test:tall.png","weight":2,"visible_half":"right"}
                }}
                """)), Map.of(), genes, companions, gates);
        assertEquals(List.of(ZOMBIE), companions.get(NATIVE));
        assertEquals(LOCUS, genes.get(ZOMBIE).locus());
        assertEquals("Eyes", genes.get(ZOMBIE).category());
        assertEquals(List.of("round", "tall"), genes.get(ZOMBIE).variants().stream().map(GeneVariant::id).toList());
        assertEquals("right", ((EyesGeneType.Instance) genes.get(ZOMBIE).instance()).visibleHalf());
    }

    @Test void existingBehaviorConditionsRemainOwnedByTheirBehavior() {
        assertSame(Conditions.ALWAYS, GeneExpression.parse(json("""
                {"condition":{"type":"test:event_context_only"}}
                """), new AttributeGeneType()));
        assertTrue(new FertilityGeneType().conditionControlsExpression());
        assertTrue(new SkinOverlayGeneType().conditionControlsExpression());
    }

    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
}
