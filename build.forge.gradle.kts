plugins {
    `java-library`
    id("net.minecraftforge.gradle") version "[6.0,6.2)"
}

stonecutter {
    const("neoforge", false)
    const("forge", true)
    replacements {
        // MCA 7.7+ (NeoForge) uses net.conczin.mca; MCA 7.6 (Forge) uses forge.net.mca
        string(true) { replace("net.conczin.mca.registry", "forge.net.mca") }
        string(true) { replace("net.conczin.mca", "forge.net.mca") }
        // Same replacement for JVM internal format (used in @At target descriptors)
        string(true) { replace("net/conczin/mca", "forge/net/mca") }
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
    maven("https://maven.architectury.dev/")
    maven("https://maven.blamejared.com")
}

dependencies {
    "minecraft"("net.minecraftforge:forge:1.20.1-47.3.0")
    compileOnly(files("${rootProject.projectDir}/libs/mca-forge-7.6.15+1.20.1-universal.jar"))
    compileOnly("dev.architectury:architectury-forge:9.2.14")
    compileOnly(fg.deobf("vazkii.patchouli:Patchouli:1.20.1-85-FORGE:api"))
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

layout.buildDirectory.set(file("${rootProject.projectDir}/.cache/townstead-build-1.20.1-forge"))

tasks.withType<ProcessResources> {
    val replaceProperties = mapOf("version" to project.version)
    inputs.properties(replaceProperties)
    filesMatching("META-INF/mods.toml") { expand(replaceProperties) }
    exclude("META-INF/neoforge.mods.toml")
    // Downgrade mixin compatibility level and remove NeoForge-only mixins for Forge
    filesMatching("townstead.mixins.json") {
        filter {
            it.replace("JAVA_21", "JAVA_17")
              .replace("\"LegacyImageButtonMixin\",\n", "")
              .replace("\"LegacyImageButtonMixin\",", "")
              .replace(",\n    \"LegacyImageButtonMixin\"", "")
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
    manifest {
        attributes("MixinConfigs" to "townstead.mixins.json")
    }
    finalizedBy("reobfJar")
}
