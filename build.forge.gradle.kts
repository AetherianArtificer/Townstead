plugins {
    `java-library`
    id("net.minecraftforge.gradle") version "[6.0,6.2)"
}

val legacyMcaNamespace = project.name.endsWith("-legacy")

stonecutter {
    const("neoforge", false)
    const("forge", true)
    if (legacyMcaNamespace) {
        replacements {
            // The released Forge MCA line uses forge.net.mca, while the 1.20.1
            // backport-improvements line migrated those same classes to
            // net.conczin.mca. Compile a distinct artifact for each namespace.
            string(true) { replace("net.conczin.mca.registry", "forge.net.mca") }
            string(true) { replace("net.conczin.mca", "forge.net.mca") }
            string(true) { replace("net/conczin/mca", "forge/net/mca") }
        }
    }
}

version = "${property("mod_version")}+${stonecutter.current.version}"
group = property("mod_group") as String
base.archivesName.set(if (legacyMcaNamespace) "townstead-mca-legacy" else "townstead-mca-modern")

java.toolchain.languageVersion.set(JavaLanguageVersion.of(17))

minecraft {
    mappings("official", "1.20.1")

    runs {
        create("client") {
            workingDirectory(project.file("run"))
            property("forge.logging.markers", "REGISTRIES")
            property("forge.logging.console.level", "debug")
            mods {
                create(property("mod_id") as String) {
                    source(sourceSets.main.get())
                }
            }
        }
        create("server") {
            workingDirectory(project.file("run"))
            property("forge.logging.markers", "REGISTRIES")
            property("forge.logging.console.level", "debug")
            mods {
                create(property("mod_id") as String) {
                    source(sourceSets.main.get())
                }
            }
        }
    }
}

repositories {
    maven("https://maven.architectury.dev/")
    maven("https://maven.blamejared.com")
    mavenCentral()
}

jarJar.enable()

dependencies {
    "minecraft"("net.minecraftforge:forge:1.20.1-47.3.0")
    compileOnly(files(
        if (legacyMcaNamespace) {
            "${rootProject.projectDir}/libs/mca-forge-legacy-7.7.0-beta.2+1.20.1-universal.jar"
        } else {
            "${rootProject.projectDir}/libs/mca-forge-7.7.0-beta.2+1.20.1.jar"
        }
    ))
    compileOnly(annotationProcessor("io.github.llamalad7:mixinextras-common:${property("mixin_extras_version")}")!!)
    implementation(jarJar("io.github.llamalad7:mixinextras-forge:${property("mixin_extras_version")}")) {
        jarJar.ranged(this, "[0.5.4,0.6)")
    }
    // Pure-Java Chronicle archive backend. minecraftLibrary supplies dev runs;
    // jarJar embeds the small MVStore artifact in distributions.
    "minecraftLibrary"("com.h2database:h2-mvstore:${property("h2_mvstore_version")}")
    "jarJar"("com.h2database:h2-mvstore:[${property("h2_mvstore_version")},2.5.0)") {
        jarJar.pin(this, property("h2_mvstore_version") as String)
    }
    compileOnly("dev.architectury:architectury-forge:9.2.14")
    compileOnly(fg.deobf("vazkii.patchouli:Patchouli:1.20.1-85-FORGE:api"))
    // JEI plugin API (runtime optional; the plugin class is only loaded by JEI's scan)
    compileOnly(fg.deobf("mezz.jei:jei-1.20.1-common-api:15.20.0.135"))
    compileOnly(fg.deobf("mezz.jei:jei-1.20.1-forge-api:15.20.0.135"))
    // No Sponge Mixin annotation processor: this build ships no refmap (targets
    // are hand-written SRG with remap=false). MixinExtras' own processor is kept
    // only because its supported ForgeGradle setup requires it.
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    // Pheno unit tests touch Minecraft types; surface the main compile classpath to tests.
    testImplementation(files(sourceSets.main.get().compileClasspath))
}

// Offline Chronicles harness; see the neoforge script for why it is not in src/test.
val sim by sourceSets.creating {
    java.setSrcDirs(listOf(rootProject.file("src/sim/java")))
    resources.setSrcDirs(emptyList<File>())
}

dependencies {
    "simImplementation"(files(sourceSets.main.get().compileClasspath))
    "simImplementation"(sourceSets.main.get().output)
}

