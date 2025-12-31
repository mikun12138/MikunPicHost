package me.mikun

import io.ktor.server.application.Application
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.routing.routing

fun Application.configureOpenApi() {
    routing {
        openAPI(path = "/", swaggerFile = "openapi/generated.json")
    }
}
