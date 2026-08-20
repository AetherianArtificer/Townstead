package com.aetherianartificer.townstead.compat.jei;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.profession.def.JobSiteProvider;
import com.aetherianartificer.townstead.profession.def.PoiHierarchy;
import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.profession.def.WorkTaskDef;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the consolidated career entries for Just Enough Professions and hides the absorbed
 * flavor entries. JEP has no plugin API, so its {@code PROFESSION_TYPE} recipe type and the
 * {@code ProfessionEntry}/{@code ProfessionWrapper} records are reached by reflection; every
 * call degrades to a no-op (with one log line) if JEP's internals move.
 *
 * <p>Entries come from profession defs: a def whose {@code poi} list declares an acquisition
 * hierarchy gets ONE entry under its canonical profession, whose stacks are the real work
 * surfaces — the subordinate {@code via} job blocks plus every block its work tasks declare
 * (tags expanded). The {@code via} professions and any other registered aliases are hidden
 * from JEP's native listing, so "place a pot, gain a chef" appears as recruitment flavor of
 * the one career instead of a sibling profession.</p>
 *
 * <p>Defs live server-side: in singleplayer and LAN the integrated server shares the registry
 * statics, so this works; on a dedicated-server client no defs are loaded and both steps
 * quietly do nothing, leaving JEP's stock behavior.</p>
 */
final class JepConsolidation {

    private static final String[] JEP_PLUGIN_CLASSES = {
            "com.mrbysco.justenoughprofessions.NeoForgeProfessionPlugin",
            "com.mrbysco.justenoughprofessions.ForgeProfessionPlugin",
            "com.mrbysco.justenoughprofessions.FabricProfessionPlugin"
    };
    private static final String JEP_ENTRY_CLASS = "com.mrbysco.justenoughprofessions.jei.ProfessionEntry";
    private static final String JEP_WRAPPER_CLASS = "com.mrbysco.justenoughprofessions.jei.ProfessionWrapper";

    private JepConsolidation() {}

    static void addConsolidatedCareers(IRecipeRegistration registration) {
        try {
            RecipeType<?> type = professionType();
            if (type == null) return;
            List<Object> wrappers = new ArrayList<>();
            for (ProfessionDef def : ProfessionDefs.all().values()) {
                if (!PoiHierarchy.hasAcquisitionHierarchy(def)) continue;
                VillagerProfession profession = registeredProfession(def.id());
                if (profession == null) continue;
                List<ItemStack> stacks = surfaceStacks(def);
                if (stacks.isEmpty()) continue;
                wrappers.add(wrap(profession, stacks));
            }
            if (wrappers.isEmpty()) return;
            addRecipesUnchecked(registration, type, wrappers);
        } catch (Throwable t) {
            Townstead.LOGGER.warn("JEP consolidation skipped (incompatible JEP version?): {}", t.toString());
        }
    }

    static void hideAbsorbedFlavors(IJeiRuntime runtime) {
        try {
            RecipeType<?> type = professionType();
            if (type == null) return;
            Set<ResourceLocation> hidden = absorbedProfessionIds();
            if (hidden.isEmpty()) return;
            List<Object> toHide = new ArrayList<>();
            for (Object wrapper : lookupAll(runtime, type)) {
                ResourceLocation id = wrapperProfessionId(wrapper);
                if (id != null && hidden.contains(id)) toHide.add(wrapper);
            }
            if (toHide.isEmpty()) return;
            hideRecipesUnchecked(runtime, type, toHide);
        } catch (Throwable t) {
            Townstead.LOGGER.warn("JEP flavor hiding skipped (incompatible JEP version?): {}", t.toString());
        }
    }

