package me.mikun

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import me.mikun.storage.PicStorage

@Suppress("ktlint:standard:kdoc")
fun Application.configureRouting() {
    routing {
        /**
         * @description get random image
         */
        get("/random") {
            PicStorage.random()?.let {
                call.respondBytes {
                    it.readBytes()
                }
            } ?: call.respond(HttpStatusCode.NotFound)
        }
    }
}
