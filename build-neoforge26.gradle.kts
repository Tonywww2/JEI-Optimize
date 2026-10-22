plugins {
    id("java-library")
    id("net.neoforged.moddev") version "2.0.148"
    id("me.modmuss50.mod-publish-plugin") version "2.1.1"
}

val mcVersion = project.property("vers.mcVersion").toString()
val modId = project.property("mod.id").toString()
val modVersion = project.property("mod.version").toString()
val jeiVersion = project.property("deps.jei").toString()
val jeiMinimumVersion = (findProperty("deps.jei.minimum") ?: jeiVersion).toString()
val jeiRuntimeVersion = findProperty("jei.runtime.$mcVersion")?.toString() ?: jeiVersion
val mezzConfigVersion = (findProperty("mezzConfig.runtime.$mcVersion") ?: project.property("deps.mezzConfig")).toString()
val neoVersion = (findProperty("neoforge.runtime.$mcVersion") ?: project.property("vers.deps.fml")).toString()

group = project.property("mod.group").toString()
version = "$modVersion+$mcVersion"
base.archivesName = modId
java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    withSourcesJar()
}

neoForge {
    version = neoVersion
    runs {
        create("client") {
            client()
            val runDir = rootProject.file(project.findProperty("compatTest.runDirectory.$mcVersion")
                ?.toString() ?: "versions/26.1.2-neoforge/run").canonicalFile
            require(runDir.toPath().startsWith(rootProject.projectDir.canonicalFile.toPath())) {
                "Compatibility test run directory must stay inside the workspace"
            }
            gameDirectory = runDir
            project.findProperty("compatTest.world")?.toString()?.let {
                programArguments.addAll("--quickPlaySingleplayer", it)
            }
            if (project.findProperty("compatTest.benchmark") == "true") {
                systemProperty("jet.benchmark", "true")
                systemProperty("jet.benchmark.autoExit", "true")
            }
        }
    }
    runs.create("server") {
        server()
        gameDirectory = rootProject.file("versions/26.1.2-neoforge/run-server")
        programArguments.add("--nogui")
    }
    mods {
        create(modId) { sourceSet(sourceSets.main.get()) }
    }
}

sourceSets.main { java.exclude("**/IngredientFilterMixin.java") }

repositories {
    mavenCentral()
    maven("https://maven.blamejared.com")
}

dependencies {
    project.findProperty("compatTest.runtimeModJars.$mcVersion")?.toString()
        ?.split(',')?.filter(String::isNotBlank)?.forEach { runtimeOnly(files(rootProject.file(it))) }
    // JEI bundles these libraries for the game; standalone regression mains need them on their classpath.
    compileOnly("net.mezzdev:suffixtree:1.1.0")
    compileOnly("net.mezzdev:baked-substring-index:0.1.0")
    compileOnly("mezz.jei:jei-$mcVersion-common-api:$jeiVersion")
    compileOnly("mezz.jei:jei-$mcVersion-neoforge-api:$jeiVersion")
    compileOnly("mezz.jei:jei-$mcVersion-common:$jeiVersion")
    compileOnly("mezz.jei:jei-$mcVersion-lib:$jeiVersion")
    compileOnly("mezz.jei:jei-$mcVersion-gui:$jeiVersion")
    runtimeOnly("mezz.jei:jei-$mcVersion-neoforge:$jeiRuntimeVersion@jar")
    compileOnly("net.mezzdev.config:mezz_config-$mcVersion-neoforge:$mezzConfigVersion@jar")
    runtimeOnly("net.mezzdev.config:mezz_config-$mcVersion-neoforge:$mezzConfigVersion@jar")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
}
tasks.processResources {
    val props = mapOf(
        "id" to modId,
        "name" to project.property("mod.name"),
        "version" to modVersion,
        "description" to project.property("mod.description"),
        "authors" to project.property("mod.authors"),
        "minecraftVersion" to mcVersion,
        "minecraftVersionRange" to project.property("vers.minecraft.versionRange"),
        "neoforgeLoaderRange" to project.property("vers.neoforge.loaderRange"),
        "neoforgeVersionRange" to project.property("vers.neoforge.versionRange"),
        "jeiMinimumVersion" to jeiMinimumVersion,
        "mixinCompat" to "JAVA_25"
    )
    inputs.properties(props)
    inputs.property("mixinManifestFormat", 1)
    exclude("META-INF/mods.toml", "pack.mcmeta")
    filesMatching(listOf("META-INF/neoforge.mods.toml", "$modId.mixins.json")) { expand(props) }
    filesMatching("$modId.mixins.json") {
        // Stonecutter preprocesses Java; select the new ABI's injectors in the JSON separately.
        filter { line ->
            if (line.trim() == "\"IngredientFilterModernMixin\","
                || line.trim() == "\"JeiGuiRenderGuardModernMixin\","
                || line.trim().startsWith("\"refmap\"")) ""
            else line.replace("\"IngredientFilterMixin\"", "\"IngredientFilter26Mixin\"")
                .replace("\"JeiNativeSearchBuilderMixin\"", "\"JeiNativeSearchBuilder26Mixin\"")
                .replace("\"JeiGuiRenderGuardMixin\"", "\"JeiGuiRenderGuard26Mixin\"")
        }
    }
}