    /** The blocks a career actually works at: via job blocks first, then declared workstations. */
    private static List<ItemStack> surfaceStacks(ProfessionDef def) {
        LinkedHashSet<ResourceLocation> blockIds = new LinkedHashSet<>();
        for (JobSiteProvider provider : def.jobSites()) {
            if (provider instanceof JobSiteProvider.JobBlock block && block.via() != null) {
                blockIds.addAll(block.blocks());
            }
        }
        for (WorkTaskDef task : def.workTasks()) {
            blockIds.addAll(task.workstations().ids());
            for (ResourceLocation tagId : task.workstations().tags()) {
                TagKey<Block> tag = TagKey.create(Registries.BLOCK, tagId);
                for (Holder<Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(tag)) {
                    ResourceLocation id = BuiltInRegistries.BLOCK.getKey(holder.value());
                    if (id != null) blockIds.add(id);
                }
            }
        }
        List<ItemStack> stacks = new ArrayList<>();
        Set<Item> seen = new LinkedHashSet<>();
        for (ResourceLocation id : blockIds) {
            if (!BuiltInRegistries.BLOCK.containsKey(id)) continue;
            ItemStack stack = new ItemStack(BuiltInRegistries.BLOCK.get(id));
            if (stack.isEmpty() || !seen.add(stack.getItem())) continue;
            stacks.add(stack);
        }
        return stacks;
    }

    /** Registered alias and via professions of hierarchy defs — JEP's fragmented flavor entries. */
    private static Set<ResourceLocation> absorbedProfessionIds() {
        Set<ResourceLocation> hidden = new LinkedHashSet<>();
        for (ProfessionDef def : ProfessionDefs.all().values()) {
            if (!PoiHierarchy.hasAcquisitionHierarchy(def)) continue;
            for (ResourceLocation alias : def.aliases()) {
                if (!alias.equals(def.id())) hidden.add(alias);
            }
            for (JobSiteProvider provider : def.jobSites()) {
                if (provider instanceof JobSiteProvider.JobBlock block && block.via() != null) {
                    hidden.add(block.via());
                }
            }
        }
        return hidden;
    }

    private static VillagerProfession registeredProfession(ResourceLocation id) {
        if (id == null || !BuiltInRegistries.VILLAGER_PROFESSION.containsKey(id)) return null;
        VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.get(id);
        return profession == VillagerProfession.NONE ? null : profession;
    }

    // ── Reflection against JEP ──

    private static RecipeType<?> professionType() throws Exception {
        for (String className : JEP_PLUGIN_CLASSES) {
            try {
                Class<?> plugin = Class.forName(className);
                Object type = plugin.getField("PROFESSION_TYPE").get(null);
                if (type instanceof RecipeType<?> recipeType) return recipeType;
            } catch (ClassNotFoundException ignored) {
                // Not this loader's plugin; try the next.
            }
        }
        return null;
    }

    private static Object wrap(VillagerProfession profession, List<ItemStack> stacks) throws Exception {
        Class<?> entryClass = Class.forName(JEP_ENTRY_CLASS);
        Constructor<?> entryCtor = entryClass.getConstructor(VillagerProfession.class, List.class);
        Object entry = entryCtor.newInstance(profession, stacks);
        Class<?> wrapperClass = Class.forName(JEP_WRAPPER_CLASS);
        return wrapperClass.getConstructor(entryClass).newInstance(entry);
    }

    private static ResourceLocation wrapperProfessionId(Object wrapper) throws Exception {
        Method entryAccessor = wrapper.getClass().getMethod("entry");
        Object entry = entryAccessor.invoke(wrapper);
        Method professionAccessor = entry.getClass().getMethod("profession");
        Object profession = professionAccessor.invoke(entry);
        return profession instanceof VillagerProfession p
                ? BuiltInRegistries.VILLAGER_PROFESSION.getKey(p)
                : null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addRecipesUnchecked(IRecipeRegistration registration, RecipeType<?> type, List<Object> wrappers) {
        registration.addRecipes((RecipeType) type, (List) wrappers);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<Object> lookupAll(IJeiRuntime runtime, RecipeType<?> type) {
        return (List<Object>) runtime.getRecipeManager()
                .createRecipeLookup((RecipeType) type)
                .get().toList();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void hideRecipesUnchecked(IJeiRuntime runtime, RecipeType<?> type, List<Object> wrappers) {
        runtime.getRecipeManager().hideRecipes((RecipeType) type, (List) wrappers);
    }
}
