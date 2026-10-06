package com.aetherianartificer.townstead.replace.behavior;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** The behavior types a profile can name. Compat code may register more. */
public final class BehaviorTypes {
    /**
     * Attacks what it regards as hostile, within {@code radius}; {@code shun_daylight} keeps it out of the
     * sun; {@code monsters} also takes on hostile mobs (never creepers).
     */
    public static final ResourceLocation HUNT = ResourceLocation.tryParse("pheno:hunt");
    /** Feeds on the people it hunts, not only on those its feeding rules allow. */
    public static final ResourceLocation BITE = ResourceLocation.tryParse("pheno:bite");

    public record Hunt(double radius, boolean shunDaylight, boolean monsters) implements BehaviorProfile.Behavior {}
    public record Bite() implements BehaviorProfile.Behavior {}

    @FunctionalInterface
    public interface Parser {
        BehaviorProfile.Behavior parse(JsonObject options);
    }

    private static final Map<ResourceLocation, Parser> PARSERS = new ConcurrentHashMap<>();

    static {
        register(HUNT, options -> new Hunt(GsonHelper.getAsDouble(options, "radius", 16),
                GsonHelper.getAsBoolean(options, "shun_daylight", false),
                GsonHelper.getAsBoolean(options, "monsters", false)));
        register(BITE, options -> new Bite());
    }

    private BehaviorTypes() {}

    public static void register(ResourceLocation type, Parser parser) {
        PARSERS.put(type, parser);
    }

    static @Nullable Parser parser(ResourceLocation type) {
        return PARSERS.get(type);
    }
}
