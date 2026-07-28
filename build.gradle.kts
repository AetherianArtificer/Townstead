plugins {
    `java-library`
    id("net.neoforged.moddev") version "2.0.28-beta"
}

stonecutter {
    const("neoforge", true)
    const("forge", false)
}

version = "${property("mod_version")}+${stonecutter.current.version}"
group = property("mod_group") as String
base.archivesName.set("townstead")

java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))

neoForge {
    version.set(property("neoforge_version") as String)

    runs {
        register("client") { client() }
        register("server") { server() }
    }

    mods {
        register(property("mod_id") as String) {
            sourceSet(sourceSets.main.get())
        }
    }
}

repositories {
    maven { url = uri("https://maven.blamejared.com") }
    // Iron's Spells publishes an API-only artifact for addons. Their licence is All Rights
    // Reserved with an explicit carve-out: "Write your own code that uses this code as a
    // dependency (such as addons or datapacks)." Nothing of theirs ships in our jar.
    maven { url = uri("https://code.redspace.io/releases") }
    mavenCentral()
}

dependencies {
    // MCA's floor-system rebuild moved building-icon drawing out of BlueprintScreen
    // (removed drawBuildingIcon) into WidgetUtils/BlueprintMapRenderer. We compile against
    // that newer API; runtime support for the older 7.7.x API is kept via version-gated
    // icon mixins (see TownsteadMixinPlugin).
    compileOnly(files("${rootProject.projectDir}/libs/mca-neoforge-1.21.1-floor-systemv2-SNAPSHOT.jar"))
    compileOnly("vazkii.patchouli:Patchouli:1.21.1-93-NEOFORGE") { isTransitive = false }
    // JEI plugin API (runtime optional; the plugin class is only loaded by JEI's scan)
    compileOnly("mezz.jei:jei-1.21.1-common-api:19.39.0.370")
    compileOnly("mezz.jei:jei-1.21.1-neoforge-api:19.39.0.370")
    // Iron's Spells API, for reading what is actually in a quick-cast slot. compileOnly and
    // non-transitive: the bridge is guarded by ModList, so nothing here is required at runtime.
    compileOnly("io.redspace:irons_spellbooks:1.21.1-3.16.2:api") { isTransitive = false }
    // Chronicle archive backend, embedded into the mod jar via jarJar
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
    jarJar("org.xerial:sqlite-jdbc:3.46.1.3")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.xerial:sqlite-jdbc:3.46.1.3")
    // Pheno unit tests touch Minecraft types (ResourceLocation, GsonHelper); moddev keeps MC as a
    // non-transitive compileOnly, so surface the main compile classpath to the test classpath.
    testImplementation(files(sourceSets.main.get().compileClasspath))
}

layout.buildDirectory.set(file("${rootProject.projectDir}/.cache/townstead-build-1.21.1-neoforge"))

tasks.withType<ProcessResources> {
    val replaceProperties = mapOf("version" to project.version)
    inputs.properties(replaceProperties)
    filesMatching("META-INF/neoforge.mods.toml") { expand(replaceProperties) }
    exclude("META-INF/mods.toml")
    // Move compat building types to a non-loading location for conditional runtime loading
    eachFile {
        if (path.startsWith("data/mca/building_types/compat/")) {
            path = path.replace("data/mca/", "townstead_compat/")
        }
    }
}

tasks.withType<JavaCompile> { options.encoding = "UTF-8" }
tasks.withType<Test> { useJUnitPlatform() }
