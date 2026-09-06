package me.mikun.mikunpic.modules.routing

import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.delay
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import me.mikun.mikunpic.LocalMikunPicConfig
import me.mikun.mikunpic.database.MetadataDB
import me.mikun.mikunpic.database.StorageDB
import me.mikun.mikunpic.dto.data.MikunPicConfig
import me.mikun.mikunpic.dto.data.PicCreate
import me.mikun.mikunpic.dto.data.Storage
import me.mikun.mikunpic.dto.data.api.OhMyRouting
import me.mikun.mikunpic.operator.sync
import me.mikun.mikunpic.operator.uploadPic
import me.mikun.mikunpic.reloadStorage
import me.mikun.mikunpic.storage.PicStorage
import me.mikun.mikunpic.storage.PicStorageLocal
import me.mikun.mikunpic.utils.toStorageConfig
import kotlin.time.Duration.Companion.milliseconds

fun Route.manage() {
    fun storage() {
        get<OhMyRouting.Manage.Storage.List> {
            call.respond(
                OhMyRouting.Manage.Storage.List.Response(
                    LocalMikunPicConfig.storages.map {
                        when (it) {
                            is MikunPicConfig.Storage.Local -> Storage.Local(
                                label = it.label,
                                pathRule = it.pathRule,
                                path = it.path,
                            )

                            is MikunPicConfig.Storage.Cos -> Storage.Cos(
                                label = it.label,
                                pathRule = it.pathRule,
                                secretId = "",
                                secretKey = "",
                                bucketName = it.bucketName,
                                region = it.region,
                            )
                        }
                    },
                ),
            )
        }

        post<OhMyRouting.Manage.Storage.Add> {
            val receive = call.receive<OhMyRouting.Manage.Storage.Add.Body>()
            if (LocalMikunPicConfig.storages.any { it.label == receive.storage.label }) {
                return@post call.respond(HttpStatusCode.Conflict)
            }
            LocalMikunPicConfig = LocalMikunPicConfig.copy(
                storages = LocalMikunPicConfig.storages + receive.storage.toStorageConfig(),
            )
            this@manage.application.reloadStorage()
            call.respond(HttpStatusCode.OK)
        }

        post<OhMyRouting.Manage.Storage.Edit> {
            val receive = call.receive<OhMyRouting.Manage.Storage.Edit.Body>()
            if (LocalMikunPicConfig.storages.none { it.label == receive.storage.label }) {
                return@post call.respond(HttpStatusCode.Conflict)
            }

            val newStorage =
                LocalMikunPicConfig.storages.find { it.label == receive.storage.label }?.takeIf {
                    it::class == receive.storage.toStorageConfig()::class
                }?.let {
                    receive.storage.toStorageConfig(old = it)
                } ?: receive.storage.toStorageConfig()

            LocalMikunPicConfig = LocalMikunPicConfig.copy(
                storages = LocalMikunPicConfig.storages.filter { it.label != receive.storage.label } + newStorage,
            )
            this@manage.application.reloadStorage()
            call.respond(HttpStatusCode.OK)
        }
        post<OhMyRouting.Manage.Storage.Delete> {
            val receive = call.receive<OhMyRouting.Manage.Storage.Delete.Body>()
            if (LocalMikunPicConfig.storages.none { it.label == receive.storageLabel }) {
                return@post call.respond(HttpStatusCode.Conflict)
            }
            LocalMikunPicConfig = LocalMikunPicConfig.copy(
                storages = LocalMikunPicConfig.storages.filter { it.label != receive.storageLabel },
            )
            this@manage.application.reloadStorage()
            call.respond(HttpStatusCode.OK)
        }

        post<OhMyRouting.Manage.Storage.Sync> {
            val receive = call.receive<OhMyRouting.Manage.Storage.Sync.Body>()
            sync(
                storageLabel = receive.storageLabel,
                syncRuleText = receive.syncRuleText,
            )

            this@manage.application.reloadStorage()
            call.respond(HttpStatusCode.OK)
        }
    }

    fun pic() {
        post<OhMyRouting.Manage.Pic.Upload> {
            val multipart = call.receiveMultipart()

            var storageLabel: String? = null
            var byteArray: ByteArray? = null
            var pic: PicCreate? = null
            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        byteArray = part.provider().readRemaining().readByteArray()
//                        filename = part.originalFileName
                    }

                    is PartData.FormItem -> {
                        when (part.name) {
                            "storage_label" -> {
                                storageLabel = part.value
                            }

                            "pic" -> {
                                pic = Json.decodeFromString(part.value)
                            }
                        }
                    }

                    else -> part.dispose()
                }
            }

            if (storageLabel == null || byteArray == null || pic == null) {
                call.respond(
                    HttpStatusCode.BadGateway,
                )
                return@post
            }

            uploadPic(
                storageLabel,
                byteArray,
                pic!!,
            )

            call.respond(
                HttpStatusCode.Created,
            )
        }

        post<OhMyRouting.Manage.Pic.Update> {
            val receive = call.receive<OhMyRouting.Manage.Pic.Update.Body>()

            StorageDB.byNameNoEx(receive.storageLabel)?.apply {
                updatePic(
                    receive.pic,
                )
            }

            call.respond(HttpStatusCode.Created)
        }

        post<OhMyRouting.Manage.Pic.List> {
            val receive = call.receive<OhMyRouting.Manage.Pic.List.Body>()
            StorageDB.listPic(
                receive.storageLabels.toSet(),
                receive.count,
                receive.page,
                receive.illustrator,
                receive.tag,
            ).let { label2Pics ->
                call.respond(
                    OhMyRouting.Manage.Pic.List.Response(
                        label2Pics = label2Pics,
                    ),
                )
            }
        }

        post<OhMyRouting.Manage.Pic.Random> {
            val receive = call.receive<OhMyRouting.Manage.Pic.Random.Body>()
            StorageDB.randomPic(
                receive.storageLabels.toSet(),
                receive.count,
                receive.illustrator,
                receive.tag,
            ).let { label2Pics ->
                call.respond(
                    OhMyRouting.Manage.Pic.Random.Response(
                        label2Pics = label2Pics,
                    ),
                )
            }
        }
    }

    fun illustrator() {
        post<OhMyRouting.Manage.Illustrator.Create> {
            val receive = call.receive<OhMyRouting.Manage.Illustrator.Create.Body>()

            MetadataDB.createIllustrator(
                receive.illustrator,
            )
        }

        get<OhMyRouting.Manage.Illustrator.Search> { req ->
            MetadataDB.searchIllustrator(
                count = req.count,
                keyword = req.keyword,
                page = req.page,
            ).let {
                call.respond(
                    OhMyRouting.Manage.Illustrator.Search.Response(
                        it,
                    ),
                )
            }
        }
    }

    fun tag() {
        post<OhMyRouting.Manage.Tag.Create> {
            val receive = call.receive<OhMyRouting.Manage.Tag.Create.Body>()

            MetadataDB.createTag(
                receive.name,
            )

            call.respond(HttpStatusCode.Created)
        }

        post<OhMyRouting.Manage.Tag.Delete> {
            val receive = call.receive<OhMyRouting.Manage.Tag.Delete.Body>()
            MetadataDB.deleteTag(
                receive.name,
            )

            call.respond(HttpStatusCode.Accepted)
        }

        get<OhMyRouting.Manage.Tag.Search> { req ->
            MetadataDB.searchTag(
                count = req.count,
                keyword = req.keyword,
                page = req.page,
            ).let {
                call.respond(
                    OhMyRouting.Manage.Tag.Search.Response(
                        it,
                    ),
                )
            }
        }
    }

    storage()

    pic()
    illustrator()
    tag()

    post<OhMyRouting.Manage.Backup> {
        StorageDB.backup()
        call.respond(HttpStatusCode.OK)
    }
}
