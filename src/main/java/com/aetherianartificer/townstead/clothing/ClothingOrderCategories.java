package com.aetherianartificer.townstead.clothing;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.work.order.OrderTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * The Order Sheet categories the clothing engine derives from its entries, so "keep one warm
 * garment per villager" names a category no pack author had to list. Every category is
 * recomputed from the loaded entries on each question and cached per reload generation.
 */
public final class ClothingOrderCategories {

    public static final ResourceLocation CLOTHING = category("clothing");
    public static final ResourceLocation WARM_CLOTHING = category("warm_clothing");
    public static final ResourceLocation COOL_CLOTHING = category("cool_clothing");
    public static final ResourceLocation HEADWEAR = category("headwear");
    public static final ResourceLocation ACCESSORIES = category("accessories");

    private static volatile List<ClothingEntry> cachedFor = List.of();
    private static volatile List<ResourceLocation> clothing = List.of();
    private static volatile List<ResourceLocation> warm = List.of();
    private static volatile List<ResourceLocation> cool = List.of();
    private static volatile List<ResourceLocation> headwear = List.of();
    private static volatile List<ResourceLocation> accessories = List.of();

    private ClothingOrderCategories() {}

    public static void bootstrap() {
        OrderTags.registerDerived(CLOTHING, () -> refresh().clothing);
        OrderTags.registerDerived(WARM_CLOTHING, () -> refresh().warm);
        OrderTags.registerDerived(COOL_CLOTHING, () -> refresh().cool);
        OrderTags.registerDerived(HEADWEAR, () -> refresh().headwear);
        OrderTags.registerDerived(ACCESSORIES, () -> refresh().accessories);
    }

    private static ResourceLocation category(String name) {
        return DataPackLang.parseId("townstead:" + OrderTags.CATEGORY_PREFIX + name);
    }

    /** One pass over the entries per reload; the entry list is replaced wholesale on reload. */
    private static Holder refresh() {
        List<ClothingEntry> entries = ClothingDefs.entries();
        if (entries != cachedFor) {
            clothing = items(entries, entry -> true);
            warm = items(entries, ClothingEntry::isWarm);
            cool = items(entries, ClothingEntry::isCool);
            headwear = items(entries, entry -> entry.slot() == ClothingChannel.HEAD);
            accessories = items(entries, entry -> entry.layer() == ClothingLayer.ACCESSORY);
            cachedFor = entries;
        }
        return new Holder(clothing, warm, cool, headwear, accessories);
    }

    private record Holder(List<ResourceLocation> clothing, List<ResourceLocation> warm,
                          List<ResourceLocation> cool, List<ResourceLocation> headwear,
                          List<ResourceLocation> accessories) {}

    /** Item ids of every stack entry the test admits, tag entries expanded through the registry. */
    static List<ResourceLocation> items(List<ClothingEntry> entries, Predicate<ClothingEntry> test) {
        LinkedHashSet<ResourceLocation> out = new LinkedHashSet<>();
        for (ClothingEntry entry : entries) {
            if (entry.isSkin() || !test.test(entry)) continue;
            if (entry.item() != null) {
                out.add(entry.item());
            } else if (entry.tag() != null) {
                TagKey<Item> tag = TagKey.create(Registries.ITEM, entry.tag());
                for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                    out.add(BuiltInRegistries.ITEM.getKey(holder.value()));
                }
            }
        }
        return new ArrayList<>(out);
    }
}
