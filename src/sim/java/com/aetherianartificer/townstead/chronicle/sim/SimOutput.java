package com.aetherianartificer.townstead.chronicle.sim;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Shared shape for the terminal report, so every mode reads the same way. */
public final class SimOutput {

    private SimOutput() {}

    public static void heading(String text) {
        System.out.println(text);
        System.out.println("-".repeat(Math.min(text.length(), 96)));
    }

    public static void section(String title) {
        System.out.printf(Locale.ROOT, "%n%s:%n", title);
    }

    public static void warnings(List<String> warnings, Collection<ResourceLocation> stubbedTags) {
        boolean approximatedNames = !SimItemNames.available();
        if (warnings.isEmpty() && stubbedTags.isEmpty() && !approximatedNames) return;
        section("warnings");
        warnings.forEach(w -> System.out.println("  " + w));
        if (approximatedNames) {
            System.out.println("  item names approximated from ids: no language file on the classpath");
        }
        stubbedTags.forEach(tag -> System.out.printf(Locale.ROOT,
                "  %s stubbed with %d sample items (pass --items to model a pack)%n",
                tag, SampleItems.DEFAULT.size()));
    }

    public static void violations(List<String> violations) {
        if (violations.isEmpty()) return;
        section("check violations");
        violations.forEach(v -> System.out.println("  " + v));
    }
}
