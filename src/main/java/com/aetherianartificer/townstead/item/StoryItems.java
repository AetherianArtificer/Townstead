package com.aetherianartificer.townstead.item;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.contract.ContractText;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.ModGate;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Items a data pack defines on top of an existing item, from {@code data/<ns>/story_item/<id>.json}
 * (schema {@code townstead:story_item/v1}): a base item, a name, description lines, an optional
 * {@code custom_model_data} for a resource pack's own model, an optional glint, and, on a written
 * book, a title, author and pages the player can read. Text is a text value (a string,
 * {@code { "text" }} or {@code { "translate", "with" }}), resolved in the player's language when
 * the item is made. The stack remembers which story item it is, so stories can recognize it.
 * <pre>
 * { "schema": "townstead:story_item/v1", "base": "minecraft:gold_nugget",
 *   "name": { "translate": "story_item.townstead.court_signet" },
 *   "lore": [ { "translate": "story_item.townstead.court_signet.lore" } ],
 *   "custom_model_data": 7301 }
 * </pre>
 */
public final class StoryItems {
    public static final String SCHEMA = "townstead:story_item/v1";
    private static final String ID_TAG = "townstead_story_item";

    public record Definition(ResourceLocation id, ResourceLocation base, ContractText name, List<ContractText> lore,
                             @Nullable Integer modelData, boolean glint, @Nullable ContractText bookTitle,
                             String bookAuthor, List<ContractText> pages) {}

    private static volatile Map<ResourceLocation, Definition> loaded = Map.of();
    private static volatile Map<ResourceLocation, String> rejected = Map.of();

    private StoryItems() {}

    public static @Nullable Definition get(ResourceLocation id) { return loaded.get(id); }

    public static Map<ResourceLocation, String> rejected() { return rejected; }

