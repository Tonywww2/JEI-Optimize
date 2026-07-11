import net.fabricmc.loom.util.ModPlatform

plugins {
    id("dev.architectury.loom") version "1.11-SNAPSHOT"
    id("me.modmuss50.mod-publish-plugin") version "2.1.1"
}

val loader = loom.platform.get()
val mcVersion = property("vers.mcVersion").toString()
val javaVersion = if (stonecutter.eval(mcVersion, ">=1.20.6")) 21 else 17
val modId = property("mod.id").toString()
val modName = property("mod.name").toString()
val modVersion = property("mod.version").toString()
val modDescription = property("mod.description").toString()
val modAuthors = property("mod.authors").toString()
val forgeLoaderRange = (findProperty("vers.forge.loaderRange") ?: "").toString()
val forgeVersionRange = (findProperty("vers.forge.versionRange") ?: "").toString()
val neoforgeLoaderRange = (findProperty("vers.neoforge.loaderRange") ?: "").toString()
val neoforgeVersionRange = (findProperty("vers.neoforge.versionRange") ?: "").toString()
val jeiVersion = property("deps.jei").toString()
val mekanismVersion = (findProperty("deps.mekanism") ?: "").toString()
val cofhCoreVersion = (findProperty("deps.cofhCore") ?: "").toString()
val thermalCoreVersion = (findProperty("deps.thermalCore") ?: "").toString()
val thermalFoundationVersion = (findProperty("deps.thermalFoundation") ?: "").toString()
val farmersDelightVersion = (findProperty("deps.farmersDelight") ?: "").toString()
val mixinExtrasCommonVersion = (findProperty("deps.mixinExtrasCommon") ?: "").toString()
val mixinExtrasForgeVersion = (findProperty("deps.mixinExtrasForge") ?: "").toString()

group = property("mod.group").toString()
version = "$modVersion+$mcVersion"
base.archivesName = "$modId-${loader.id()}"

loom {
    silentMojangMappingsLicense()
    if (loader == ModPlatform.FORGE) {
        mixin {
            useLegacyMixinAp = true
            defaultRefmapName = "$modId.refmap.json"
        }
        forge {
            mixinConfig("$modId.mixins.json")
        }
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
    maven("https://maven.neoforged.net/releases")
    maven("https://maven.blamejared.com")
    maven("https://maven.covers1624.net/")
    maven("https://api.modrinth.com/maven")
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    mappings(loom.officialMojangMappings())

    if (loader == ModPlatform.FORGE) {
        "forge"("net.minecraftforge:forge:$mcVersion-${property("vers.deps.fml")}")

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
    } else {
        "neoForge"("net.neoforged:neoforge:${property("vers.deps.fml")}")

        // JEI 19.x (1.21.1) NeoForge artifact coordinates. Sub-artifact split may need
        // adjustment once the JEI internals are ported (see docs plan, phase 3).
        modCompileOnly("mezz.jei:jei-$mcVersion-neoforge-api:$jeiVersion")
        modCompileOnly("mezz.jei:jei-$mcVersion-common-api:$jeiVersion")
        compileOnly("mezz.jei:jei-$mcVersion-common:$jeiVersion")
        compileOnly("mezz.jei:jei-$mcVersion-lib:$jeiVersion")
        compileOnly("mezz.jei:jei-$mcVersion-gui:$jeiVersion")
        modRuntimeOnly("mezz.jei:jei-$mcVersion-neoforge:$jeiVersion")
    }
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
            "neoforgeLoaderRange" to neoforgeLoaderRange,
            "neoforgeVersionRange" to neoforgeVersionRange,
            "jeiVersion" to jeiVersion,
            "mixinCompat" to "JAVA_$javaVersion",
        )
        inputs.properties(props)
        filesMatching(listOf("META-INF/mods.toml", "META-INF/neoforge.mods.toml", "pack.mcmeta", "jei_optimize.mixins.json")) {
            expand(props)
        }
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release = javaVersion
    }
}

java {
    withSourcesJar()
    toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
}

// ---------------------------------------------------------------------------------------------------
// CurseForge publishing via me.modmuss50.mod-publish-plugin.
//
// Uploads this loader node's remapped jar; the root stonecutter.gradle.kts `publishAllVersions` task
// publishes every loader in one go. Secrets/ids are read lazily (only when a publish task runs), so
// ordinary builds are unaffected:
//   - CURSEFORGE_TOKEN     env var (preferred for CI), or curseforge.token in your USER-level
//     ~/.gradle/gradle.properties (never commit it).
//   - curseforge.projectId numeric id from the CurseForge project page (non-secret, in gradle.properties).
//
// Usage:
//   ./gradlew publishAllVersions                         # both loaders
//   ./gradlew :1.20.1-forge:publishMods                  # a single loader
//   ./gradlew publishAllVersions -Ppublish.dryRun=true   # validate the whole flow, upload nothing
// ---------------------------------------------------------------------------------------------------
publishMods {
    // Architectury Loom's final (remapped) artifact - not the raw jar task output.
    file = tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar").flatMap { it.archiveFile }
    version = project.version.toString() // e.g. 0.2.0+1.20.1 - unique per loader via mcVersion
    displayName = "${property("mod.name")} ${property("mod.version")} - MC $mcVersion (${loader.id()})"
    modLoaders.add(loader.id())
    type = STABLE

    // Validate the pipeline without uploading: -Ppublish.dryRun=true
    dryRun = providers.gradleProperty("publish.dryRun").map { it.toBoolean() }.orElse(false)

    changelog = providers.environmentVariable("CHANGELOG")
        .orElse(providers.provider { rootProject.file("CHANGELOG.md").takeIf { it.exists() }?.readText() })
        .orElse("See the GitHub releases page.")

    curseforge {
        projectId = providers.gradleProperty("curseforge.projectId")
        accessToken = providers.environmentVariable("CURSEFORGE_TOKEN")
            .orElse(providers.gradleProperty("curseforge.token"))
        minecraftVersions.add(mcVersion)
        javaVersions.add(JavaVersion.toVersion(javaVersion))
        // Client-side JEI startup optimizer. CurseForge (plugin 2.x) needs at least one environment.
        client = true
        server = false
    }
}