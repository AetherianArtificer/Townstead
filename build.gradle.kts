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

dependencies {
    compileOnly(files("${rootProject.projectDir}/libs/mca-neoforge-7.7.5+1.21.1.jar"))
    testImplementation(files("${rootProject.projectDir}/libs/mca-neoforge-7.7.5+1.21.1.jar"))
    testImplementation("com.google.code.gson:gson:2.10.1")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
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
