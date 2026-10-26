package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.data.PackInfo;
import com.aetherianartificer.townstead.root.RootBrowseEntry.Fact;
import com.aetherianartificer.townstead.root.RootBrowseEntry.Power;
import com.aetherianartificer.townstead.root.RootBrowseEntry.Text;
import com.aetherianartificer.townstead.root.ability.Ability;
import com.aetherianartificer.townstead.root.gene.Gene;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneRegistry;
import com.aetherianartificer.townstead.root.gene.GeneVariant;
import com.aetherianartificer.townstead.root.gene.InheritedGene;
import com.aetherianartificer.townstead.root.gene.types.AbilityGeneType;
import com.aetherianartificer.townstead.root.gene.types.ActiveAbilityGeneType;
import com.aetherianartificer.townstead.root.gene.types.AgelessGeneType;
import com.aetherianartificer.townstead.root.gene.types.AttachmentGeneType;
import com.aetherianartificer.townstead.root.gene.types.ChronotypeGeneType;
import com.aetherianartificer.townstead.root.gene.types.DietGeneType;
import com.aetherianartificer.townstead.root.gene.types.FertilityGeneType;
import com.aetherianartificer.townstead.root.gene.types.LitterSizeGeneType;
import com.aetherianartificer.townstead.root.gene.types.ScaleGeneType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Builds a {@link RootBrowseEntry} from a root's genes. The facts and traits are read off the gene
 * types themselves (a diet gene, a life cycle, a flight ability), so they stay true to what the
 * root actually does and need no extra authoring.
 *
 * <p>Diet, activity and lifespan always show when the root has them: they are what players compare
 * first. Everything else shows only when it sets the root apart from a human: a size other than
 * 100%, eggs, flight, unusual senses.</p>
 */
public final class RootBrowse {

    /** A gene counts towards facts and powers once it is expressed at least this often. */
    static final float EXPRESSED = 0.5f;

    private static final String FACT = "townstead.root.fact.";

    private RootBrowse() {}

    /** Everything about a root's genes that facts and traits read, gathered once. */
    public record Profile(
            @Nullable String diet,
            @Nullable Text activity,
            @Nullable String chronotype,
            int lifeDays,
            boolean ageless,
            boolean eggs,
            int litter,
            boolean sterile,
            float scale,
            Set<Ability> abilities,
            boolean wings
    ) {}

    public static RootBrowseEntry entry(Root root) {
        Profile profile = profile(root);
        PackInfo.Info pack = PackInfo.of(root.id().getNamespace());
        return new RootBrowseEntry(
                root.id().toString(),
                root.icon() == null ? "" : root.icon(),
                species(root),
                root.ancestry() == null ? "" : root.ancestry().toString(),
                root.lineage() == null ? "" : root.lineage().toString(),
                root.id().getNamespace(),
                text(pack.name()),
                text(pack.qualifier()),
                root.featured(),
                facts(profile),
                traits(profile),
                powers(root.id()));
    }

    private static String species(Root root) {
        ResourceLocation species = RootRegistry.effectiveSpecies(root.id());
        return species == null ? "" : species.toString();
    }

