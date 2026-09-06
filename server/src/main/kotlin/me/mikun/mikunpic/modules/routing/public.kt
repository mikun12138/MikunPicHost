package me.mikun.mikunpic.modules.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import me.mikun.mikunpic.database.StorageDB
import me.mikun.mikunpic.dto.data.Platform
import me.mikun.mikunpic.dto.data.api.OhMyRouting
import me.mikun.mikunpic.storage.PicStorage

fun Route.public() {
    /**
     * @description get random image
     */
    get<OhMyRouting.Random> { req ->
        if (req.illustratorIds == null && req.tags == null) {
            PicStorage.weightRandom()?.let {
                call.respondBytes {
                    it.readBytes()
                }
            } ?: return@get call.respond(HttpStatusCode.NotFound)
        }

        val illustratorFilter =
            req.illustratorIds?.let { OhMyRouting.Manage.Pic.IllustratorFilter.Ids(it) }
                ?: OhMyRouting.Manage.Pic.IllustratorFilter.Any
        val tagFilter = req.tags?.let { OhMyRouting.Manage.Pic.TagFilter.All(it) }
            ?: OhMyRouting.Manage.Pic.TagFilter.Any
        // TODO:: cache
        StorageDB.randomPic(
            StorageDB.dbs.map { it.nameNoEx }.toSet(),
            1,
            illustratorFilter,
            tagFilter,
        ).firstNotNullOfOrNull { (storageLabel, pics) ->
            if (pics.isEmpty()) return@get call.respond(HttpStatusCode.NotFound)

            PicStorage.byKey(
                label = storageLabel,
                pics.first().storeKey
            )?.let {
                call.respondBytes {
                    it.readBytes()
                }
            } ?: call.respond(HttpStatusCode.NotFound)
        } ?: call.respond(HttpStatusCode.NotFound)

    }

    get<OhMyRouting.Pic.Id> { req ->
        val pic = StorageDB.byNameNoEx(req.storageLabel)?.selectPic(
            id = req.id.toInt(),
        ) ?: return@get call.respond(HttpStatusCode.NotFound)

        PicStorage.byKey(
            label = req.storageLabel,
            key = pic.storeKey,
            thumbnail = req.thumbnail,
        )?.let {
            call.respondBytes {
                it.readBytes()
            }
        } ?: call.respond(HttpStatusCode.NotFound)
    }

    get<OhMyRouting.Pic.PlatformKey> { req ->
        val platform =
            Platform.byName(req.platform) ?: return@get call.respond(HttpStatusCode.NotFound)

        for (db in StorageDB.dbs) {
            val pic = db.selectPic(
                platform = platform,
                key = req.key,
            ) ?: continue

            PicStorage.byKey(
                label = db.nameNoEx,
                key = pic.storeKey,
                thumbnail = req.thumbnail,
            )?.let {
                return@get call.respondBytes {
                    it.readBytes()
                }
            }
        }

        call.respond(HttpStatusCode.NotFound)
    }
}
