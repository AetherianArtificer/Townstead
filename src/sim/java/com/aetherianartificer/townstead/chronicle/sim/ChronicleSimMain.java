package com.aetherianartificer.townstead.chronicle.sim;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Fabricates chronicles from the terminal, running the same generators the
 * server runs. No Minecraft launch, no save, no SQLite archive.
 *
 * <pre>
 *   gradlew chronicleSim --args="village --village 3 --years 180"
 *   gradlew chronicleSim --args="person --name Bram --age 47 --profession townstead:cook"
 *   gradlew chronicleSim --args="help"
 * </pre>
 */
public final class ChronicleSimMain {

    private ChronicleSimMain() {}

    public static void main(String[] rawArgs) throws IOException {
        SimArgs args = new SimArgs(rawArgs);
        if (args.mode.equals("help") || args.has("help")) {
            usage();
            return;
        }

        SimConditions.register();
        Path dataRoot = args.path("data", "src/main/resources/data");
        SimTemplates.Loaded loaded = SimTemplates.load(dataRoot);
        if (loaded.templates().isEmpty()) {
            System.out.println("No chronicle_event templates found under " + dataRoot.toAbsolutePath());
            loaded.warnings().forEach(w -> System.out.println("  warn: " + w));
            System.exit(2);
        }

        int status = switch (args.mode) {
            case "village" -> VillageSim.run(args, loaded);
            case "person" -> PersonSim.run(args, loaded);
            case "scale" -> ScaleSim.run(args, loaded);
            case "taps" -> TapsSim.run(args, loaded);
            default -> {
                System.out.println("Unknown mode '" + args.mode + "'");
                usage();
                yield 2;
            }
        };
        if (status != 0) System.exit(status);
    }

    private static void usage() {
        System.out.println("""
                Chronicles offline harness

                  village   fabricate a village's history (default)
                  person    fabricate one person's life
                  scale     every template's importance on one scale, and who keeps it
                  taps      which reportable game events have a template, and which do not
                  help      this text

                Shared options
                  --years N            how much history exists (default 200; in person mode, the
                                       village's age, defaulting to the subject's age plus 20)
                  --days-per-year N    calendar length (default 360)
                  --seed N             names and roster (default 1)
                  --village N          village id; in village mode this is the history's seed
                  --dim ID             dimension id (default minecraft:overworld)
                  --threshold F        override the scope's retention threshold
                  --data PATH          data-pack root (default src/main/resources/data)
                  --items SPEC         tag contents: "tag=id,id;tag=id"
                  --help               this text

                village mode
                  --residents N        living villagers roles may bind to (default 12)
                  --player-founded     the player founded it, so there is no deep past
                  --check              exit non-zero if an invariant is violated

                person mode
                  --kind KIND          villager (default) or player: a role written for a
                                       villager fits a player, because both are people
                  --name TEXT          display name (default: a fabricated one)
                  --profession ID      profession id, gating which roles fit (default townstead:cook)
                  --age N              age in years (default 40)
                  --others N           acquaintances available to bind (default 8)
                  --uuid UUID          pin the subject, since a life is seeded from its uuid

                scale mode
                  --magnitude F        the magnitude to price everything at (default 1.0)
                  --person-threshold F  what a person keeps, for comparison
                  --threshold F        what the village keeps

                Examples
                  village --village 3 --years 180 --residents 14 --check
                  person --name "Bram Colefield" --age 52 --profession minecraft:farmer
                  person --threshold 1.0        keep almost everything, to see the whole candidate stream
                """);
    }
}
