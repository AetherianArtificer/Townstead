package com.aetherianartificer.townstead.politics.definition;

import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a kind of faction is: whether it holds land, how people join it, which offices it has, and
 * how it is governed. Defined in {@code data/<ns>/faction/}.
 */
public record FactionKind(ResourceLocation id,
                          PoliticalDisplay display,
                          Set<ResourceLocation> tags,
                          boolean holdsLand,
                          Membership membership,
                          List<Office> offices,
                          Founding founding,
                          Presentation presentation,
                          @Nullable GovernanceDefinition governance,
                          Members members,
                          @Nullable Recruitment recruitment) {
    public static final String SCHEMA = "townstead:faction/v1";
    public static final ResourceLocation GENERATED = id("townstead:generated");
    public static final ResourceLocation OPEN = id("townstead:open");
    public static final ResourceLocation APPLICATION = id("townstead:application");
    public static final ResourceLocation RESIDENCE = id("townstead:residence");
    private static final Set<ResourceLocation> ADMISSIONS = Set.of(OPEN, APPLICATION, RESIDENCE);

    public FactionKind {
        tags = Set.copyOf(tags);
        offices = List.copyOf(offices);
    }

    public boolean generated() {
        return tags.contains(GENERATED);
    }

    public @Nullable Office office(ResourceLocation bond) {
        for (Office office : offices) if (office.bond().equals(bond)) return office;
        return null;
    }

    public Set<ResourceLocation> officeIds() {
        Set<ResourceLocation> out = new LinkedHashSet<>();
        for (Office office : offices) out.add(office.bond());
        return out;
    }

    /**
     * How people join. {@code admission} is {@code townstead:open}, {@code townstead:application}
     * (approved by holders of {@code approval}) or {@code townstead:residence} (by living in one of
     * the faction's settlements). {@code noticeDays} is how long a departure takes, 0 for at once.
     */
    public record Membership(ResourceLocation bond, ResourceLocation admission, ResourceLocation approval,
                             int approvals, int noticeDays, Condition eligibility) {
        static Membership parse(JsonObject json) {
            ResourceLocation admission = PoliticalJson.requiredId(json, "admission");
            if (!ADMISSIONS.contains(admission)) {
                throw new IllegalArgumentException("Unknown admission " + admission + "; known admissions are " + ADMISSIONS);
            }
            ResourceLocation approval = PoliticalJson.optionalId(json, "approval");
            int approvals = GsonHelper.getAsInt(json, "approvals", 1);
            if (approvals < 1) throw new IllegalArgumentException("'membership.approvals' must be at least 1");
            int notice = GsonHelper.getAsInt(json, "notice_days", 0);
            if (notice < 0) throw new IllegalArgumentException("'membership.notice_days' cannot be negative");
            Condition eligibility = Conditions.ALWAYS;
            if (json.has("eligibility")) {
                eligibility = Conditions.parse(json.get("eligibility"));
                if (eligibility == null) throw new IllegalArgumentException("'membership.eligibility' is not a registered Pheno condition");
            }
            return new Membership(PoliticalJson.requiredId(json, "bond"), admission,
                    approval == null ? id("townstead:review_membership_requests") : approval, approvals, notice, eligibility);
        }
    }

    /** An office is a bond kind; the faction kind says how many seats it has. {@code max} -1 is unlimited. */
    public record Office(ResourceLocation bond, int minimum, int maximum, boolean founder) {
        static Office parse(JsonObject json) {
            int minimum = GsonHelper.getAsInt(json, "min", 0);
            int maximum = GsonHelper.getAsInt(json, "max", -1);
            if (minimum < 0) throw new IllegalArgumentException("Office minimum cannot be negative");
            if (maximum < -1 || (maximum >= 0 && maximum < minimum)) {
                throw new IllegalArgumentException("Office maximum must be -1 or at least its minimum");
            }
            boolean founder = GsonHelper.getAsBoolean(json, "founder", false);
            if (founder && maximum == 0) throw new IllegalArgumentException("A founder office cannot have a maximum of zero");
            return new Office(PoliticalJson.requiredId(json, "bond"), minimum, maximum, founder);
        }

        public boolean full(int holders) {
            return maximum >= 0 && holders >= maximum;
        }
    }

    /**
     * How it comes to be. An order also names what its founding hands over: {@code gifts} (items
     * given to whoever founds it, such as a lodge's Oath Altar) and {@code teaches} (recipes they learn).
     */
    public record Founding(ResourceLocation procedure, Condition eligibility,
                           List<ResourceLocation> gifts, List<ResourceLocation> teaches) {
        public Founding {
            gifts = List.copyOf(gifts);
            teaches = List.copyOf(teaches);
        }

        static Founding parse(@Nullable JsonObject json) {
            if (json == null) return new Founding(id("townstead:generated"), Conditions.ALWAYS, List.of(), List.of());
            Condition eligibility = Conditions.ALWAYS;
            if (json.has("eligibility")) {
                eligibility = Conditions.parse(json.get("eligibility"));
                if (eligibility == null) throw new IllegalArgumentException("'founding.eligibility' is not a registered Pheno condition");
            }
            return new Founding(PoliticalJson.requiredId(json, "procedure"), eligibility,
                    ids(json, "gifts"), ids(json, "teaches"));
        }

        private static List<ResourceLocation> ids(JsonObject json, String field) {
            List<ResourceLocation> out = new ArrayList<>();
            JsonArray array = PoliticalJson.array(json, field, false);
            if (array != null) for (JsonElement element : array) {
                ResourceLocation value = com.aetherianartificer.townstead.data.DataPackLang.parseId(element.getAsString());
                if (value == null) throw new IllegalArgumentException("'founding." + field + "' has a bad id");
                out.add(value);
            }
            return out;
        }
    }

    /** {@code factionNames} names its own name pool, used whatever the founding culture; null means the culture's. */
    public record Presentation(List<String> factionNamePatterns, @Nullable ResourceLocation factionNames) {
        public Presentation { factionNamePatterns = List.copyOf(factionNamePatterns); }

        static Presentation parse(@Nullable JsonObject json) {
            if (json == null) return new Presentation(List.of("{name}"), null);
            ResourceLocation names = json.has("faction_names")
                    ? com.aetherianartificer.townstead.data.DataPackLang.parseId(GsonHelper.getAsString(json, "faction_names")) : null;
            return new Presentation(com.aetherianartificer.townstead.culture.FactionNaming.parsePatterns(json), names);
        }
    }

    /**
     * What membership makes someone: the disposition {@code group} its members belong to, and the
     * {@code profession} they practice (members are hunters, hunters are members). Both optional.
     */
    /** What sworn members become: a disposition group, a profession, and the shift template they work. */
    public record Members(@Nullable String group, @Nullable ResourceLocation profession, @Nullable ResourceLocation shift) {
        public static final Members NONE = new Members(null, null, null);

        static Members parse(@Nullable JsonObject json) {
            if (json == null) return NONE;
            String group = json.has("group") ? GsonHelper.getAsString(json, "group") : null;
            ResourceLocation profession = json.has("profession")
                    ? com.aetherianartificer.townstead.data.DataPackLang.parseId(GsonHelper.getAsString(json, "profession")) : null;
            ResourceLocation shift = json.has("shift")
                    ? com.aetherianartificer.townstead.data.DataPackLang.parseId(GsonHelper.getAsString(json, "shift")) : null;
            return new Members(group, profession, shift);
        }
    }

    /**
     * How an order takes on villagers: they train for {@code trainingNights} nights near its altar,
     * then take {@code ritual}. Room is {@code 1 + one per} block of {@code perBlock} in its
     * {@code building} buildings. Adults meeting {@code volunteer} sometimes ask to join, at
     * {@code volunteerChance} a night.
     */
    public record Recruitment(ResourceLocation ritual, int trainingNights, @Nullable String building,
                              @Nullable ResourceLocation perBlock, @Nullable JsonElement volunteer, double volunteerChance) {
        /** Parsed on use: political definitions load before every Pheno condition is known. Null when malformed. */
        public @Nullable Condition volunteerCondition() {
            return volunteer == null ? Conditions.ALWAYS : Conditions.parse(volunteer);
        }

        static @Nullable Recruitment parse(@Nullable JsonObject json) {
            if (json == null) return null;
            ResourceLocation ritual = PoliticalJson.requiredId(json, "ritual");
            int nights = GsonHelper.getAsInt(json, "training_nights", 3);
            if (nights < 0) throw new IllegalArgumentException("'recruitment.training_nights' cannot be negative");
            JsonObject room = GsonHelper.getAsJsonObject(json, "room", new JsonObject());
            String building = room.has("building") ? GsonHelper.getAsString(room, "building") : null;
            String block = room.has("per_block") ? GsonHelper.getAsString(room, "per_block") : null;
            ResourceLocation perBlock = block == null ? null
                    : com.aetherianartificer.townstead.data.DataPackLang.parseId(block.startsWith("#") ? block.substring(1) : block);
            JsonObject volunteers = GsonHelper.getAsJsonObject(json, "volunteers", new JsonObject());
            JsonElement volunteer = volunteers.has("condition") ? volunteers.get("condition") : null;
            double chance = GsonHelper.getAsDouble(volunteers, "chance", 0);
            return new Recruitment(ritual, nights, building, perBlock, volunteer, chance);
        }
    }

    public static FactionKind parse(ResourceLocation id, JsonObject json, Map<String, String> lang) {
        TownsteadSchema.validateRequired(json, SCHEMA);
        List<Office> offices = new ArrayList<>();
        Set<ResourceLocation> seen = new LinkedHashSet<>();
        JsonArray officeArray = PoliticalJson.array(json, "offices", false);
        if (officeArray != null) {
            for (JsonElement element : officeArray) {
                if (!element.isJsonObject()) throw new IllegalArgumentException("Every office must be an object");
                Office office = Office.parse(element.getAsJsonObject());
                if (!seen.add(office.bond())) throw new IllegalArgumentException("Office " + office.bond() + " is listed twice");
                offices.add(office);
            }
        }
        JsonObject governanceJson = PoliticalJson.object(json, "governance", false);
        return new FactionKind(id, PoliticalDisplay.parse(json, id, lang), PoliticalJson.idSet(json, "tags"),
                GsonHelper.getAsBoolean(json, "holds_land", true),
                Membership.parse(PoliticalJson.object(json, "membership", true)), offices,
                Founding.parse(PoliticalJson.object(json, "founding", false)),
                Presentation.parse(PoliticalJson.object(json, "presentation", false)),
                governanceJson == null ? null : GovernanceDefinition.parse(governanceJson, seen),
                Members.parse(PoliticalJson.object(json, "members", false)),
                Recruitment.parse(PoliticalJson.object(json, "recruitment", false)));
    }

    private static ResourceLocation id(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) throw new IllegalStateException(value);
        return id;
    }
}
