package me.mikun

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import me.mikun.storage.PicStorage
import kotlin.time.Duration.Companion.minutes

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain
        .main(args)
}

fun Application.module() {
    configureHTTP()
    configureSerialization()
    configureRouting()
    configureOpenApi()

    if (environment.config.property("api_only").getString() == "true") {
        install(ApiOnlyPlugin)
    }

    install(RateLimit) {
        register(RateLimitName("with_ip")) {
            rateLimiter(limit = 60, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }
    }

    install(XForwardedHeaders) {
        skipLastProxies(1)
    }

    PicStorage.configure(this)
}
