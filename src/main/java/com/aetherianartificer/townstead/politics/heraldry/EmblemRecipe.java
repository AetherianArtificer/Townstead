package com.aetherianartificer.townstead.politics.heraldry;

import net.minecraft.resources.ResourceLocation;

/** Bounded, versioned recipe; patterns resolve through Minecraft's synchronized registry. */
public record EmblemRecipe(int field, String division, int divisionColor, String symbol, int symbolColor) {
    public static final EmblemRecipe DEFAULT = new EmblemRecipe(9, "minecraft:stripe_center", 4, "minecraft:rhombus", 0);
    public EmblemRecipe {
        if (field < 0 || field > 15 || divisionColor < 0 || divisionColor > 15 || symbolColor < 0 || symbolColor > 15)
            throw new IllegalArgumentException("Invalid dye");
        for (String id : new String[]{division, symbol})
            if (id == null || id.length() > 256 || (!id.isEmpty() && ResourceLocation.tryParse(id) == null))
                throw new IllegalArgumentException("Invalid pattern");
    }
    public String encode() { return "1;" + field + ";" + division + ";" + divisionColor + ";" + symbol + ";" + symbolColor; }
    public static EmblemRecipe decode(String raw) {
        String[] parts = raw.split(";", -1);
        if (parts.length != 6 || !parts[0].equals("1")) throw new IllegalArgumentException("Unsupported emblem recipe");
        return new EmblemRecipe(Integer.parseInt(parts[1]), parts[2], Integer.parseInt(parts[3]), parts[4], Integer.parseInt(parts[5]));
    }
    public static EmblemRecipe safe(String raw) {
        try { return decode(raw); } catch (RuntimeException ignored) { return DEFAULT; }
    }
}
