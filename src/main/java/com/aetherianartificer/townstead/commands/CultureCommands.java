package com.aetherianartificer.townstead.commands;

import com.aetherianartificer.townstead.culture.CultureDrift;
import com.aetherianartificer.townstead.culture.Cultures;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.politics.state.SettlementFoundingRecord;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import com.aetherianartificer.townstead.spirit.SpiritBaseline;
import com.aetherianartificer.townstead.spirit.SpiritRegistry;
import com.aetherianartificer.townstead.spirit.SpiritTotals;
import com.aetherianartificer.townstead.spirit.VillageSpiritAggregator;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import com.mojang.brigadier.CommandDispatcher;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Debug readouts for culture blends and drift: {@code /townstead culture villager} shows the nearest
 * villager's blend; {@code /townstead culture village} shows what the village is known for above the
 * spirit baseline, and where living there pulls its people.
 */
public final class CultureCommands {
    private CultureCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("townstead").then(Commands.literal("culture")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("villager").executes(c -> villager(c.getSource())))
                .then(Commands.literal("village").executes(c -> village(c.getSource())))));
    }

    private static int villager(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return fail(source, "Run this as a player near a villager.");
        VillagerEntityMCA villager = CommandTargets.lookedAtOrNearest(player, null);
        if (villager == null) return fail(source, "No villager nearby.");
        var life = TownsteadVillagers.get(villager).life();
        say(source, villager.getName().getString() + ": recorded culture " + blank(life.culture())
                + ", shown as " + blank(Cultures.rootOf(life.culture())));
        Map<String, Float> blend = life.cultureBlend();
        if (blend.isEmpty()) say(source, "  no culture yet");
        sorted(blend).forEach(e -> say(source, "  " + percent(e.getValue()) + "  " + e.getKey()));
        say(source, "  last drift on day " + life.cultureDriftDay());
        return 1;
    }

    private static int village(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Optional<Village> found;
        try {
            found = VillageManager.get(level).findNearestVillage(BlockPos.containing(source.getPosition()), Village.MERGE_MARGIN);
        } catch (Throwable error) {
            found = Optional.empty();
        }
        if (found.isEmpty()) return fail(source, "No village here.");
        Village village = found.get();
        say(source, village.getName() + " (village " + village.getId() + ")");

        SettlementFoundingRecord founding = PoliticalSavedData.get(level.getServer())
                .founding(new SettlementRef(level.dimension().location(), village.getId()));
        say(source, founding == null ? "  not founded yet"
                : "  founded as " + founding.profile() + ", culture " + blank(String.valueOf(founding.culture())));

        SpiritTotals spirit = VillageSpiritAggregator.totalsFor(village);
        say(source, "  spirit: " + spirit.total() + " points from " + spirit.contributingBuildings() + " building(s)");
        List<String> knownFor = new ArrayList<>();
        for (SpiritRegistry.Spirit axis : SpiritRegistry.ordered()) {
            double share = spirit.shareOf(axis.id());
            if (share <= 0) continue;
            double excess = SpiritBaseline.excess(spirit, axis.id());
            say(source, "    " + axis.id() + " " + percent(share) + " (ordinary " + percent(SpiritBaseline.of(axis.id())) + ")"
                    + (excess > 0 ? "  +" + percent(excess) : ""));
            if (excess > 0) knownFor.add(axis.id());
        }
        say(source, "  known for: " + (knownFor.isEmpty() ? "nothing beyond the ordinary" : String.join(", ", knownFor)));

        List<VillagerEntityMCA> residents = new ArrayList<>();
        village.getResidentsUUIDs().forEach(id -> {
            if (id != null && level.getEntity(id) instanceof VillagerEntityMCA resident) residents.add(resident);
        });
        Map<String, Float> pull = CultureDrift.homePull(level, village, residents, PoliticalSavedData.get(level.getServer()));
        say(source, "  living here pulls " + residents.size() + " loaded resident(s) toward:");
        if (pull.isEmpty()) say(source, "    nothing: no resident has a culture yet");
        float total = 0;
        for (float v : pull.values()) total += v;
        final float sum = total;
        sorted(pull).forEach(e -> say(source, "    " + percent(e.getValue() / sum) + "  " + e.getKey()));
        return 1;
    }

    private static List<Map.Entry<String, Float>> sorted(Map<String, Float> values) {
        List<Map.Entry<String, Float>> entries = new ArrayList<>(values.entrySet());
        entries.sort(Map.Entry.<String, Float>comparingByValue().reversed());
        return entries;
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.0f%%", value * 100);
    }

    private static String blank(String value) {
        return value == null || value.isBlank() || "null".equals(value) ? "(none)" : value;
    }

    private static void say(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
    }

    private static int fail(CommandSourceStack source, String text) {
        source.sendFailure(Component.literal(text));
        return 0;
    }
}
