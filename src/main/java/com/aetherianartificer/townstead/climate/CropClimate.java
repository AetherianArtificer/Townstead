package com.aetherianartificer.townstead.climate;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.temperature.RoomHeat;
import com.aetherianartificer.townstead.temperature.TemperatureSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether a crop can grow at a position right now. A season mod's "no" always wins; Townstead never
 * makes an out-of-season crop grow. Then the crop's climate class is checked against the day's
 * mean air (room air inside a room, so a heated glass room works as a greenhouse).
 */
public final class CropClimate {
    public enum Verdict { GROWS, OUT_OF_SEASON, TOO_COLD, TOO_HOT }

    private static final Map<String, TagKey<Block>> CLASS_TAGS = new ConcurrentHashMap<>();
    private static final String[] SERENE = {"spring", "summer", "autumn", "winter", "year_round"};
    private static final Map<String, TagKey<Block>> SERENE_TAGS = new ConcurrentHashMap<>();
    private static volatile boolean sereneProbed;
    private static Method sereneFertile;

    private CropClimate() {}

    public static Verdict verdict(ServerLevel level, BlockPos pos, BlockState crop) {
        return verdict(level, pos, crop, 0);
    }

    /** As {@link #verdict(ServerLevel, BlockPos, BlockState)}, with the temperature {@code daysAhead}. */
    public static Verdict verdict(ServerLevel level, BlockPos pos, BlockState crop, int daysAhead) {
        if (!seasonModAllows(level, pos, crop.getBlock())) return Verdict.OUT_OF_SEASON;
        return temperatureVerdict(level, pos, crop, daysAhead);
    }

    /** Only the temperature check, for the growth hook; the season mods gate their own growth. */
    public static Verdict temperatureVerdict(ServerLevel level, BlockPos pos, BlockState crop, int daysAhead) {
        // Without temperature, crops grow as vanilla and any season mod say.
        if (!com.aetherianartificer.townstead.TownsteadConfig.temperatureActive()) return Verdict.GROWS;
        var range = TemperatureSettings.get().cropClasses().get(cropClass(crop));
        if (range == null) return Verdict.GROWS;
        return judge(growingCelsius(level, pos, daysAhead), range.min(), range.max());
    }

    static Verdict judge(float celsius, float min, float max) {
        if (celsius < min) return Verdict.TOO_COLD;
        if (celsius > max) return Verdict.TOO_HOT;
        return Verdict.GROWS;
    }

    /** The day's mean air at the crop: room air in a room, else the climate without day/night or weather. */
    public static float growingCelsius(ServerLevel level, BlockPos pos, int daysAhead) {
        // Only a covered crop can be in a room; an open field never starts a room scan.
        if (level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) > pos.getY() + 1) {
            var room = RoomHeat.at(level, pos);
            if (room.isPresent()) return (float) room.getAsDouble();
        }
        return ClimateModel.dailyMean(level, pos, daysAhead);
    }

    /**
     * The crop's class: a {@code #townstead:crops/climate/<class>} tag, else one taken from Serene's
     * season tags, else the default class.
     */
    public static String cropClass(BlockState crop) {
        TemperatureSettings s = TemperatureSettings.get();
        for (String name : s.cropClasses().keySet()) {
            if (crop.is(CLASS_TAGS.computeIfAbsent(name, n -> tag("townstead:crops/climate/" + n)))) return name;
        }
        boolean[] seasons = new boolean[SERENE.length];
        for (int i = 0; i < SERENE.length; i++) {
            seasons[i] = crop.is(SERENE_TAGS.computeIfAbsent(SERENE[i], n -> tag("sereneseasons:" + n + "_crops")));
        }
        String fromSeasons = classFromSeasons(seasons[0], seasons[1], seasons[2], seasons[3], seasons[4]);
        return fromSeasons != null && s.cropClasses().containsKey(fromSeasons) ? fromSeasons : s.defaultCropClass();
    }

    /** Winter or all-year crops are hardy, summer-only crops warm-season, spring or autumn crops cool-season. */
    @Nullable
    static String classFromSeasons(boolean spring, boolean summer, boolean autumn, boolean winter, boolean yearRound) {
        if (winter || yearRound) return "hardy";
        if (summer && !spring && !autumn) return "warm_season";
        if ((spring || autumn) && !summer) return "cool_season";
        return null;
    }

    /** Serene's own fertility check, with its greenhouse glass rule; true without Serene. */
    static boolean seasonModAllows(ServerLevel level, BlockPos pos, Block block) {
        if (!ModCompat.isLoaded("sereneseasons")) return true;
        Method fertile = sereneFertile();
        if (fertile == null) return true;
        try {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (Boolean.TRUE.equals(fertile.invoke(null, id.toString(), level, pos))) return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return true;
        }
        TagKey<Block> glass = SERENE_TAGS.computeIfAbsent("greenhouse_glass", n -> tag("sereneseasons:greenhouse_glass"));
        BlockPos.MutableBlockPos cursor = pos.mutable();
        for (int i = 0; i < 16 && cursor.getY() < level.getMaxBuildHeight() - 1; i++) {
            cursor.move(0, 1, 0);
            if (level.getBlockState(cursor).is(glass)) return true;
        }
        return false;
    }

    private static synchronized Method sereneFertile() {
        if (!sereneProbed) {
            sereneProbed = true;
            try {
                sereneFertile = Class.forName("sereneseasons.init.ModFertility")
                        .getMethod("isCropFertile", String.class, net.minecraft.world.level.Level.class, BlockPos.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                sereneFertile = null;
            }
        }
        return sereneFertile;
    }

    private static TagKey<Block> tag(String id) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.tryParse(id));
    }
}
