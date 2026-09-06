package me.mikun.mikunpic

import java.lang.System.getenv
import java.nio.file.Files
import java.nio.file.Path

abstract class ServerAppDirs {
    abstract val data: String
    abstract val config: String
    abstract val cache: String
    abstract val runtime: String

    enum class DeployType {
        Sys,
        User,
        Portable
        ;

        companion object {
            fun byValue(value: String): DeployType {
                return when (value) {
                    "sys" -> Sys
                    "user" -> User
                    "portable" -> Portable
                    else -> error(
                        "Unknown deploy mode '$value'. Expected: sys, user, or portable",
                    )
                }
            }
        }
    }

    companion object {
        private lateinit var appName: String
        private lateinit var delegate: ServerAppDirs

        val current: ServerAppDirs
            get() = delegate

        fun init(
            appName: String,
            dirs: ServerAppDirs = Sandbox
        ) {
            Companion.appName = appName
            delegate = dirs
        }

        fun init(
            appName: String,
            deployType: DeployType,
        ) {
            Companion.appName = appName
            delegate = when (deployType) {
                DeployType.Sys -> Linux.Sys
                DeployType.User -> Linux.User
                DeployType.Portable -> Linux.Portable
            }
        }

    }

    object Sandbox : ServerAppDirs() {
        private val root by lazy {
            Path.of("sandbox", appName)
        }
        override val data: String by lazy {
            root.resolve("data").toString()
        }

        override val config: String by lazy {
            root.resolve("config").toString()
        }

        override val cache: String by lazy {
            root.resolve("cache").toString()
        }

        override val runtime: String by lazy {
            root.resolve("runtime").toString()
        }
    }

    object Linux {
        object Sys : ServerAppDirs() {
            override val data: String by lazy {
                Path.of(
                    "/var/lib",
                    appName
                ).toString()
            }

            override val config: String by lazy {
                Path.of(
                    "/etc",
                    appName
                ).toString()
            }

            override val cache: String by lazy {
                Path.of(
                    "/var/cache",
                    appName
                ).toString()
            }

            override val runtime: String by lazy {
                Path.of(
                    "/run",
                    appName
                ).toString()
            }
        }

        object User : ServerAppDirs() {
            private fun xdgDir(
                variable: String,
                fallback: String,
            ): String {
                return getenv(variable)
                    ?.let { Path.of(it).takeIf { path -> path.isAbsolute }?.toString() }
                    ?: fallback
            }

            override val data: String by lazy {
                Path.of(
                    xdgDir(
                        "XDG_DATA_HOME",
                        Path.of(
                            System.getProperty("user.home"),
                            ".local",
                            "share"
                        ).toString(),
                    ),
                    appName,
                ).toString()
            }

            override val config: String by lazy {
                Path.of(
                    xdgDir(
                        "XDG_CONFIG_HOME",
                        Path.of(
                            System.getProperty("user.home"),
                            ".config"
                        ).toString(),
                    ),
                    appName,
                ).toString()
            }

            override val cache: String by lazy {
                Path.of(
                    xdgDir(
                        "XDG_CACHE_HOME",
                        Path.of(
                            System.getProperty("user.home"),
                            ".cache"
                        ).toString(),
                    ),
                    appName,
                ).toString()
            }

            override val runtime: String by lazy {
                Path.of(
                    xdgDir(
                        "XDG_RUNTIME_DIR",
                        Path.of(System.getProperty("java.io.tmpdir")).toString(),
                    ),
                    appName,
                ).toString()
            }
        }

        object Portable : ServerAppDirs() {
            private val root: Path by lazy {
                applicationRoot()
            }

            private fun applicationRoot(): Path {
                val location = ServerAppDirs::class.java
                    .protectionDomain
                    ?.codeSource
                    ?.location
                    ?: error("Cannot determine portable application location")

                val locationPath = Path.of(location.toURI())
                    .toAbsolutePath()
                    .normalize()

                return if (Files.isDirectory(locationPath)) {
                    locationPath
                } else {
                    locationPath.parent
                        ?: error("Cannot determine portable application directory")
                }
            }

            override val data: String by lazy {
                root.resolve("data").toString()
            }

            override val config: String by lazy {
                root.resolve("config").toString()
            }

            override val cache: String by lazy {
                root.resolve("cache").toString()
            }

            override val runtime: String by lazy {
                root.resolve("runtime").toString()
            }
        }
    }
}