    static Profile profile(Root root) {
        String diet = null;
        Text activity = null;
        String chronotype = null;
        boolean ageless = false;
        int litter = 1;
        boolean sterile = false;
        float scale = 1f;
        boolean wings = false;
        Set<Ability> abilities = EnumSet.noneOf(Ability.class);
        for (Gene gene : expressedGenes(root.id())) {
            for (GeneVariant variant : gene.variants()) {
                GeneInstance instance = variant.instance();
                if (instance instanceof ChronotypeGeneType.Instance && activity == null) {
                    activity = activity(gene.displayName());
                    chronotype = chronotypeOf(gene.displayName());
                } else if (gene.hasVariants()) {
                    continue;   // a pick-one gene says nothing certain about every bearer
                } else if (instance instanceof DietGeneType.Instance d) {
                    diet = d.diet();
                } else if (instance instanceof AgelessGeneType.Instance) {
                    ageless = true;
                } else if (instance instanceof LitterSizeGeneType.Instance l) {
                    litter = Math.max(litter, l.litterSize());
                } else if (instance instanceof FertilityGeneType.Instance f) {
                    sterile |= !f.fertile();
                } else if (instance instanceof ScaleGeneType.Instance s) {
                    scale = s.scale();
                } else if (instance instanceof AbilityGeneType.Instance a && a.ability() != null) {
                    abilities.add(a.ability());
                } else if (instance instanceof AttachmentGeneType.Instance a) {
                    for (String id : a.attachments()) wings |= id.toLowerCase(Locale.ROOT).contains("wing");
                }
            }
        }
        LifeCycle cycle = RootRegistry.effectiveLifeCycle(root.id());
        int days = cycle == null || cycle.isEmpty() ? 0 : cycle.totalBaseDays();
        boolean eggs = false;
        if (cycle != null) {
            ageless |= cycle.ageless();
            for (LifeStage stage : cycle.stages()) {
                eggs |= stage.id().toLowerCase(Locale.ROOT).contains("egg") || (!stage.mobile() && !stage.talkable());
            }
        }
        return new Profile(diet, activity, chronotype, days, ageless, eggs, litter, sterile, scale, abilities, wings);
    }

    public static List<Fact> facts(Profile p) {
        List<Fact> out = new ArrayList<>();
        if (p.diet() != null) out.add(new Fact("diet", diet(p.diet())));
        if (p.activity() != null) out.add(new Fact("active", p.activity()));
        if (p.ageless()) {
            out.add(new Fact("lifespan", Text.of(FACT + "lifespan.ageless", "Ageless")));
        } else if (p.lifeDays() > 0) {
            float ratio = p.lifeDays() / (float) LifeCycle.defaultHumanLike().totalBaseDays();
            out.add(new Fact("lifespan", Math.abs(ratio - 1f) < 0.15f
                    ? Text.of(FACT + "lifespan.human", "Like a human")
                    : Text.of(FACT + "lifespan.ratio", ratioText(ratio) + "x human", ratioText(ratio))));
        }
        if (Math.abs(p.scale() - 1f) >= 0.02f) {
            String percent = Integer.toString(Math.round(p.scale() * 100f));
            out.add(new Fact("size", Text.of(FACT + "size.percent", percent + "%%", percent)));
        }
        if (p.sterile()) {
            out.add(new Fact("born", Text.of(FACT + "born.never", "Cannot have children")));
        } else if (p.eggs()) {
            out.add(new Fact("born", p.litter() > 1
                    ? Text.of(FACT + "born.clutch", "Eggs, clutch of " + p.litter(), Integer.toString(p.litter()))
                    : Text.of(FACT + "born.eggs", "Eggs")));
        } else if (p.litter() > 1) {
            out.add(new Fact("born", Text.of(FACT + "born.litter", "Litters of " + p.litter(), Integer.toString(p.litter()))));
        }
        Set<Ability> a = p.abilities();
        if (a.contains(Ability.CREATIVE_FLIGHT)) out.add(new Fact("moves", Text.of(FACT + "moves.flies", "Flies")));
        else if (a.contains(Ability.ELYTRA_FLIGHT)) out.add(new Fact("moves", Text.of(FACT + "moves.glides", "Glides")));
        else if (a.contains(Ability.HOVER)) out.add(new Fact("moves", Text.of(FACT + "moves.hovers", "Hovers")));
        if (a.contains(Ability.WATER_BREATHING)) out.add(new Fact("breathes", Text.of(FACT + "breathes.water", "Air and water")));
        if (a.contains(Ability.NIGHT_VISION)) out.add(new Fact("senses", Text.of(FACT + "senses.darkvision", "Darkvision")));
        else if (a.contains(Ability.LAVA_VISION)) out.add(new Fact("senses", Text.of(FACT + "senses.lava", "Sees through lava")));
        return out;
    }

