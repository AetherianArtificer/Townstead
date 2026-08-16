plugins {
    `java-library`
    id("net.minecraftforge.gradle") version "[6.0,6.2)"
}

val legacyMcaNamespace = project.name.endsWith("-legacy")
val mcaNamespace = if (legacyMcaNamespace) "forge.net.mca" else "forge.net.conczin.mca"

stonecutter {
    const("neoforge", false)
    const("forge", true)
    replacements {
        // Both 1.20.1 lines ship Architectury-relocated jars, so MCA lives under a
        // forge.* prefix at runtime. The released Forge line also predates the
        // net.mca -> net.conczin.mca move, while the backport-improvements line
        // carries it. Compile a distinct artifact for each namespace.
        if (legacyMcaNamespace) {
            string(true) { replace("net.conczin.mca.registry", "forge.net.mca") }
            string(true) { replace("net.conczin.mca", "forge.net.mca") }
            string(true) { replace("net/conczin/mca", "forge/net/mca") }
        } else {
            // No .registry rule here: the three affected imports already pick the
            // flat 1.20.1 form via version directives, and a second rule producing
            // this same target would prefix those references twice.
            string(true) { replace("net.conczin.mca", "forge.net.conczin.mca") }
            string(true) { replace("net/conczin/mca", "forge/net/conczin/mca") }
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
    // Both must be relocated (universal) jars: the sources compile against the
    // forge.* namespace the shipped jars actually carry at runtime.
    compileOnly(files(
        if (legacyMcaNamespace) {
            "${rootProject.projectDir}/libs/mca-forge-legacy-7.7.0-beta.2+1.20.1-universal.jar"
        } else {
            "${rootProject.projectDir}/libs/mca-forge-7.7.1-alpha.1+1.20.1-universal.jar"
        }
    ))
    compileOnly(annotationProcessor("io.github.llamalad7:mixinextras-common:${property("mixin_extras_version")}")!!)
    implementation(jarJar("io.github.llamalad7:mixinextras-forge:${property("mixin_extras_version")}")) {
        jarJar.ranged(this, "[0.5.4,0.6)")
    }
    compileOnly("dev.architectury:architectury-forge:9.2.14")
    compileOnly(fg.deobf("vazkii.patchouli:Patchouli:1.20.1-85-FORGE:api"))
    // No Sponge Mixin annotation processor: this build ships no refmap (targets
    // are hand-written SRG with remap=false). MixinExtras' own processor is kept
    // only because its supported ForgeGradle setup requires it.
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    // Pheno unit tests touch Minecraft types; surface the main compile classpath to tests.
    testImplementation(files(sourceSets.main.get().compileClasspath))
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
