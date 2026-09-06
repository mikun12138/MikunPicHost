package me.mikun.mikunpic.storage

import io.ktor.server.application.Application
import io.ktor.server.application.log
import me.mikun.mikunpic.LocalMikunPicConfig
import me.mikun.mikunpic.dto.awesome.FileExtension
import me.mikun.mikunpic.dto.data.MikunPicConfig
import me.mikun.mikunpic.dto.data.api.OhMyRouting
import java.io.InputStream
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.random.Random
import kotlin.random.nextInt

sealed class PicStorage {
    abstract val label: String
    val picKeys =
        object : CopyOnWriteArraySet<String>() {

            private fun isValid(e: String?): Boolean = e != null &&
                FileExtension.image.any {
                    e.endsWith(
                        it,
                        ignoreCase = true,
                    )
                }

            override fun add(e: String?): Boolean = isValid(e) && super.add(e)

            override fun addAll(elements: Collection<String>): Boolean {
                val valid = elements.filter { isValid(it) }
                return super.addAll(valid)
            }
        }

    companion object {
        val storages: MutableList<PicStorage> = mutableListOf()

        fun reload(application: Application) {
            storages.clear()
            configure(application)
        }

        fun configure(application: Application) {
            runCatching {
                LocalMikunPicConfig.storages.forEach {
                    when (it) {
                        is MikunPicConfig.Storage.Local -> {
                            storages.add(
                                PicStorageLocal(
                                    it.label,
                                ).apply {
                                    init(
                                        application,
                                        it,
                                    )
                                },
                            )
                        }

                        is MikunPicConfig.Storage.Cos -> {
                            storages.add(
                                PicStorageCos(
                                    it.label,
                                ).apply {
                                    init(
                                        application,
                                        it,
                                    )
                                },
                            )
                        }

                        else -> error("??? how can you reach here ???")
                    }
                    application.log.info("Add storage: ${it.label}")
                }
            }.onFailure { e ->
                application.log.error(e.message)
                // TODO::
//                throw e
            }
        }

        suspend fun random(): InputStream? = storages.random().random()

        suspend fun weightRandom(): InputStream? {
            val weights = storages.map { it.picKeys.count() }
            val sum = weights.sum()
            if (sum == 0) return null
            var random = Random.nextInt(sum)
            weights.forEachIndexed { index, weight ->
                random -= weight
                if (random < 0) {
                    return storages[index].random()
                }
            }

            error("it should be unreachable...")
        }

        suspend fun byKey(
            label: String,
            key: String,
            thumbnail: OhMyRouting.Pic.Thumbnail = OhMyRouting.Pic.Thumbnail.Orig,
        ): InputStream? {
            storages.find { it.label == label }?.let { storage ->
                try {
                    storage.byKey(key, thumbnail)?.let {
                        return it
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } ?: run {
                storages.forEach { storage ->
                    try {
                        storage.byKey(key, thumbnail)?.let {
                            return it
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            return null
        }

        suspend fun upload(
            label: String,
            byteArray: ByteArray,
            storeKey: String,
        ) = storages.find { it.label == label }?.upload(
            byteArray,
            storeKey,
        )
    }

    abstract fun init(
        application: Application,
        storage: MikunPicConfig.Storage,
    )

    abstract suspend fun random(): InputStream?

    abstract suspend fun hash(
        key: String,
    ): String?

    abstract suspend fun byKey(
        key: String,
        thumbnail: OhMyRouting.Pic.Thumbnail = OhMyRouting.Pic.Thumbnail.Orig,
    ): InputStream?

    abstract suspend fun upload(
        byteArray: ByteArray,
        storeKey: String,
    )
}
