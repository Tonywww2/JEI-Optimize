import net.fabricmc.loom.util.ModPlatform

plugins {
    id("dev.architectury.loom") version "1.11-SNAPSHOT"
}

val loader = loom.platform.get()
val mcVersion = property("vers.mcVersion").toString()
val modId = property("mod.id").toString()
val modName = property("mod.name").toString()
val modVersion = property("mod.version").toString()
val modDescription = property("mod.description").toString()
val modAuthors = property("mod.authors").toString()
val forgeLoaderRange = property("vers.forge.loaderRange").toString()
val forgeVersionRange = property("vers.forge.versionRange").toString()
val jeiVersion = property("deps.jei").toString()
val mekanismVersion = property("deps.mekanism").toString()
val cofhCoreVersion = property("deps.cofhCore").toString()
val thermalCoreVersion = property("deps.thermalCore").toString()
val thermalFoundationVersion = property("deps.thermalFoundation").toString()
val farmersDelightVersion = property("deps.farmersDelight").toString()
val mixinExtrasCommonVersion = property("deps.mixinExtrasCommon").toString()
val mixinExtrasForgeVersion = property("deps.mixinExtrasForge").toString()

group = property("mod.group").toString()
version = "$modVersion+$mcVersion"
base.archivesName = "$modId-${loader.id()}"

loom {
    silentMojangMappingsLicense()
    mixin {
        defaultRefmapName = "$modId.refmap.json"
    }
    forge {
        mixinConfig("$modId.mixins.json")
    }
    if (stonecutter.current.isActive) {
        runConfigs.all {
            ideConfigGenerated(true)
            runDir("../../run")
        }
    }
}

repositories {
    mavenCentral()
    maven("https://maven.minecraftforge.net/")
    maven("https://maven.blamejared.com")
    maven("https://maven.covers1624.net/")
    maven("https://api.modrinth.com/maven")
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    mappings(loom.officialMojangMappings())

    if (loader == ModPlatform.FORGE) {
        "forge"("net.minecraftforge:forge:$mcVersion-${property("vers.deps.fml")}")
    } else {
        error("This base project currently supports only Forge 1.20.1")
    }

    modCompileOnly("mezz.jei:jei-$mcVersion-forge-api:$jeiVersion")
    compileOnly("mezz.jei:jei-$mcVersion-core:$jeiVersion")
    compileOnly("mezz.jei:jei-$mcVersion-common:$jeiVersion")
    compileOnly("mezz.jei:jei-$mcVersion-lib:$jeiVersion")
    compileOnly("mezz.jei:jei-$mcVersion-gui:$jeiVersion")
    modRuntimeOnly("mezz.jei:jei-$mcVersion-forge:$jeiVersion")

    modRuntimeOnly("maven.modrinth:mekanism:$mekanismVersion")
    modRuntimeOnly("maven.modrinth:cofh-core:$cofhCoreVersion")
    modRuntimeOnly("com.teamcofh:thermal_core:$thermalCoreVersion")
    modRuntimeOnly("maven.modrinth:thermal-foundation:$thermalFoundationVersion")
    modRuntimeOnly("maven.modrinth:farmers-delight:$farmersDelightVersion")
    "forgeRuntimeLibrary"("io.github.llamalad7:mixinextras-common:$mixinExtrasCommonVersion")
    modRuntimeOnly("io.github.llamalad7:mixinextras-forge:$mixinExtrasForgeVersion")
}

tasks {
    matching { it.name == "createMinecraftArtifacts" }.configureEach {
        dependsOn("stonecutterGenerate")
    }

    processResources {
        val props = mapOf(
            "id" to modId,
            "name" to modName,
            "version" to modVersion,
            "description" to modDescription,
            "authors" to modAuthors,
            "minecraftVersion" to mcVersion,
            "forgeLoaderRange" to forgeLoaderRange,
            "forgeVersionRange" to forgeVersionRange,
            "jeiVersion" to jeiVersion,
        )
        inputs.properties(props)
        filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) {
            expand(props)
        }
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release = 17
    }
}

java {
    withSourcesJar()
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}