    /** A stack of the story item {@code id}, in {@code player}'s language. Empty when it is not defined. */
    public static ItemStack make(ResourceLocation id, int count, ServerPlayer player) {
        Definition def = loaded.get(id);
        if (def == null) return ItemStack.EMPTY;
        Item base = BuiltInRegistries.ITEM.get(def.base());
        ItemStack stack = new ItemStack(base, Math.max(1, count));
        String locale = locale(player);
        Component name = Component.literal(def.name().resolve(Map.of(), locale)).withStyle(style -> style.withItalic(false));
        List<Component> lore = new ArrayList<>();
        for (ContractText line : def.lore()) {
            lore.add(Component.literal(line.resolve(Map.of(), locale)).withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY)));
        }
        //? if >=1.21 {
        stack.set(net.minecraft.core.component.DataComponents.ITEM_NAME, name);
        if (!lore.isEmpty()) stack.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));
        if (def.modelData() != null) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                    new net.minecraft.world.item.component.CustomModelData(def.modelData()));
        }
        if (def.glint()) stack.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack,
                tag -> tag.putString(ID_TAG, id.toString()));
        if (def.bookTitle() != null) {
            List<net.minecraft.server.network.Filterable<Component>> pages = new ArrayList<>();
            for (ContractText page : def.pages()) {
                pages.add(net.minecraft.server.network.Filterable.passThrough(Component.literal(page.resolve(Map.of(), locale))));
            }
            stack.set(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT,
                    new net.minecraft.world.item.component.WrittenBookContent(
                            net.minecraft.server.network.Filterable.passThrough(def.bookTitle().resolve(Map.of(), locale)),
                            def.bookAuthor(), 0, pages, true));
        }
        //?} else {
        /*net.minecraft.nbt.CompoundTag tag = stack.getOrCreateTag();
        tag.putString(ID_TAG, id.toString());
        net.minecraft.nbt.CompoundTag display = stack.getOrCreateTagElement("display");
        display.putString("Name", Component.Serializer.toJson(name));
        if (!lore.isEmpty()) {
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (Component line : lore) list.add(net.minecraft.nbt.StringTag.valueOf(Component.Serializer.toJson(line)));
            display.put("Lore", list);
        }
        if (def.modelData() != null) tag.putInt("CustomModelData", def.modelData());
        if (def.bookTitle() != null) {
            tag.putString("title", def.bookTitle().resolve(Map.of(), locale));
            tag.putString("author", def.bookAuthor());
            tag.putBoolean("resolved", true);
            net.minecraft.nbt.ListTag pages = new net.minecraft.nbt.ListTag();
            for (ContractText page : def.pages()) {
                pages.add(net.minecraft.nbt.StringTag.valueOf(Component.Serializer.toJson(Component.literal(page.resolve(Map.of(), locale)))));
            }
            tag.put("pages", pages);
        }
        *///?}
        return stack;
    }

    /** Which story item {@code stack} is, or null. */
    public static @Nullable ResourceLocation idOf(ItemStack stack) {
        if (stack.isEmpty()) return null;
        //? if >=1.21 {
        net.minecraft.world.item.component.CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        String raw = data.copyTag().getString(ID_TAG);
        //?} else {
        /*if (!stack.hasTag()) return null;
        String raw = stack.getTag().getString(ID_TAG);
        *///?}
        return raw.isEmpty() ? null : ResourceLocation.tryParse(raw);
    }

    public static boolean is(ItemStack stack, ResourceLocation id) {
        return id.equals(idOf(stack));
    }

    private static String locale(ServerPlayer player) {
        //? if >=1.21 {
        return player.clientInformation().language();
        //?} else {
        /*return player.getLanguage();
        *///?}
    }

    public static Definition parse(ResourceLocation id, JsonObject json) {
        TownsteadSchema.validateRequired(json, SCHEMA);
        ResourceLocation base = DataPackLang.parseId(GsonHelper.getAsString(json, "base"));
        if (base == null) throw new IllegalArgumentException("base: not a valid item id");
        if (!json.has("name")) throw new IllegalArgumentException("name: required");
        List<ContractText> lore = new ArrayList<>();
        if (json.has("lore")) for (JsonElement line : GsonHelper.getAsJsonArray(json, "lore")) lore.add(ContractText.parse(line));
        ContractText title = null;
        String author = "";
        List<ContractText> pages = new ArrayList<>();
        if (json.has("book")) {
            if (!base.toString().equals("minecraft:written_book")) throw new IllegalArgumentException("book: the base must be minecraft:written_book");
            JsonObject book = GsonHelper.getAsJsonObject(json, "book");
            title = ContractText.parse(book.get("title"));
            author = GsonHelper.getAsString(book, "author", "");
            for (JsonElement page : GsonHelper.getAsJsonArray(book, "pages")) pages.add(ContractText.parse(page));
        }
        return new Definition(id, base, ContractText.parse(json.get("name")), List.copyOf(lore),
                json.has("custom_model_data") ? GsonHelper.getAsInt(json, "custom_model_data") : null,
                GsonHelper.getAsBoolean(json, "glint", false), title, author, List.copyOf(pages));
    }

    public static final class Loader extends SimpleJsonResourceReloadListener {
        public Loader() { super(new Gson(), "story_item"); }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, Definition> parsed = new HashMap<>();
            Map<ResourceLocation, String> errors = new HashMap<>();
            for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
                try {
                    JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey().toString());
                    if (!ModGate.allows(json)) continue;
                    Definition def = parse(entry.getKey(), json);
                    if (!BuiltInRegistries.ITEM.containsKey(def.base())) throw new IllegalArgumentException("base: no item '" + def.base() + "'");
                    parsed.put(entry.getKey(), def);
                } catch (Exception exception) {
                    errors.put(entry.getKey(), exception.getMessage());
                    Townstead.LOGGER.warn("Story item {} rejected: {}", entry.getKey(), exception.getMessage());
                }
            }
            loaded = Map.copyOf(parsed);
            rejected = Map.copyOf(errors);
            Townstead.LOGGER.info("Loaded {} story items ({} rejected)", parsed.size(), errors.size());
        }
    }
}
