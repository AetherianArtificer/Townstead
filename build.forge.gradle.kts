plugins {
    `java-library`
    id("net.minecraftforge.gradle") version "[6.0,6.2)"
}

val mcaNamespace = "forge.net.conczin.mca"
val mcaArtifact = "minecraft-comes-alive"
val mcaVersion = "7.7.1-alpha.3+1.20.1-universal"

stonecutter {
    const("neoforge", false)
    const("forge", true)
    replacements {
        // MCA's current 1.20.1 universal Forge jar is Architectury-relocated at runtime.
        // Source stays on MCA's current net.conczin namespace and Stonecutter applies the
        // relocation required by that production artifact.
        string(true) { replace("net.conczin.mca", "forge.net.conczin.mca") }
        string(true) { replace("net/conczin/mca", "forge/net/conczin/mca") }
    }
}

version = "${property("mod_version")}+${stonecutter.current.version}"
group = property("mod_group") as String
base.archivesName.set("townstead")

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
    flatDir { dirs(rootProject.file("libs")) }
    maven("https://maven.architectury.dev/")
    maven("https://maven.blamejared.com")
    // Curios API, for the optional wearables integration (villager Curios slots and screen).
    maven("https://www.cursemaven.com") { content { includeGroup("curse.maven") } }
    mavenCentral()
}

jarJar.enable()

dependencies {
    "minecraft"("net.minecraftforge:forge:1.20.1-47.3.0")
    // Both must be relocated (universal) jars: the sources compile against the
    // forge.* namespace the shipped jars actually carry at runtime.
    // Resolve through flatDir so ForgeGradle can remap the universal production jar for the
    // named development/test runtime. A files(...) dependency cannot be deobfuscated.
    compileOnly(fg.deobf("townstead.libs:$mcaArtifact:$mcaVersion"))
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
    // Curios (runtime optional): everything Curios-shaped lives in compat.curios behind ModCompat.
    compileOnly(fg.deobf("curse.maven:curios-309927:6418456"))
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
    "${rootProject.projectDir}/.cache/townstead-build-1.20.1-forge"
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
        if (name != "processResources") return@doLast
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
            "Townstead-MCA-Namespace" to mcaNamespace
        )
    }
    finalizedBy("reobfJar")
}

tasks.named<Jar>("jarJar") {
    archiveClassifier.set("")
    manifest {
        attributes(
            "MixinConfigs" to "townstead.mixins.json",
            "Townstead-MCA-Namespace" to mcaNamespace
        )
    }
    finalizedBy("reobfJarJar")
}
