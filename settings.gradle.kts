pluginManagement {
    repositories {
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.minecraftforge.net/")
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.7.11"
}

stonecutter {
    kotlinController = true
    centralScript = "build.gradle.kts"

    create(rootProject) {
        version("1.21.1-neoforge", "1.21.1")
        version("1.20.1-forge", "1.20.1").buildscript("build.forge.gradle.kts")
        vcsVersion = "1.21.1-neoforge"
    }
}
