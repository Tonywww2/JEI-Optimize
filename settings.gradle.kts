pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.architectury.dev")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.kikugie.dev/snapshots")
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.6"
}

rootProject.name = "jei-optimize"

stonecutter {
    kotlinController = true
    create(rootProject) {
        version("1.20.1-forge", "1.20.1")
        version("1.21.1-neoforge", "1.21.1")
        vcsVersion = "1.20.1-forge"
    }
}