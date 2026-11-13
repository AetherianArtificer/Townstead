package com.aetherianartificer.townstead.skillcheck;

import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.profession.def.SkillDef;
import com.aetherianartificer.townstead.profession.def.SkillDefs;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/** Every skill file of every loaded career must register, with its power and every companion parsed. */
@GameTestHolder(SkillCheck.ID)
@PrefixGameTestTemplate(false)
public final class SkillLoadTests {

    private static final java.util.Set<String> OWN = java.util.Set.of(
            "minecraft", "townstead", "pheno", "c", "forge", "townstead_work", "townstead_state");

    /** A power that names a thing from another mod (an effect, a block, a type) cannot be judged without it. */
    private static boolean foreign(com.google.gson.JsonElement node) {
        if (node.isJsonArray()) {
            for (var child : node.getAsJsonArray()) if (foreign(child)) return true;
            return false;
        }
        if (node.isJsonPrimitive() && node.getAsJsonPrimitive().isString()) {
            String value = node.getAsString();
            return value.matches("[a-z0-9_]+:[a-z0-9_./]+") && !OWN.contains(value.substring(0, value.indexOf(':')));
        }
        if (!node.isJsonObject()) return false;
        for (var entry : node.getAsJsonObject().entrySet()) {
            if (foreign(entry.getValue())) return true;
        }
        return false;
    }

    private static boolean parses(JsonObject power) {
        if (foreign(power)) return true;
        var type = com.aetherianartificer.townstead.root.gene.GeneTypes.get(
                net.minecraft.util.GsonHelper.getAsString(power, "type", ""));
        return type.isPresent() && type.get().parse(power, java.util.Map.of()) != null;
    }

    @GameTest(template = "empty")
    public static void everySkillLoadsWithItsPower(GameTestHelper h) throws Exception {
        List<String> broken = new ArrayList<>();
        // A path that needs a mod registers none of its skills; such a path is not installed here.
        java.util.Set<String> livePaths = new java.util.HashSet<>();
        for (ResourceLocation known : SkillDefs.all().keySet()) {
            int cut = known.getPath().lastIndexOf('/');
            if (cut > 0) livePaths.add(known.getNamespace() + ":" + known.getPath().substring(0, cut));
        }
        int checked = 0;
        int parsedOnly = 0;
        var files = h.getLevel().getServer().getResourceManager()
                .listResources("profession", id -> id.getPath().endsWith(".json") && id.getPath().contains("/skill/"));
        for (var entry : files.entrySet()) {
            // profession/<career>/skill/<id>.json or profession/<career>/path/<path>/skill/<id>.json
            String[] parts = entry.getKey().getPath().split("/");
            String name = parts[parts.length - 1].replace(".json", "");
            String career = parts[1];
            String local = parts.length == 6 ? career + "/" + parts[3] + "/" + name : career + "/" + name;
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(entry.getKey().getNamespace(), local);
            JsonObject json;
            try (Reader reader = entry.getValue().openAsReader()) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            }
            // A career or path that needs a mod that is not installed here registers nothing.
            // Its powers are still parsed, so a broken file is caught without the mod.
            if (ProfessionDefs.byId(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), career)) == null
                    || !livePaths.contains(id.getNamespace() + ":" + local.substring(0, local.lastIndexOf('/')))) {
                parsedOnly++;
                if (json.has("power") && !parses(json.getAsJsonObject("power"))) broken.add(id + " has a power that does not parse");
                if (json.has("companions")) {
                    for (var companion : json.getAsJsonObject("companions").entrySet()) {
                        if (!parses(companion.getValue().getAsJsonObject())) {
                            broken.add(id + " has a companion that does not parse: " + companion.getKey());
                        }
                    }
                }
                continue;
            }
            checked++;
            SkillDef def = SkillDefs.byId(id);
            if (def == null) {
                broken.add(id + " did not register");
                continue;
            }
            if (json.has("power") && def.power() == null) broken.add(id + " lost its power");
            int companions = json.has("companions") ? json.getAsJsonObject("companions").size() : 0;
            if (def.companions().size() != companions) {
                broken.add(id + " kept " + def.companions().size() + " of " + companions + " companions");
            }
        }
        var paths = h.getLevel().getServer().getResourceManager()
                .listResources("profession", id -> id.getPath().endsWith("/path.json"));
        for (var entry : paths.entrySet()) {
            JsonObject json;
            try (Reader reader = entry.getValue().openAsReader()) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            }
            if (!json.has("powers")) continue;
            int index = 0;
            for (var power : json.getAsJsonArray("powers")) {
                if (!parses(power.getAsJsonObject())) broken.add(entry.getKey() + " power " + index + " does not parse");
                index++;
            }
        }
        h.assertTrue(checked + parsedOnly > 900, "Only " + (checked + parsedOnly) + " skill files were found");
        h.assertTrue(broken.isEmpty(), broken.size() + " skills are broken: " + String.join("; ", broken));
        h.succeed();
    }
}
