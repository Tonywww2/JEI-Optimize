plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.20.1-forge"

stonecutter parameters {
    val loader = current.project.substringAfterLast('-')
    constants {
        match(loader, "forge", "neoforge")
    }
}

// ---------------------------------------------------------------------------------------------------
// One-command multi-loader publish. Runs each node's publishMods (CurseForge upload, see
// build.gradle.kts) for every Stonecutter version, regardless of the currently active one.
//   ./gradlew publishAllVersions
// ---------------------------------------------------------------------------------------------------
tasks.register("publishAllVersions") {
    group = "publishing"
    description = "Builds and publishes every Minecraft/loader version to CurseForge."
    dependsOn(stonecutter.tasks.named("publishMods").map { it.values })
}

// Upload each loader file serially to avoid CurseForge API rate limiting.
stonecutter.tasks.order("publishCurseforge")