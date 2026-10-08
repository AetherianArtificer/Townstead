package com.aetherianartificer.townstead.work.job;

import com.aetherianartificer.townstead.work.martial.MartialJob;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class MartialJobDefsTest {

    @Test
    void bundledHunterJobsParse() throws Exception {
        MartialJob patrol = load("hunter_patrol").martial();
        assertEquals(MartialJob.Kind.PATROL, patrol.kind());
        assertTrue(patrol.arms().tag());
        assertEquals(MartialJob.Style.AUTO, patrol.arms().style());

        MartialJob sweep = load("hunter_sweep").martial();
        assertEquals(MartialJob.Kind.SWEEP, sweep.kind());
        assertEquals(48, sweep.sweep().reach());

        MartialJob drill = load("hunter_drill").martial();
        assertEquals(MartialJob.Kind.DRILL, drill.kind());
        assertTrue(drill.place().orderAltar());
    }

    @Test
    void holdAndDrillNeedAPlace() {
        JsonObject json = JsonParser.parseString("""
                { "task": "townstead_work:x", "type": "townstead:hold_post" }""").getAsJsonObject();
        assertNull(WorkJobDef.parse(ResourceLocation.tryParse("test:x"), json));
    }

    private static WorkJobDef load(String name) throws Exception {
        try (var in = MartialJobDefsTest.class.getResourceAsStream("/data/townstead/work_job/" + name + ".json")) {
            assertNotNull(in, name);
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            WorkJobDef def = WorkJobDef.parse(ResourceLocation.tryParse("townstead:" + name), json);
            assertNotNull(def, name);
            assertNotNull(def.martial(), name);
            return def;
        }
    }
}
