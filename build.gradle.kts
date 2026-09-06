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

val archPackageOutputDir = layout.buildDirectory.dir("arch/pkg")
val archSourceOutputDir = layout.buildDirectory.dir("arch/src")
val archBuildDir = layout.buildDirectory.dir("arch/build")
val archLogDir = layout.buildDirectory.dir("arch/log")

val buildArchPackage by tasks.registering(Exec::class) {
    group = "dev"

    workingDir = layout.projectDirectory.dir("app/packaging/arch").asFile
    commandLine("makepkg", "--force", "--cleanbuild")

    inputs.file(layout.projectDirectory.file("app/packaging/arch/PKGBUILD"))
    outputs.dir(archPackageOutputDir)
    outputs.upToDateWhen { false }

    doFirst {
        listOf(
            archPackageOutputDir,
            archSourceOutputDir,
            archBuildDir,
            archLogDir,
        ).forEach {
            it.get().asFile.mkdirs()
        }

        environment("PKGDEST", archPackageOutputDir.get().asFile.absolutePath)
        environment("SRCDEST", archSourceOutputDir.get().asFile.absolutePath)
        environment("SRCPKGDEST", archPackageOutputDir.get().asFile.absolutePath)
        environment("LOGDEST", archLogDir.get().asFile.absolutePath)
        environment("BUILDDIR", archBuildDir.get().asFile.absolutePath)
    }
}

val zipDesktopReleaseDistributable by tasks.registering(Zip::class) {
    group = "dev"
    dependsOn(":app:desktopApp:createReleaseDistributable")

    archiveFileName.set("mikunpicc-$version.zip")
    destinationDirectory.set(layout.buildDirectory.dir("buildAll/desktop"))

    from(project(":app:desktopApp").layout.buildDirectory.dir("compose/binaries/main-release/app"))
}

val zipViteBuild by tasks.registering(Zip::class) {
    group = "dev"
    dependsOn(":app:webApp:viteBuild")
    dependsOn(":app:webApp:copyComposeResourcesToViteDist")
    dependsOn(":app:webApp:copyConfigToViteDist")

    archiveFileName.set("mikunpicc-$version-web.zip")
    destinationDirectory.set(layout.buildDirectory.dir("buildAll/web"))
    from(project(":app:webApp").layout.buildDirectory.dir("vite/js/dist"))
}

val collectBuildAllOutputs by tasks.registering(Sync::class) {
    group = "dev"
    dependsOn(":server:shadowJar")
    dependsOn(zipDesktopReleaseDistributable)
    dependsOn(":app:desktopApp:packageReleaseDeb")
    dependsOn(zipViteBuild)
    dependsOn(buildArchPackage)

    into(layout.buildDirectory.dir("buildAll"))

    preserve {
        include("desktop/mikunpicc-$version.zip")
        include("web/mikunpicc-$version-web.zip")
    }

    from(project(":server").layout.buildDirectory.dir("libs")) {
        include("mikunpic-$version.jar")
        into("server")
    }

    from(project(":app:desktopApp").layout.buildDirectory.dir("compose/binaries/main-release/deb")) {
        include("*.deb")
        into("desktop")
    }

    from(archPackageOutputDir) {
        include("*.pkg.tar.*")
        into("arch")
    }
}

tasks.register("buildAll") {
    group = "dev"
    dependsOn(collectBuildAllOutputs)
}
