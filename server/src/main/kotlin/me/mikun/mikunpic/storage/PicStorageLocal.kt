package me.mikun.mikunpic.storage

import io.ktor.server.application.Application
import io.ktor.util.Digest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.mikun.mikunpic.dto.data.MikunPicConfig
import me.mikun.mikunpic.dto.data.api.OhMyRouting
import java.io.IOException
import java.io.InputStream
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes

class PicStorageLocal(
    override val label: String,
) : PicStorage() {
    lateinit var folderPath: String
    private lateinit var rootPath: Path

    override fun init(
        application: Application,
        storage: MikunPicConfig.Storage,
    ) {
        (storage as? MikunPicConfig.Storage.Local)?.let { storage ->
            val root = Path.of(storage.path).toAbsolutePath().normalize()
            Files.createDirectories(root)
            Files.isDirectory(root) || error("$root is not a dir!")

            rootPath = root.toRealPath()
            folderPath = rootPath.toString()

            flashStorage()
        }
    }

    private fun pathOf(key: String): Path? {
        if (key.isBlank()) return null

        val relativePath = try {
            Path.of(key.replace('\\', '/'))
        } catch (_: InvalidPathException) {
            return null
        }

        if (relativePath.isAbsolute) return null

        val resolvedPath = rootPath.resolve(relativePath).normalize()
        return resolvedPath.takeIf {
            it != rootPath && it.startsWith(rootPath)
        }
    }

    private fun Path.hasSymbolicLink(): Boolean {
        var currentPath = rootPath
        rootPath.relativize(this).forEach {
            currentPath = currentPath.resolve(it)
            if (Files.isSymbolicLink(currentPath)) return true
        }
        return false
    }

    private fun isHidden(path: Path): Boolean = runCatching {
        Files.isHidden(path)
    }.getOrDefault(false)

    // for read
    private fun existingRegularFile(key: String): Path? {
        val path = pathOf(key) ?: return null
        if (path.hasSymbolicLink()) return null

        val realPath = try {
            path.toRealPath(LinkOption.NOFOLLOW_LINKS)
        } catch (_: Exception) {
            return null
        }

        return realPath.takeIf {
            it.startsWith(rootPath) && Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS)
        }
    }

    // for write
    private fun writablePath(key: String): Path? {
        val path = pathOf(key) ?: return null
        val parent = path.parent ?: rootPath

        if (parent.hasSymbolicLink()) return null

        try {
            Files.createDirectories(parent)
        } catch (_: IOException) {
            return null
        }

        if (parent.hasSymbolicLink()) return null
        if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) return null

        val realParent = try {
            parent.toRealPath(LinkOption.NOFOLLOW_LINKS)
        } catch (_: Exception) {
            return null
        }

        if (!realParent.startsWith(rootPath)) return null

        return path
    }

    private fun storageKey(path: Path): String = rootPath.relativize(path)
        .joinToString("/") {
            it.toString()
        }

    override suspend fun random(): InputStream? {
        return withContext(Dispatchers.IO) {
            fun findExist(maxAttempt: Int): InputStream? {
                repeat(maxAttempt) {
                    val key = picKeys.randomOrNull() ?: return null
                    existingRegularFile(key)?.let {
                        return runCatching {
                            Files.newInputStream(it, LinkOption.NOFOLLOW_LINKS)
                        }.getOrNull()
                    }
                }
                return null
            }

            findExist(10)
        }
    }

    override suspend fun hash(
        key: String,
    ): String? {
        val bytes = byKey(key)?.readBytes() ?: return null
        return Digest("md5").let {
            it += bytes
            it.build()
        }.toHexString()
    }

    override suspend fun byKey(
        key: String,
        thumbnail: OhMyRouting.Pic.Thumbnail,
    ): InputStream? = withContext(Dispatchers.IO) {
        existingRegularFile(key)?.let {
            runCatching {
                Files.newInputStream(it, LinkOption.NOFOLLOW_LINKS)
            }.getOrNull()
        }
    }

    override suspend fun upload(
        byteArray: ByteArray,
        storeKey: String,
    ) {
        withContext(Dispatchers.IO) {
            val path = writablePath(storeKey) ?: error("Invalid local storage key: $storeKey")
            if (path.hasSymbolicLink()) error("Invalid local storage key: $storeKey")

            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                println("file: $storeKey already exist!")
            } else {
                Files.write(
                    path,
                    byteArray,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS,
                )
                picKeys.add(storageKey(path))
            }
        }
    }

    fun flashStorage() {
        picKeys.clear()

        Files.walkFileTree(
            rootPath,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(
                    dir: Path,
                    attrs: BasicFileAttributes,
                ): FileVisitResult = if (dir != rootPath && isHidden(dir)) {
                    FileVisitResult.SKIP_SUBTREE
                } else {
                    FileVisitResult.CONTINUE
                }

                override fun visitFile(
                    file: Path,
                    attrs: BasicFileAttributes,
                ): FileVisitResult {
                    if (attrs.isRegularFile && !isHidden(file)) {
                        picKeys.add(storageKey(file))
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(
                    file: Path,
                    exc: IOException,
                ): FileVisitResult = FileVisitResult.CONTINUE
            },
        )
    }
}
