package me.mikun.storage

import io.ktor.server.application.Application
import io.ktor.server.application.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds
import kotlin.io.path.Path

class PicStorageLocal : PicStorage() {
    lateinit var folderPath: String

    override fun init(application: Application) {
        with(application) {
            environment.config.propertyOrNull("storage.local.path")?.getString()?.let {
                folderPath = it
            } ?: throw IllegalArgumentException("Please config: [storage.local.path] firstly!")

            require(File(folderPath).exists()) {
                "storage folder could not be found: $folderPath"
            }

            flashStorage()

            launch(Dispatchers.IO) {
                val watchService = FileSystems.getDefault().newWatchService()
                Path(folderPath).register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                )

                while (true) {
                    val key = watchService.take()
                    key
                        .pollEvents()
                        .filter { it.kind() == StandardWatchEventKinds.ENTRY_CREATE }
                        .forEach { event ->
                            val fileName = event.context().toString()
                            picKeys.add(fileName)
                        }
                    key.reset()

                    log.info("PicStorage count update: ${picKeys.size}")
                }
            }
        }
    }

    override suspend fun random(): InputStream? {
        return withContext(Dispatchers.IO) {
            fun findExist(maxAttempt: Int): FileInputStream? {
                repeat(maxAttempt) {
                    val fileName = picKeys.randomOrNull() ?: return null
                    val file =
                        File(
                            folderPath,
                            fileName,
                        )
                    if (file.exists()) return file.inputStream()
                }
                return null
            }

            findExist(10) ?: run {
                flashStorage()
                findExist(1)
            }
        }
    }

    fun flashStorage() {
        picKeys.clear()
        File(folderPath).listFiles()?.let { files ->
            picKeys.addAll(files.map { it.name })
        }
    }
}
