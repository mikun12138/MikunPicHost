package me.mikun.mikunpic

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.EngineMain
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import me.mikun.mikunpic.modules.configureAuth
import me.mikun.mikunpic.modules.configureCORS
import me.mikun.mikunpic.modules.configureDatabase
import me.mikun.mikunpic.modules.configureHTTP
import me.mikun.mikunpic.modules.configureOpenApi
import me.mikun.mikunpic.modules.configureRateLimit
import me.mikun.mikunpic.modules.configureResources
import me.mikun.mikunpic.modules.configureSerialization
import me.mikun.mikunpic.modules.routing.configureRouting
import me.mikun.mikunpic.storage.PicStorage

private const val APP_NAME = "mikunpic"
private const val DEPLOY_MODE_PREFIX = "--deploy-mode="
private const val PORT_PREFIX = "--port="

fun main(args: Array<String>) {
    val deployMode = args.firstOrNull { it.startsWith(DEPLOY_MODE_PREFIX) }
        ?.removePrefix(DEPLOY_MODE_PREFIX)
        ?.let(ServerAppDirs.DeployType::byValue)

    deployMode?.let { deployMode ->
        ServerAppDirs.init(APP_NAME, deployMode)
    } ?: ServerAppDirs.init(APP_NAME)

    val port = args.firstOrNull { it.startsWith(PORT_PREFIX) }
        ?.removePrefix(PORT_PREFIX)
        ?.let(String::toIntOrNull)
        ?: 8080

    embeddedServer(
        Netty,
        port = port,
        module = Application::module,
    ).start(wait = true)

}


fun Application.module() {
    configureHTTP()
    configureSerialization()
    configureAuth()
    configureResources()
    configureRouting()
    configureOpenApi()
    configureDatabase()

    configureRateLimit()

    configureCORS()

    install(XForwardedHeaders) {
        skipLastProxies(1)
    }

    PicStorage.configure(this)
}

fun Application.reloadStorage() {
    PicStorage.reload(this)
}