// Only the active version registers it; see the neoforge script.
if (stonecutter.current.isActive) {
    tasks.register<JavaExec>("chronicleSim") {
        group = "verification"
        description = "Fabricate chronicles offline and print them (no Minecraft launch)."
        mainClass.set("com.aetherianartificer.townstead.chronicle.sim.ChronicleSimMain")
        classpath = sim.runtimeClasspath
        workingDir = rootProject.projectDir
    }
}

layout.buildDirectory.set(file(
    "${rootProject.projectDir}/.cache/townstead-build-1.20.1-forge" +
        if (legacyMcaNamespace) "-legacy" else "-modern"
))

tasks.withType<ProcessResources> {
    val replaceProperties = mapOf("version" to project.version)
    inputs.properties(replaceProperties)
    filesMatching("META-INF/mods.toml") { expand(replaceProperties) }
    exclude("META-INF/neoforge.mods.toml")
    // Downgrade the mixin compatibility level for Java 17. Icon mixins remain in
    // the config; TownsteadMixinPlugin gates optional MCA targets at runtime.
    filesMatching("townstead.mixins.json") {
        filter {
            it.replace("JAVA_21", "JAVA_17")
        }
    }
    // Use correct pack format for 1.20.1
    filesMatching("pack.mcmeta") {
        filter { it.replace("\"pack_format\": 34", "\"pack_format\": 15") }
    }
    // 1.20.1 uses plural tag/recipe/loot_table directories
    // Move compat building types to a non-loading location for conditional runtime loading
    eachFile {
        if (path.contains("/tags/block/")) {
            path = path.replace("/tags/block/", "/tags/blocks/")
        }
        if (path.contains("/tags/item/")) {
            path = path.replace("/tags/item/", "/tags/items/")
        }
        if (path.contains("/recipe/")) {
            path = path.replace("/recipe/", "/recipes/")
        }
        if (path.contains("/loot_table/")) {
            path = path.replace("/loot_table/", "/loot_tables/")
        }
        if (path.startsWith("data/mca/building_types/compat/")) {
            path = path.replace("data/mca/", "townstead_compat/")
        }
    }
    // 1.20.1 recipe format uses "item" instead of "id" in results
    filesMatching("data/*/recipe/*.json") {
        filter { it.replace("\"id\":", "\"item\":") }
    }
    // 1.20.1 Patchouli: book id is stored as NBT on the result item, not a 1.21 data component
    filesMatching("data/townstead/recipe/townstead_guide.json") {
        filter {
            it.replace(
                Regex("""\"components\"\s*:\s*\{\s*\"patchouli:book\"\s*:\s*\"([^\"]+)\"\s*\}\s*,"""),
                "\"nbt\": \"{\\\\\"patchouli:book\\\\\":\\\\\"$1\\\\\"}\","
            )
        }
    }
    // 1.20.1 recipe conditions use "conditions" key and "forge:mod_loaded" type
    filesMatching("data/*/recipe/*.json") {
        filter {
            it.replace("\"neoforge:conditions\"", "\"conditions\"")
              .replace("\"neoforge:mod_loaded\"", "\"forge:mod_loaded\"")
        }
    }
    doLast {
        val compatRoot = destinationDir.resolve("townstead_compat/building_types/compat")
        val index = destinationDir.resolve("townstead_compat/index.txt")
        val entries = if (compatRoot.isDirectory) compatRoot.walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .map { it.relativeTo(destinationDir.resolve("townstead_compat")).invariantSeparatorsPath }
            .sorted()
            .toList() else emptyList()
        index.parentFile.mkdirs()
        index.writeText(entries.joinToString("\n", postfix = if (entries.isEmpty()) "" else "\n"))
    }
}

tasks.withType<JavaCompile> { options.encoding = "UTF-8" }
tasks.withType<Test> { useJUnitPlatform() }

tasks.named<Jar>("jar") {
    // The plain jar remains available for diagnostics; distribution uses the
    // unclassified jarJar artifact configured below.
    archiveClassifier.set("slim")
    manifest {
        attributes(
            "MixinConfigs" to "townstead.mixins.json",
            "Townstead-MCA-Namespace" to if (legacyMcaNamespace) "forge.net.mca" else "net.conczin.mca"
        )
    }
    finalizedBy("reobfJar")
}

tasks.named<Jar>("jarJar") {
    archiveClassifier.set("")
    manifest {
        attributes(
            "MixinConfigs" to "townstead.mixins.json",
            "Townstead-MCA-Namespace" to if (legacyMcaNamespace) "forge.net.mca" else "net.conczin.mca"
        )
    }
    finalizedBy("reobfJarJar")
}
