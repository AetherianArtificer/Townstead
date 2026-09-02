package com.aetherianartificer.townstead.client.gui.career;

import com.aetherianartificer.townstead.client.gui.calendar.StampCatalog;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** The small, contextual set of seals offered by the career record's stamp case. */
final class CareerStampCatalog {

    record Entry(String textureId, ResourceLocation texture, String name,
                 String sourcePack, String markLabel) {
        boolean hasArt() { return texture != null; }
    }

    private CareerStampCatalog() {}

    static List<Entry> list(String careerName) {
        List<Entry> result = new ArrayList<>();
        result.add(new Entry("", null,
                Component.translatable("townstead.career.screen.stamp.archives").getString(),
                "", ""));

        String cleanCareer = careerName == null ? "" : careerName.trim();
        if (!cleanCareer.isEmpty()) {
            String guild = Component.translatable(
                    "townstead.career.screen.stamp.guild", cleanCareer).getString();
            result.add(new Entry("", null, guild, "", guild));
        }

        Set<String> seen = new LinkedHashSet<>();
        for (StampCatalog.Entry stamp : StampCatalog.list()) {
            String path = stamp.texture().getPath().toLowerCase(Locale.ROOT);
            if (!path.startsWith("textures/stamps/career/") || !seen.add(stamp.textureId())) {
                continue;
            }
            result.add(new Entry(stamp.textureId(), stamp.texture(), stamp.displayName(),
                    stamp.sourcePack(), stamp.displayName()));
        }
        return List.copyOf(result);
    }
}