    /** The keys the browser's trait filters match on. */
    public static List<String> traits(Profile p) {
        Set<String> out = new LinkedHashSet<>();
        Set<Ability> a = p.abilities();
        if (p.wings() || a.contains(Ability.CREATIVE_FLIGHT) || a.contains(Ability.ELYTRA_FLIGHT)) out.add("winged");
        if (p.scale() <= 0.9f) out.add("small");
        if (p.scale() >= 1.1f) out.add("large");
        if (a.contains(Ability.WATER_BREATHING)) out.add("aquatic");
        if (p.eggs() && !p.sterile()) out.add("lays_eggs");
        if ("nocturnal".equals(p.chronotype())) out.add("nocturnal");
        if (a.contains(Ability.NIGHT_VISION)) out.add("darkvision");
        if (p.ageless()) out.add("ageless");
        if (p.sterile()) out.add("sterile");
        return List.copyOf(out);
    }

    /** {@code 8.33} → "8.3", {@code 4.0} → "4", {@code 12.6} → "13". */
    static String ratioText(float ratio) {
        if (ratio >= 10f) return Integer.toString(Math.round(ratio));
        float rounded = Math.round(ratio * 10f) / 10f;
        return rounded == Math.floor(rounded) ? Integer.toString((int) rounded) : Float.toString(rounded);
    }

    private static List<Power> powers(ResourceLocation rootId) {
        List<Power> out = new ArrayList<>();
        for (Gene gene : expressedGenes(rootId)) {
            if (!"abilities".equalsIgnoreCase(gene.category())) continue;
            GeneInstance instance = gene.instance();
            if (gene.drawback()) {
                out.add(new Power(gene.id().toString(), Power.Kind.DRAWBACK, 0));
            } else if (instance instanceof ActiveAbilityGeneType.Instance active) {
                out.add(new Power(gene.id().toString(), Power.Kind.ACTIVE, active.cooldownTicks()));
            } else {
                out.add(new Power(gene.id().toString(), Power.Kind.STRENGTH, 0));
            }
        }
        return out;
    }

    /** The root's genes expressed often enough to describe it, then their companions, without repeats. */
    private static List<Gene> expressedGenes(ResourceLocation rootId) {
        Set<ResourceLocation> seen = new LinkedHashSet<>();
        List<Gene> out = new ArrayList<>();
        for (InheritedGene inherited : RootRegistry.effectiveInheritedGenes(rootId)) {
            if (inherited.occurrence() < EXPRESSED) continue;
            add(inherited.geneId(), seen, out);
        }
        for (Gene gene : List.copyOf(out)) {
            for (ResourceLocation companion : GeneRegistry.companionsOf(gene.id())) add(companion, seen, out);
        }
        return out;
    }

    private static void add(ResourceLocation id, Set<ResourceLocation> seen, List<Gene> out) {
        if (!seen.add(id)) return;
        Gene gene = GeneRegistry.byId(id);
        if (gene != null) out.add(gene);
    }

    private static Text diet(String diet) {
        String id = diet.toLowerCase(Locale.ROOT);
        String bare = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return switch (bare) {
            case "omnivore", "carnivore", "herbivore", "none" -> Text.of(FACT + "diet." + bare, titleCase(bare));
            default -> Text.literal(titleCase(bare));
        };
    }

    /** The bundled day/dusk/night mixes read as when they are awake; any other chronotype by its own name. */
    private static Text activity(Component name) {
        String mix = chronotypeOf(name);
        if (mix != null) {
            return switch (mix) {
                case "diurnal" -> Text.of(FACT + "active.diurnal", "By day");
                case "crepuscular" -> Text.of(FACT + "active.crepuscular", "Dusk and dawn");
                case "nocturnal" -> Text.of(FACT + "active.nocturnal", "By night");
                default -> text(name);
            };
        }
        return text(name);
    }

    private static @Nullable String chronotypeOf(Component name) {
        if (name.getContents() instanceof TranslatableContents t && t.getKey().startsWith("chronotype.mix.")) {
            return t.getKey().substring("chronotype.mix.".length());
        }
        return null;
    }

    static Text text(Component component) {
        if (component.getContents() instanceof TranslatableContents t && t.getArgs().length == 0) {
            return new Text(t.getKey(), List.of(), component.getString());
        }
        return Text.literal(component.getString());
    }

    private static String titleCase(String id) {
        StringBuilder out = new StringBuilder();
        for (String word : id.split("[_\\-./]+")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
