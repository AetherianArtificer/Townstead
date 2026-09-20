import java.util.zip.ZipFile

plugins {
    `java-library`
    id("net.neoforged.moddev") version "2.0.147"
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
    version = property("neoforge_version").toString()

    runs {
        register("client") {
            client()
            gameDirectory = rootProject.file("run")
            // ModDev keeps ordinary implementation dependencies outside FML's game layer.
            // Put the embedded Chronicles backend on that layer for development launches too.
            additionalRuntimeClasspathConfiguration.dependencies.add(
                project.dependencies.create("com.h2database:h2-mvstore:${property("h2_mvstore_version")}")
            )
        }
        register("server") {
            server()
            gameDirectory = rootProject.file("run")
            additionalRuntimeClasspathConfiguration.dependencies.add(
                project.dependencies.create("com.h2database:h2-mvstore:${property("h2_mvstore_version")}")
            )
        }
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
    // Curios API, for the optional wearables integration (villager Curios slots and screen).
    maven {
        url = uri("https://www.cursemaven.com")
        content { includeGroup("curse.maven") }
    }
    mavenCentral()
}

val mcaVersion = providers.gradleProperty("mca_version").get()
val mcaDevelopmentVersion = providers.gradleProperty("mca_development_version").get()
val mcaJarVersion = providers.gradleProperty("mca_jar_version").get()
val mcaJar = rootProject.file("libs/mca-neoforge-$mcaJarVersion.jar")

dependencies {
    // Townstead compiles against the current MCA snapshot. verifyMcaDependency checks that the
    // local jar still declares the supported snapshot version before Java compilation.
    compileOnly(files(mcaJar))
    runtimeOnly(files(mcaJar))
    implementation(jarJar("io.github.llamalad7:mixinextras-neoforge:${property("mixin_extras_version")}")!!)
    // JEI plugin API (runtime optional; the plugin class is only loaded by JEI's scan)
    compileOnly("mezz.jei:jei-1.21.1-common-api:19.39.0.370")
    compileOnly("mezz.jei:jei-1.21.1-neoforge-api:19.39.0.370")
    // Iron's Spells API, for reading what is actually in a quick-cast slot. compileOnly and
    // non-transitive: the bridge is guarded by ModList, so nothing here is required at runtime.
    compileOnly("io.redspace:irons_spellbooks:1.21.1-3.16.2:api") { isTransitive = false }
    // Curios (runtime optional): everything Curios-shaped lives in compat.curios behind ModCompat.
    compileOnly("curse.maven:curios-309927:6529130")
    // Jade plugin API (runtime optional; the plugin class is only loaded by Jade's scan)
    compileOnly("curse.maven:jade-324717:8591319")
    // Pure-Java Chronicle archive backend, embedded without SQLite's native binaries.
    implementation(jarJar("com.h2database:h2-mvstore:${property("h2_mvstore_version")}")!!)
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    // Pheno unit tests touch Minecraft types (ResourceLocation, GsonHelper); moddev keeps MC as a
    // non-transitive compileOnly, so surface the main compile classpath to the test classpath.
    testImplementation(files(sourceSets.main.get().compileClasspath))
}

// Tests that need the real CompoundTag/BlockPos/FriendlyByteBuf. src/test shadows those
// with stubs, so these run as a separate suite. `gradlew check` runs both.
testing {
    suites {
        val integrationTest by registering(JvmTestSuite::class) {
            useJUnitJupiter()
            dependencies {
                implementation(platform("org.junit:junit-bom:5.10.2"))
                implementation(files(sourceSets.main.get().compileClasspath))
                implementation(sourceSets.main.get().output)
                // en_us.json, which Language loads the first time a translatable is read.
                runtimeOnly(fileTree(layout.buildDirectory.dir("moddev/artifacts")) {
                    include("*client-extra*.jar")
                })
            }
            targets.all { testTask.configure { shouldRunAfter(tasks.test) } }
        }
    }
}
tasks.check { dependsOn(testing.suites.named("integrationTest")) }

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
    val replaceProperties = mapOf(
        "version" to project.version,
        "mca_version" to mcaVersion,
        "mca_development_version" to mcaDevelopmentVersion
    )
    inputs.properties(replaceProperties)
    filesMatching(listOf("META-INF/neoforge.mods.toml", "META-INF/townstead-mca.properties")) {
        expand(replaceProperties)
    }
    exclude("META-INF/mods.toml")
    // Move compat building types to a non-loading location for conditional runtime loading
    eachFile {
        if (path.startsWith("data/mca/building_types/compat/")) {
            path = path.replace("data/mca/", "townstead_compat/")
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

val verifyMcaDependency by tasks.registering {
    group = "verification"
    description = "Verifies that Townstead is compiling against the supported MCA snapshot version."
    inputs.file(mcaJar)
    inputs.property("mcaDevelopmentVersion", mcaDevelopmentVersion)

    doLast {
        check(mcaJar.isFile) {
            "Missing pinned MCA dependency: ${mcaJar.absolutePath}"
        }

        val metadata = ZipFile(mcaJar).use { archive ->
            val entry = archive.getEntry("META-INF/neoforge.mods.toml")
                ?: error("Pinned MCA jar has no META-INF/neoforge.mods.toml")
            archive.getInputStream(entry).bufferedReader().use { it.readText() }
        }
        val declaredVersion = Regex("(?m)^version\\s*=\\s*\"([^\"]+)\"\\s*$")
            .find(metadata)?.groupValues?.get(1)
            ?: error("Pinned MCA jar does not declare a mod version")
        val expectedVersion = mcaDevelopmentVersion
        check(declaredVersion == expectedVersion) {
            "Pinned MCA jar declares $declaredVersion; expected $expectedVersion"
        }
    }
}

tasks.withType<JavaCompile> {
    dependsOn(verifyMcaDependency)
    options.encoding = "UTF-8"
}
tasks.withType<Test> {
    useJUnitPlatform()
    // ApiV1IsolationTest scans the compiled api/v1 classes for leaked internals.
    systemProperty("townstead.classes", sourceSets.main.get().output.classesDirs.asPath)
    systemProperty("townstead.mcaVersion", mcaVersion)
    systemProperty("townstead.mcaDevelopmentVersion", mcaDevelopmentVersion)
}

// The public API alone, for third-party mods to compile against (compileOnly, never shipped).
tasks.register<Jar>("apiJar") {
    group = "build"
    description = "Packages only com.aetherianartificer.townstead.api.v1 for consumers to compile against."
    archiveBaseName.set("townstead-api")
    archiveClassifier.set("v1")
    from(sourceSets.main.get().output) { include("com/aetherianartificer/townstead/api/v1/**") }
    from(sourceSets.main.get().allSource) { include("com/aetherianartificer/townstead/api/v1/**") }
    dependsOn(tasks.named("classes"))
}
