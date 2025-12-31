import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.ktor.plugin.OpenApiPreview

val kotlin_version: String by project
//val logback_version: String by project

plugins {
    kotlin("jvm") version "2.2.20"
    id("io.ktor.plugin") version "3.3.2"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20"
    id("com.diffplug.spotless") version "8.1.0"
}

group = "me.mikun"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

ktor {
    @OptIn(OpenApiPreview::class)
    openApi {
        title = "Mikun PicHost Api"
        version = "0.0.1"
        description = "simple pic-host with local file"
    }
}

dependencies {
    implementation("org.openfolder:kotlin-asyncapi-ktor:3.1.3")
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-openapi")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-netty")
//    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.ktor:ktor-server-config-yaml")
    implementation("io.ktor:ktor-server-rate-limit")

    implementation("org.apache.logging.log4j:log4j-api:2.25.3")
    implementation("org.apache.logging.log4j:log4j-core:2.25.3")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.3")

    implementation("com.qcloud:cos_api:5.6.259")
    implementation("com.qcloud:cos-sts_api:3.1.1")


    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
    testImplementation(kotlin("test"))

}

configurations.all {
    exclude(group = "ch.qos.logback")
}

tasks.withType<Jar> {
    dependsOn("buildOpenApi")
    exclude("application.yaml")
}

tasks.withType<ShadowJar> {
    dependsOn("buildOpenApi")
    exclude("application.yaml")
}

spotless {
    kotlin {
        target("src/**/*.kt")

        ktlint("1.8.0")
            .editorConfigOverride(mapOf(
            ))
    }
}
