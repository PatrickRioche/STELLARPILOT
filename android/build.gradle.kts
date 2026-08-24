plugins {
    id("com.android.application") version "8.12.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0" apply false
}

// STELLARPILOT_LOCAL_BUILD
// Les sources restent dans Google Drive.
// Les fichiers temporaires/generes Gradle sont places hors Google Drive
// pour eviter les AccessDeniedException sous Windows.

val stellarPilotLocalAppData =
    System.getenv("LOCALAPPDATA")
        ?: System.getProperty("java.io.tmpdir")

val stellarPilotBuildRoot =
    file("$stellarPilotLocalAppData/StellarPilot/android-build")

layout.buildDirectory.set(
    stellarPilotBuildRoot.resolve("root")
)

subprojects {
    layout.buildDirectory.set(
        stellarPilotBuildRoot.resolve(name)
    )
}
