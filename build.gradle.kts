plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.ktor) apply false

    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.android.lint) apply false
}

allprojects {
    group = "me.mikun.mikunpic"
    version = "0.2.1"
}

subprojects {
    apply(plugin = rootProject.libs.plugins.spotless.get().pluginId)

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("**/*.kt")

            ktlint("1.8.0")
                .editorConfigOverride(
                    mapOf(
                        "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
                        "ktlint_standard_filename" to "disabled",
                        "ktlint_standard_kdoc" to "disabled",
                    )
                )
        }
    }
}

tasks.register("runJsServerWebpack") {
    group = "dev"
    dependsOn(":app:webApp:jsBrowserDevelopmentRun")
    dependsOn(":server:run")
}

tasks.register("runJsServer") {
    group = "dev"
    dependsOn(":app:webApp:viteRun")
    dependsOn(":server:run")
}

tasks.register("runDesktopServer") {
    group = "dev"
    dependsOn(":app:desktopApp:hotRun")
    dependsOn(":server:run")
}

tasks.register<Exec>("adbReverse8080") {
    group = "dev"
    mustRunAfter(":app:androidApp:installDebug")
    commandLine("adb", "reverse", "tcp:8080", "tcp:8080")
}

tasks.register<Exec>("runAndroid") {
    group = "dev"
    dependsOn(":app:androidApp:installDebug")
    dependsOn("adbReverse8080")
    commandLine("adb", "shell", "monkey", "-p", "me.mikun.mikunpic", "1")
}

tasks.register("runAndroidServer") {
    group = "dev"
    dependsOn("runAndroid")
    dependsOn(":server:run")
}
