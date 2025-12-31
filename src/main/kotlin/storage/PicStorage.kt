package me.mikun.storage

import io.ktor.server.application.Application
import io.ktor.server.application.log
import java.io.InputStream
import java.util.concurrent.CopyOnWriteArraySet

abstract class PicStorage {
    protected val picKeys =
        object : CopyOnWriteArraySet<String>() {
            private val extensions = listOf(".jpg", ".jpeg", ".png", ".gif", ".jfif")

            private fun isValid(e: String?): Boolean = e != null && extensions.any { e.endsWith(it, ignoreCase = true) }

            override fun add(e: String?): Boolean = isValid(e) && super.add(e)

            override fun addAll(elements: Collection<String>): Boolean {
                val valid = elements.filter { isValid(it) }
                return super.addAll(valid)
            }
        }

    companion object {
        lateinit var delegate: PicStorage

        fun configure(application: Application) {
            with(application) {
                runCatching {
                    environment.config.let { config ->
                        when (config.property("storage.type").getString()) {
                            "local" -> {
                                delegate =
                                    PicStorageLocal().apply {
                                        init(application)
                                    }
                            }

                            "cos" -> {
                                delegate =
                                    PicStorageCos().apply {
                                        init(application)
                                    }
                            }
                        }
                    }
                }.onFailure { e ->
                    log.error(e.message)
                    throw e
                }

                log.info("PicStorage count: ${delegate.picKeys.size}")
            }
        }

        suspend fun random(): InputStream? = delegate.random()
    }

    abstract fun init(application: Application)

    abstract suspend fun random(): InputStream?
}