val javaVersion = 25
val tooltipJavaVersion = javaVersion
sourceSets.test { java.exclude("**/Tooltip*Test.java") }
val tooltipTestSources = fileTree(layout.buildDirectory.dir("generated/stonecutter/test/java")) {
    include("**/Tooltip*Test.java")
}
val compileTooltipTests = tasks.register<JavaCompile>("compileTooltipTests") {
    dependsOn(tasks.named("classes"), "stonecutterGenerateTest")
    source(tooltipTestSources)
    classpath = sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    destinationDirectory.set(layout.buildDirectory.dir("classes/tooltipTest"))
    javaCompiler.set(javaToolchains.compilerFor {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    })
}
val tooltipTests = listOf(
    "instrumentation.TooltipMemoryDiagnosticsTest",
    "config.TooltipPluginThreadPolicyTest",
    "integration.TooltipMineColoniesAttributesTest",
    "index.TooltipDeferredStorageTest",
    "runtime.TooltipUiRefreshBatchTest",
    "index.TooltipPipelineTest",
    "runtime.TooltipBuildGateTest",
    "index.TooltipBackendTest",
    "index.TooltipUidCompatibilityTest",
    "runtime.TooltipCaptureContextTest",
    "index.TooltipSearchIndexTest",
    "TooltipAbiTest"
).map { testClass ->
    tasks.register<JavaExec>("run" + testClass.substringAfterLast('.')) {
        dependsOn(compileTooltipTests)
        classpath = files(compileTooltipTests.flatMap { it.destinationDirectory }) +
            sourceSets.main.get().output + sourceSets.main.get().compileClasspath
        mainClass.set("com.tonywww.jeioptimize.$testClass")
        javaLauncher.set(javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(tooltipJavaVersion))
        })
        doFirst {
            val jars = project.findProperty("tooltipTest.jars")?.toString()
                ?.split(';')?.filter(String::isNotBlank)
                ?: listOf(configurations.runtimeClasspath.get().single {
                    it.name == "jei-$mcVersion-neoforge-$jeiRuntimeVersion.jar"
                }.absolutePath)
            args(jars)
        }
    }
}
tasks.register("tooltipTest") {
    group = "verification"
    description = "Runs tooltip capture, reference substring index and ABI tests without launching Minecraft."
    dependsOn(tooltipTests)
}
tasks.named("check") { dependsOn("tooltipTest") }

// Minecraft 26.1+ is unobfuscated. The ordinary jar is the production artifact.
publishMods {
    file = tasks.jar.flatMap { it.archiveFile }
    version = project.version.toString()
    displayName = "${project.property("mod.name")} $modVersion - MC $mcVersion (neoforge)"
    modLoaders.add("neoforge")
    type = STABLE
    dryRun = providers.gradleProperty("publish.dryRun").map { it.toBoolean() }.orElse(false)
    changelog = providers.environmentVariable("CHANGELOG")
        .orElse(providers.provider { rootProject.file("CHANGELOG.md").readText() })
    curseforge {
        projectId = providers.gradleProperty("curseforge.projectId")
        accessToken = providers.environmentVariable("CURSEFORGE_TOKEN")
            .orElse(providers.gradleProperty("curseforge.token"))
        minecraftVersions.add(mcVersion)
        javaVersions.add(JavaVersion.toVersion(25))
        client = true
        server = false
    }
}
