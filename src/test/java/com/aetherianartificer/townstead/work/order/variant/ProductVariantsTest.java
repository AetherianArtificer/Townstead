package com.aetherianartificer.townstead.work.order.variant;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductVariantsTest {

    private static ResourceLocation rl(String s) {
        return ResourceLocation.tryParse(s);
    }

    private static final ResourceLocation METHOD = rl("test:method");
    private static final ResourceLocation STRENGTH = rl("test:strength");

    /** Herbs, keyed "test:herb/<method>/<strength>/<herb ns>/<herb path>", that vary by method and strength. */
    private static final class Herbs implements VariantFamily {
        final VariantAxis method = new VariantAxis(METHOD, "method", true, List.of(
                new VariantAxis.Value("dried", "dried", null), new VariantAxis.Value("charred", "charred", null)));
        final VariantAxis strength = new VariantAxis(STRENGTH, "strength", false, List.of(
                new VariantAxis.Value("mild", "mild", null), new VariantAxis.Value("strong", "strong", null)));

        @Override
        public ResourceLocation id() {
            return rl("test:herbs");
        }

        @Override
        public @Nullable Described describe(ResourceLocation product) {
            if (!"test".equals(product.getNamespace()) || !product.getPath().startsWith("herb/")) return null;
            String[] p = product.getPath().split("/", 5);
            Map<ResourceLocation, String> values = new LinkedHashMap<>();
            values.put(METHOD, p[1]);
            values.put(STRENGTH, p[2]);
            return new Described(id(), rl(p[3] + ":" + p[4]), values);
        }

        @Override
        public @Nullable ResourceLocation compose(ResourceLocation base, Map<ResourceLocation, String> values) {
            return rl("test:herb/" + values.get(METHOD) + "/" + values.get(STRENGTH) + "/" + base.getNamespace() + "/" + base.getPath());
        }

        @Override
        public List<VariantAxis> axes(ResourceLocation base) {
            return List.of(method, strength);
        }

        @Override
        public String label(ResourceLocation base) {
            return base.getPath();
        }

        @Override
        public @Nullable ResourceLocation icon(ResourceLocation base) {
            return base;
        }
    }

    @BeforeEach
    void setUp() {
        ProductVariants.clearForTest();
        ProductVariants.register(new Herbs());
    }

    @AfterEach
    void tearDown() {
        ProductVariants.clearForTest();
    }

    private static Map<ResourceLocation, String> values(String method, String strength) {
        Map<ResourceLocation, String> v = new LinkedHashMap<>();
        v.put(METHOD, method);
        v.put(STRENGTH, strength);
        return v;
    }

    @Test
    void everyAxisChosenIsTheExactProduct() {
        ResourceLocation product = ProductVariants.product(rl("test:herbs"), rl("minecraft:oxeye_daisy"), values("dried", "mild"));
        assertEquals(rl("test:herb/dried/mild/minecraft/oxeye_daisy"), product);
        assertFalse(ProductVariants.isPattern(product));
        VariantFamily.Described d = ProductVariants.describe(product);
        assertNotNull(d);
        assertEquals(rl("minecraft:oxeye_daisy"), d.base());
        assertEquals("dried", d.values().get(METHOD));
    }

    @Test
    void anOpenAxisIsAPatternThatReadsBack() {
        ResourceLocation pattern = ProductVariants.product(rl("test:herbs"), rl("minecraft:oxeye_daisy"), values(VariantAxis.ANY, "strong"));
        assertNotNull(pattern);
        assertTrue(ProductVariants.isPattern(pattern));
        ProductVariants.Pattern back = ProductVariants.parse(pattern);
        assertNotNull(back);
        assertEquals(rl("minecraft:oxeye_daisy"), back.base());
        assertEquals(VariantAxis.ANY, back.values().get(METHOD));
        assertEquals("strong", back.values().get(STRENGTH));
    }

    @Test
    void aBaseWithASlashInItsPathReadsBack() {
        ResourceLocation pattern = ProductVariants.product(rl("test:herbs"), rl("somemod:herbs/rare_moss"), values(VariantAxis.ANY, "mild"));
        ProductVariants.Pattern back = ProductVariants.parse(pattern);
        assertNotNull(back);
        assertEquals(rl("somemod:herbs/rare_moss"), back.base());
    }

    @Test
    void aPatternTakesInEveryValueOfItsOpenAxisAndNoOther() {
        ResourceLocation pattern = ProductVariants.product(rl("test:herbs"), rl("minecraft:oxeye_daisy"), values(VariantAxis.ANY, "strong"));
        assertTrue(ProductVariants.matches(pattern, rl("test:herb/dried/strong/minecraft/oxeye_daisy")));
        assertTrue(ProductVariants.matches(pattern, rl("test:herb/charred/strong/minecraft/oxeye_daisy")));
        assertFalse(ProductVariants.matches(pattern, rl("test:herb/dried/mild/minecraft/oxeye_daisy")), "a fixed axis is not open");
        assertFalse(ProductVariants.matches(pattern, rl("test:herb/dried/strong/minecraft/allium")), "another base");
        assertFalse(ProductVariants.matches(pattern, rl("minecraft:oxeye_daisy")), "something the family does not describe");
    }

    @Test
    void anAxisThatCannotBeLeftOpenMakesNoPattern() {
        assertNull(ProductVariants.product(rl("test:herbs"), rl("minecraft:oxeye_daisy"), values("dried", VariantAxis.ANY)));
    }

    @Test
    void anExactProductMatchesOnlyItself() {
        ResourceLocation product = rl("test:herb/dried/mild/minecraft/oxeye_daisy");
        assertTrue(ProductVariants.matches(product, product));
        assertFalse(ProductVariants.matches(product, rl("test:herb/charred/mild/minecraft/oxeye_daisy")));
    }

    @Test
    void aTemplateFamilyReadsAndSpellsItsKeys() {
        VariantAxis method = new VariantAxis(METHOD, "method", true, List.of(
                new VariantAxis.Value("dry", "dried", null), new VariantAxis.Value("char", "charred", null),
                new VariantAxis.Value("dry.char", "dried, then charred", null)));
        TemplateVariantFamily family = TemplateVariantFamily.of(rl("test:prepared"),
                "test_product:prepared/{test:method}/{base.namespace}/{base.path}", List.of(method));
        assertNotNull(family);
        ProductVariants.register(family);
        VariantFamily.Described d = ProductVariants.describe(rl("test_product:prepared/dry.char/minecraft/oxeye_daisy"));
        assertNotNull(d);
        assertEquals(rl("minecraft:oxeye_daisy"), d.base());
        assertEquals("dry.char", d.values().get(METHOD));
        assertEquals(rl("test_product:prepared/char/somemod/herbs/moss"),
                family.compose(rl("somemod:herbs/moss"), Map.of(METHOD, "char")));
        assertNull(ProductVariants.describe(rl("test_product:prepared/boil/minecraft/oxeye_daisy")), "not a method of the axis");
        ResourceLocation any = ProductVariants.product(rl("test:prepared"), rl("minecraft:oxeye_daisy"), Map.of(METHOD, VariantAxis.ANY));
        assertTrue(ProductVariants.matches(any, rl("test_product:prepared/char/minecraft/oxeye_daisy")));
        assertFalse(ProductVariants.matches(any, rl("test_product:prepared/char/minecraft/allium")));
    }

    @Test
    void aTemplateMissingAPlaceholderIsRefused() {
        VariantAxis method = new VariantAxis(METHOD, "method", true, List.of(new VariantAxis.Value("dry", "dried", null)));
        assertNull(TemplateVariantFamily.of(rl("test:prepared"), "test_product:prepared/{test:method}/{base.path}", List.of(method)));
        assertNull(TemplateVariantFamily.of(rl("test:prepared"), "test_product:{base.namespace}/{base.path}", List.of(method)));
    }

    @Test
    void aValueSpelledAsNothingStillReadsBack() {
        ResourceLocation strengthId = rl("test:potion_strength");
        VariantAxis strength = new VariantAxis(strengthId, "strength", true, List.of(
                new VariantAxis.Value("plain", "plain", null, "", false),
                new VariantAxis.Value("strong", "strong", null, "strong_", false),
                new VariantAxis.Value("long", "long", null, "long_", false)));
        TemplateVariantFamily family = TemplateVariantFamily.of(rl("test:potions"),
                "test_product:potion/{base.namespace}/{test:potion_strength}{base.path}", List.of(strength));
        assertNotNull(family);
        VariantFamily.Described strong = family.describe(rl("test_product:potion/minecraft/strong_healing"));
        assertNotNull(strong);
        assertEquals(rl("minecraft:healing"), strong.base());
        assertEquals("strong", strong.values().get(strengthId));
        VariantFamily.Described plain = family.describe(rl("test_product:potion/minecraft/healing"));
        assertNotNull(plain);
        assertEquals(rl("minecraft:healing"), plain.base());
        assertEquals("plain", plain.values().get(strengthId));
        assertEquals(rl("test_product:potion/minecraft/healing"), family.compose(rl("minecraft:healing"), Map.of(strengthId, "plain")));
        assertEquals(rl("test_product:potion/minecraft/long_healing"), family.compose(rl("minecraft:healing"), Map.of(strengthId, "long")));
    }
}
