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
    // Keep this jar aligned with the MCA jar deployed in the test instance's mods/
    // folder: signature drift between the compile jar and runtime jar compiles
    // cleanly but throws NoSuchMethodError in-game. APIs that only exist on other
    // MCA builds are handled via runtime-gated mixins (see TownsteadMixinPlugin).
    compileOnly(files("${rootProject.projectDir}/libs/mca-neoforge-7.7.36-beta.3+1.21.1.jar"))
    implementation(jarJar("io.github.llamalad7:mixinextras-neoforge:${property("mixin_extras_version")}")!!)
    compileOnly("vazkii.patchouli:Patchouli:1.21.1-93-NEOFORGE") { isTransitive = false }
    // JEI plugin API (runtime optional; the plugin class is only loaded by JEI's scan)
    compileOnly("mezz.jei:jei-1.21.1-common-api:19.39.0.370")
    compileOnly("mezz.jei:jei-1.21.1-neoforge-api:19.39.0.370")
    // Iron's Spells API, for reading what is actually in a quick-cast slot. compileOnly and
    // non-transitive: the bridge is guarded by ModList, so nothing here is required at runtime.
    compileOnly("io.redspace:irons_spellbooks:1.21.1-3.16.2:api") { isTransitive = false }
    // Pure-Java Chronicle archive backend, embedded without SQLite's native binaries.
    implementation(jarJar("com.h2database:h2-mvstore:${property("h2_mvstore_version")}")!!)
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    // Pheno unit tests touch Minecraft types (ResourceLocation, GsonHelper); moddev keeps MC as a
    // non-transitive compileOnly, so surface the main compile classpath to the test classpath.
    testImplementation(files(sourceSets.main.get().compileClasspath))
}

// Offline Chronicles harness. Its own source set, kept off the test source set because
// that one shadows CompoundTag/BlockPos with stubs; the harness needs the real classes.
val sim by sourceSets.creating {
    java.setSrcDirs(listOf(rootProject.file("src/sim/java")))
    resources.setSrcDirs(emptyList<File>())
}

dependencies {
    "simImplementation"(files(sourceSets.main.get().compileClasspath))
    "simImplementation"(sourceSets.main.get().output)
    // Minecraft's own assets carry en_us.json, so the harness can print real item
    // names instead of guessing from ids. Absent on a clean checkout until moddev
    // has run, and the harness says so when it falls back.
    "simRuntimeOnly"(fileTree(layout.buildDirectory.dir("moddev/artifacts")) {
        include("*client-extra*.jar")
    })
}

// Only the active version registers it, so an unqualified `gradlew chronicleSim` runs
// once instead of once per Stonecutter version.
if (stonecutter.current.isActive) {
    tasks.register<JavaExec>("chronicleSim") {
        group = "verification"
        description = "Fabricate chronicles offline and print them (no Minecraft launch)."
        mainClass.set("com.aetherianartificer.townstead.chronicle.sim.ChronicleSimMain")
        classpath = sim.runtimeClasspath
        workingDir = rootProject.projectDir
    }
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
