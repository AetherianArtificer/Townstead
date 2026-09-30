package com.aetherianartificer.townstead.contract;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.chronicle.Chronicles;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.ModGate;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.story.net.StoryQuestSyncS2CPayload;
import com.aetherianartificer.townstead.story.reward.Reward;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Radiant contracts: repeatable jobs a giver rolls from a pool of {@link ContractDefinition}s.
 * Each player keeps a few rolled offers per pool (renewed every few days, at most one of each
 * {@code offer_group} while others fit) and up to {@value #MAX_ACTIVE} taken contracts. Values are
 * rolled at offer time and frozen. Taken contracts are read every few seconds, show in the Quest
 * Ledger, expire after their days, and are handed in to any giver of the same pool. Stories give
 * them through Ink helpers ({@code contract_offer}, {@code contract_accept},
 * {@code contract_turn_in} and friends).
 */
public final class Contracts {
    /** Chronicle counter on a giver each time a contract they gave is handed in. */
    public static final String GIVEN_DONE = "townstead:contracts_given_done";
    /** Chronicle counter on the player each time they hand in a contract. */
    public static final String DONE = "townstead:contracts_done";
    private static final String KEY = "townstead:contracts";
    private static final int MAX_ACTIVE = 3;
    private static final int OFFERS = 3;
    private static final int OFFER_DAYS = 3;
    private static final int TICK = 100;

    private static volatile Map<ResourceLocation, ContractDefinition> templates = Map.of();
    private static volatile Map<ResourceLocation, String> rejected = Map.of();

    private Contracts() {}

    public static Map<ResourceLocation, ContractDefinition> loaded() { return templates; }

    public static Map<ResourceLocation, String> rejected() { return rejected; }

    // ---- data ----

    /** A rolled contract: its template, its frozen values, and, once taken, its progress. */
    public static final class Rolled {
        final ResourceLocation template;
        final Map<String, String> values;
        @Nullable UUID giver;
        String giverName = "";
        long expiresDay = -1;
        long[] base = new long[0];
        long[] progress = new long[0];
        boolean ready;
        List<ContractObjective> objectives;

        Rolled(ResourceLocation template, Map<String, String> values) {
            this.template = template;
            this.values = values;
        }
    }

    private static final class State {
        final Map<ResourceLocation, List<Rolled>> offers = new HashMap<>();
        final Map<ResourceLocation, Long> offeredDay = new HashMap<>();
        final List<Rolled> active = new ArrayList<>();
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    // ---- giving ----

    /** The first offer for {@code player} in {@code pool}, rolling fresh ones when there are none or they are old. */
    public static @Nullable Rolled offer(ServerPlayer player, @Nullable LivingEntity giver, ResourceLocation pool) {
        State state = state(player);
        long today = TownsteadCalendar.worldDay(player.server);
        List<Rolled> offers = state.offers.computeIfAbsent(pool, key -> new ArrayList<>());
        offers.removeIf(rolled -> !available(templates.get(rolled.template), player, giver));
        if (offers.isEmpty() || today - state.offeredDay.getOrDefault(pool, today) >= OFFER_DAYS) {
            offers.clear();
            offers.addAll(roll(player, giver, pool));
            state.offeredDay.put(pool, today);
            save(player);
        }
        return offers.isEmpty() ? null : offers.get(0);
    }

    public static String title(ServerPlayer player, @Nullable Rolled rolled) {
        ContractDefinition def = rolled == null ? null : templates.get(rolled.template);
        return def == null ? "" : def.title().resolve(values(rolled, player), locale(player));
    }

    public static String about(ServerPlayer player, @Nullable Rolled rolled) {
        ContractDefinition def = rolled == null ? null : templates.get(rolled.template);
        return def == null ? "" : def.about().resolve(values(rolled, player), locale(player));
    }

    /** Moves the first offer to the back, so the giver can offer the next one. */
    public static void skip(ServerPlayer player, ResourceLocation pool) {
        List<Rolled> offers = state(player).offers.get(pool);
        if (offers != null && offers.size() > 1) offers.add(offers.remove(0));
    }

    /** Takes the first offer. False when there is none, or the player already carries the most they can. */
    public static boolean accept(ServerPlayer player, LivingEntity giver, ResourceLocation pool) {
        State state = state(player);
        List<Rolled> offers = state.offers.get(pool);
        if (offers == null || offers.isEmpty() || state.active.size() >= MAX_ACTIVE) return false;
        Rolled rolled = offers.get(0);
        ContractDefinition def = templates.get(rolled.template);
        if (def == null || objectives(rolled) == null) return false;
        offers.remove(0);
        rolled.giver = giver.getUUID();
        rolled.giverName = giver.getName().getString();
        rolled.expiresDay = TownsteadCalendar.worldDay(player.server) + def.days();
        rolled.base = new long[rolled.objectives.size()];
        rolled.progress = new long[rolled.objectives.size()];
        for (int i = 0; i < rolled.objectives.size(); i++) {
            ContractObjective objective = rolled.objectives.get(i);
            if (objective.gain()) rolled.base[i] = Math.max(0L, objective.read(player, giver));
        }
        state.active.add(rolled);
        if (def.onAccept() != null) run(def.onAccept(), rolled, giver, player);
        save(player);
        com.aetherianartificer.townstead.story.StoryService.sync(player);
        return true;
    }

    /** How many taken contracts from {@code pool} are ready to hand in. */
    public static int ready(ServerPlayer player, ResourceLocation pool) {
        return (int) state(player).active.stream().filter(r -> r.ready && pool(r).equals(pool)).count();
    }

    /** How many contracts from {@code pool} the player has taken and not yet handed in. */
    public static int active(ServerPlayer player, ResourceLocation pool) {
        return (int) state(player).active.stream().filter(r -> pool(r).equals(pool)).count();
    }

    /** Hands in every ready contract from {@code pool} to {@code giver}. Returns how many were handed in. */
    public static int turnIn(ServerPlayer player, LivingEntity giver, ResourceLocation pool) {
        State state = state(player);
        int done = 0;
        for (Rolled rolled : List.copyOf(state.active)) {
            if (!rolled.ready || !pool(rolled).equals(pool) || objectives(rolled) == null) continue;
            ContractDefinition def = templates.get(rolled.template);
            if (def == null) continue;
            // Every objective must still hold (goods still carried) before any is taken.
            boolean stillThere = rolled.objectives.stream().allMatch(o -> o.latch() || o.read(player, giver) >= o.count());
            if (!stillThere) {
                rolled.ready = false;
                continue;
            }
            rolled.objectives.forEach(o -> o.turnIn(player));
            if (def.onTurnIn() != null) run(def.onTurnIn(), rolled, giver, player);
            for (ContractReward reward : rewards(def, rolled, player)) {
                try {
                    reward.give(player, giver);
                } catch (RuntimeException e) {
                    Townstead.LOGGER.warn("Contract {} reward failed: {}", def.id(), e.getMessage());
                }
            }
            state.active.remove(rolled);
            Chronicles.addCounter(player.server, player.getUUID(), DONE, 1);
            if (rolled.giver != null) Chronicles.addCounter(player.server, rolled.giver, GIVEN_DONE, 1);
            done++;
        }
        if (done > 0) {
            save(player);
            com.aetherianartificer.townstead.story.StoryService.sync(player);
        }
        return done;
    }

    // ---- progress ----

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % TICK != 0) return;
        long today = TownsteadCalendar.worldDay(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            State state = state(player);
            if (state.active.isEmpty()) continue;
            boolean changed = false;
            for (Rolled rolled : List.copyOf(state.active)) {
                ContractDefinition def = templates.get(rolled.template);
                if (def == null || (!rolled.ready && today > rolled.expiresDay)) {
                    state.active.remove(rolled);
                    changed = true;
                    continue;
                }
                if (objectives(rolled) == null) continue;
                LivingEntity giver = giver(server, rolled);
                boolean all = true;
                for (int i = 0; i < rolled.objectives.size() && i < rolled.progress.length; i++) {
                    ContractObjective objective = rolled.objectives.get(i);
                    long reading = objective.read(player, giver);
                    if (reading != ContractObjective.UNKNOWN) {
                        long value = Math.min(objective.count(), Math.max(0L, objective.gain() ? reading - rolled.base[i] : reading));
                        // Latched progress holds once reached: leaving the place does not undo getting there.
                        if (objective.latch() ? value > rolled.progress[i] : value != rolled.progress[i]) {
                            rolled.progress[i] = value;
                            changed = true;
                        }
                    }
                    if (rolled.progress[i] < objective.count()) all = false;
                }
                if (all != rolled.ready) {
                    rolled.ready = all;
                    changed = true;
                }
            }
            if (changed) {
                save(player);
                com.aetherianartificer.townstead.story.StoryService.sync(player);
            }
        }
    }

    /** Adds the player's contracts to the Quest Ledger list. */
    public static void ledger(ServerPlayer player, List<StoryQuestSyncS2CPayload.Quest> quests) {
        String locale = locale(player);
        String playerName = player.getGameProfile().getName();
        int n = 0;
        for (Rolled rolled : state(player).active) {
            ContractDefinition def = templates.get(rolled.template);
            if (def == null || objectives(rolled) == null) continue;
            Map<String, String> values = values(rolled, player);
            List<StoryQuestSyncS2CPayload.Objective> objectives = new ArrayList<>();
            for (int i = 0; i < rolled.objectives.size(); i++) {
                ContractObjective objective = rolled.objectives.get(i);
                long current = i < rolled.progress.length ? rolled.progress[i] : 0L;
                objectives.add(new StoryQuestSyncS2CPayload.Objective(objective.label(values, locale, player),
                        Math.min(current, objective.count()), objective.count(), current >= objective.count()));
            }
            List<StoryQuestSyncS2CPayload.Reward> rewards = new ArrayList<>();
            String xpText = DataPackLang.resolveFallback("contract.townstead.reward.xp", locale, "Experience");
            for (ContractReward reward : rewards(def, rolled, player)) {
                for (Reward.Preview preview : reward.preview(rolled.giverName, playerName, xpText)) {
                    rewards.add(new StoryQuestSyncS2CPayload.Reward(preview.text(), preview.itemId(), preview.count()));
                }
            }
            quests.add(new StoryQuestSyncS2CPayload.Quest("contract/" + def.id() + "#" + n++,
                    def.title().resolve(values, locale), def.about().resolve(values, locale),
                    rolled.giverName, (byte) (rolled.ready ? 1 : 0), true, List.copyOf(objectives), List.copyOf(rewards)));
        }
    }

    // ---- rolling ----

    private static List<Rolled> roll(ServerPlayer player, @Nullable LivingEntity giver, ResourceLocation pool) {
        List<ContractDefinition> fit = new ArrayList<>();
        for (ContractDefinition def : templates.values()) {
            if (def.pool().equals(pool) && available(def, player, giver)) fit.add(def);
        }
        fit.sort((a, b) -> a.id().compareTo(b.id()));
        List<Rolled> out = new ArrayList<>();
        List<String> groups = new ArrayList<>();
        RandomSource random = player.getRandom();
        for (int n = 0; n < OFFERS && !fit.isEmpty(); n++) {
            // One of each offer group while others still fit.
            List<ContractDefinition> fresh = fit.stream().filter(d -> d.offerGroup() == null || !groups.contains(d.offerGroup())).toList();
            List<ContractDefinition> from = fresh.isEmpty() ? fit : fresh;
            int pick = random.nextInt(from.stream().mapToInt(ContractDefinition::weight).sum());
            ContractDefinition chosen = from.get(0);
            for (ContractDefinition def : from) {
                pick -= def.weight();
                if (pick < 0) {
                    chosen = def;
                    break;
                }
            }
            fit.remove(chosen);
            if (chosen.offerGroup() != null) groups.add(chosen.offerGroup());
            Map<String, String> values = rollValues(chosen, player, random);
            if (values != null) out.add(new Rolled(chosen.id(), values));
        }
        return out;
    }

    /** Picks one value per variable, and the emeralds of each currency reward. Null when a pool is empty. */
    private static @Nullable Map<String, String> rollValues(ContractDefinition def, ServerPlayer player, RandomSource random) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, JsonObject> entry : def.variables().entrySet()) {
            JsonObject variable = entry.getValue();
            String name = entry.getKey();
            switch (GsonHelper.getAsString(variable, "kind")) {
                case "int" -> {
                    int min = GsonHelper.getAsInt(variable, "min"), max = GsonHelper.getAsInt(variable, "max", min);
                    double value = min + (max > min ? random.nextInt(max - min + 1) : 0)
                            + GsonHelper.getAsDouble(variable, "per_player_level", 0) * player.experienceLevel;
                    if (variable.has("limit")) value = Math.min(value, GsonHelper.getAsInt(variable, "limit"));
                    values.put(name, Long.toString((long) Math.floor(value)));
                }
                case "item", "block", "entity" -> {
                    List<ResourceLocation> pool = registryPool(variable);
                    if (pool.isEmpty()) return null;
                    ResourceLocation id = pool.get(random.nextInt(pool.size()));
                    values.put(name, id.toString());
                    values.put(name + "_name", displayName(GsonHelper.getAsString(variable, "kind"), id));
                }
                case "text" -> {
                    JsonArray options = GsonHelper.getAsJsonArray(variable, "options", new JsonArray());
                    if (options.isEmpty()) return null;
                    values.put(name, ContractText.parse(options.get(random.nextInt(options.size()))).resolve(values, locale(player)));
                }
                default -> {
                    return null;
                }
            }
        }
        for (int i = 0; i < def.rewards().size(); i++) {
            JsonObject reward = def.rewards().get(i);
            if (!"townstead:currency".equals(GsonHelper.getAsString(reward, "type", ""))) continue;
            int[] range = ContractReward.currencyRange(reward, def.difficulty());
            values.put("_reward" + i, Integer.toString(range[0] + random.nextInt(range[1] - range[0] + 1)));
        }
        return values;
    }

    private static List<ResourceLocation> registryPool(JsonObject variable) {
        String kind = GsonHelper.getAsString(variable, "kind");
        TreeSet<ResourceLocation> ids = new TreeSet<>();
        if (variable.has("ids")) {
            for (JsonElement e : variable.getAsJsonArray("ids")) {
                ResourceLocation id = DataPackLang.parseId(e.getAsString());
                if (id != null && registered(kind, id)) ids.add(id);
            }
        }
        if (variable.has("tags")) {
            for (JsonElement e : variable.getAsJsonArray("tags")) {
                ResourceLocation tag = DataPackLang.parseId(e.getAsString().replace("#", ""));
                if (tag == null) continue;
                switch (kind) {
                    case "item" -> BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, tag))
                            .forEach(h -> ids.add(BuiltInRegistries.ITEM.getKey(h.value())));
                    case "block" -> BuiltInRegistries.BLOCK.getTagOrEmpty(TagKey.create(Registries.BLOCK, tag))
                            .forEach(h -> ids.add(BuiltInRegistries.BLOCK.getKey(h.value())));
                    default -> BuiltInRegistries.ENTITY_TYPE.getTagOrEmpty(TagKey.create(Registries.ENTITY_TYPE, tag))
                            .forEach(h -> ids.add(BuiltInRegistries.ENTITY_TYPE.getKey(h.value())));
                }
            }
        }
        return new ArrayList<>(ids);
    }

    private static boolean registered(String kind, ResourceLocation id) {
        return switch (kind) {
            case "item" -> BuiltInRegistries.ITEM.containsKey(id);
            case "block" -> BuiltInRegistries.BLOCK.containsKey(id);
            default -> BuiltInRegistries.ENTITY_TYPE.containsKey(id);
        };
    }

    private static String displayName(String kind, ResourceLocation id) {
        return switch (kind) {
            case "item" -> BuiltInRegistries.ITEM.get(id).getDescription().getString();
            case "block" -> BuiltInRegistries.BLOCK.get(id).getName().getString();
            default -> BuiltInRegistries.ENTITY_TYPE.get(id).getDescription().getString();
        };
    }

    // ---- helpers ----

    private static boolean available(@Nullable ContractDefinition def, ServerPlayer player, @Nullable LivingEntity giver) {
        if (def == null) return false;
        Condition when = def.when();
        return when == null || when.test(new ConditionContext(player, giver));
    }

    private static ResourceLocation pool(Rolled rolled) {
        ContractDefinition def = templates.get(rolled.template);
        return def == null ? rolled.template : def.pool();
    }

    private static Map<String, String> values(Rolled rolled, ServerPlayer player) {
        Map<String, String> values = new LinkedHashMap<>(rolled.values);
        values.put("player", player.getGameProfile().getName());
        return values;
    }

    /** The objectives of a rolled contract, built once from its template and values. Null when one does not build. */
    private static @Nullable List<ContractObjective> objectives(Rolled rolled) {
        if (rolled.objectives != null) return rolled.objectives;
        ContractDefinition def = templates.get(rolled.template);
        if (def == null) return null;
        List<ContractObjective> built = new ArrayList<>();
        try {
            for (JsonObject raw : def.objectives()) built.add(ContractObjective.parse(filled(raw, rolled.values).getAsJsonObject()));
        } catch (RuntimeException e) {
            Townstead.LOGGER.warn("Contract {}: an objective does not build: {}", def.id(), e.getMessage());
            return null;
        }
        rolled.objectives = built;
        return built;
    }

    private static List<ContractReward> rewards(ContractDefinition def, Rolled rolled, ServerPlayer player) {
        List<ContractReward> out = new ArrayList<>();
        String locale = locale(player);
        for (int i = 0; i < def.rewards().size(); i++) {
            JsonObject json = filled(def.rewards().get(i), rolled.values).getAsJsonObject();
            if ("townstead:currency".equals(GsonHelper.getAsString(json, "type", ""))) {
                json.addProperty("count", Integer.parseInt(rolled.values.getOrDefault("_reward" + i, "1")));
            }
            String text = json.has("text") ? ContractText.parse(json.get("text")).resolve(rolled.values, locale) : "";
            ContractReward reward = ContractReward.parse(json, def.id().getNamespace(), text);
            if (reward != null) out.add(reward);
        }
        return out;
    }

    private static boolean run(JsonObject raw, Rolled rolled, LivingEntity giver, ServerPlayer player) {
        Action action = Actions.parse(filled(raw, rolled.values));
        if (action == null) return false;
        ActionContext ctx = new ActionContext(giver, player);
        action.run(ctx);
        return ctx.succeeded();
    }

    /** Template JSON with values in: a string exactly {@code "{var}"} becomes the value (numbers as numbers); {@code {var}} inside text is replaced. */
    static JsonElement filled(JsonElement raw, Map<String, String> values) {
        if (raw.isJsonObject()) {
            JsonObject out = new JsonObject();
            for (Map.Entry<String, JsonElement> e : raw.getAsJsonObject().entrySet()) out.add(e.getKey(), filled(e.getValue(), values));
            return out;
        }
        if (raw.isJsonArray()) {
            JsonArray out = new JsonArray();
            for (JsonElement e : raw.getAsJsonArray()) out.add(filled(e, values));
            return out;
        }
        if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isString()) return raw;
        String text = raw.getAsString();
        if (text.length() > 2 && text.startsWith("{") && text.endsWith("}")) {
            String value = values.get(text.substring(1, text.length() - 1));
            if (value != null) {
                try {
                    return new JsonPrimitive(Long.parseLong(value));
                } catch (NumberFormatException ignored) {
                    return new JsonPrimitive(value);
                }
            }
        }
        return new JsonPrimitive(ContractText.fill(text, values));
    }

    private static @Nullable LivingEntity giver(MinecraftServer server, Rolled rolled) {
        if (rolled.giver == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(rolled.giver);
            if (entity instanceof LivingEntity living) return living;
        }
        return null;
    }

    static String locale(ServerPlayer player) {
        //? if >=1.21 {
        return player.clientInformation().language();
        //?} else {
        /*return player.getLanguage();
        *///?}
    }

    // ---- saving ----

    private static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> load(player));
    }

    public static void onLogout(ServerPlayer player) {
        STATES.remove(player.getUUID());
    }

    private static State load(Player player) {
        State state = new State();
        CompoundTag root = data(player).getCompound(KEY);
        for (Tag raw : root.getList("active", Tag.TAG_COMPOUND)) {
            Rolled rolled = read((CompoundTag) raw);
            if (rolled != null) state.active.add(rolled);
        }
        CompoundTag offers = root.getCompound("offers");
        for (String key : offers.getAllKeys()) {
            ResourceLocation pool = ResourceLocation.tryParse(key);
            if (pool == null) continue;
            CompoundTag entry = offers.getCompound(key);
            List<Rolled> list = new ArrayList<>();
            for (Tag raw : entry.getList("rolled", Tag.TAG_COMPOUND)) {
                Rolled rolled = read((CompoundTag) raw);
                if (rolled != null) list.add(rolled);
            }
            state.offers.put(pool, list);
            state.offeredDay.put(pool, entry.getLong("day"));
        }
        return state;
    }

    private static void save(ServerPlayer player) {
        State state = state(player);
        CompoundTag root = new CompoundTag();
        ListTag active = new ListTag();
        for (Rolled rolled : state.active) active.add(write(rolled));
        root.put("active", active);
        CompoundTag offers = new CompoundTag();
        for (Map.Entry<ResourceLocation, List<Rolled>> entry : state.offers.entrySet()) {
            CompoundTag tag = new CompoundTag();
            ListTag list = new ListTag();
            for (Rolled rolled : entry.getValue()) list.add(write(rolled));
            tag.put("rolled", list);
            tag.putLong("day", state.offeredDay.getOrDefault(entry.getKey(), 0L));
            offers.put(entry.getKey().toString(), tag);
        }
        root.put("offers", offers);
        CompoundTag data = data(player);
        data.put(KEY, root);
        store(player, data);
    }

    private static CompoundTag write(Rolled rolled) {
        CompoundTag tag = new CompoundTag();
        tag.putString("template", rolled.template.toString());
        CompoundTag values = new CompoundTag();
        rolled.values.forEach(values::putString);
        tag.put("values", values);
        if (rolled.giver != null) tag.putUUID("giver", rolled.giver);
        tag.putString("giver_name", rolled.giverName);
        tag.putLong("expires", rolled.expiresDay);
        tag.put("base", new LongArrayTag(rolled.base));
        tag.put("progress", new LongArrayTag(rolled.progress));
        tag.putBoolean("ready", rolled.ready);
        return tag;
    }

    private static @Nullable Rolled read(CompoundTag tag) {
        ResourceLocation template = ResourceLocation.tryParse(tag.getString("template"));
        if (template == null) return null;
        Map<String, String> values = new LinkedHashMap<>();
        CompoundTag raw = tag.getCompound("values");
        for (String key : raw.getAllKeys()) values.put(key, raw.getString(key));
        Rolled rolled = new Rolled(template, values);
        if (tag.hasUUID("giver")) rolled.giver = tag.getUUID("giver");
        rolled.giverName = tag.getString("giver_name");
        rolled.expiresDay = tag.getLong("expires");
        rolled.base = tag.getLongArray("base");
        rolled.progress = tag.getLongArray("progress");
        rolled.ready = tag.getBoolean("ready");
        return rolled;
    }

    private static CompoundTag data(Player player) {
        //? if neoforge {
        return player.getData(Townstead.PLAYER_ROOT_DATA);
        //?} else {
        /*return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        *///?}
    }

    private static void store(Player player, CompoundTag root) {
        //? if neoforge {
        player.setData(Townstead.PLAYER_ROOT_DATA, root);
        //?} else {
        /*player.getPersistentData().put(Player.PERSISTED_NBT_TAG, root);
        *///?}
    }

    // ---- loading ----

    public static final class Loader extends SimpleJsonResourceReloadListener {
        public Loader() { super(new Gson(), "contract"); }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, ContractDefinition> parsed = new HashMap<>();
            Map<ResourceLocation, String> errors = new HashMap<>();
            for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
                try {
                    JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey().toString());
                    if (!ModGate.allows(json)) continue;
                    parsed.put(entry.getKey(), ContractDefinition.parse(entry.getKey(), json));
                } catch (Exception exception) {
                    errors.put(entry.getKey(), exception.getMessage());
                    Townstead.LOGGER.warn("Contract {} rejected: {}", entry.getKey(), exception.getMessage());
                }
            }
            templates = Map.copyOf(parsed);
            rejected = Map.copyOf(errors);
            Townstead.LOGGER.info("Loaded {} contracts ({} rejected)", parsed.size(), errors.size());
        }
    }
}
