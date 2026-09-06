package me.mikun.mikunpic.database

import me.mikun.mikunpic.database.table.IllustratorTable
import me.mikun.mikunpic.database.table.PicTable
import me.mikun.mikunpic.database.table.PlatformKeyTable
import me.mikun.mikunpic.database.table.TagTable
import me.mikun.mikunpic.database.table.relation.Illustrator2PlatformKeysTable
import me.mikun.mikunpic.database.table.relation.Pic2IllustratorTable
import me.mikun.mikunpic.database.table.relation.Pic2TagsTable
import me.mikun.mikunpic.ServerAppDirs
import me.mikun.mikunpic.dto.data.Illustrator
import me.mikun.mikunpic.dto.data.Pic
import me.mikun.mikunpic.dto.data.PicCreate
import me.mikun.mikunpic.dto.data.PicSelect
import me.mikun.mikunpic.dto.data.PicUpdate
import me.mikun.mikunpic.dto.data.Platform
import me.mikun.mikunpic.dto.data.api.OhMyRouting
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.name
import org.jetbrains.exposed.v1.jdbc.orWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.io.File
import java.sql.Connection
import kotlin.let
import kotlin.use

class StorageDB(
    val db: Database,
) {
    init {
        transaction(db) {
            SchemaUtils.create(
                PicTable,
                Pic2IllustratorTable,
                Pic2TagsTable,
            )
        }
    }

    val nameNoEx
        get() = db.name.removeSuffix(".db")

    val countPic
        get() = transaction(db) {
            PicEntity.count()
        }

    suspend fun createPic(
        pic: PicCreate,
        hash: String,
    ) {
        transaction(db) {
            var newIllustratorId: Int? = null
            val newTagIds = mutableListOf<Int>()
            transaction(MetadataDB.db) {
                pic.illustrator?.let { illustrator ->
                    val illustratorQuery = PlatformKeyTable.join(
                        otherTable = Illustrator2PlatformKeysTable,
                        joinType = JoinType.LEFT,
                        onColumn = PlatformKeyTable.id,
                        otherColumn = Illustrator2PlatformKeysTable.platformkey,
                    ).join(
                        otherTable = IllustratorTable,
                        joinType = JoinType.LEFT,
                        onColumn = Illustrator2PlatformKeysTable.illustrator,
                        otherColumn = IllustratorTable.id,
                    ).selectAll().apply {
                        illustrator.platformKeyMap.forEach { platform, key ->
                            orWhere {
                                (PlatformKeyTable.platform eq platform) and (PlatformKeyTable.key eq key)
                            }
                        }
                    }

                    // create
                    newIllustratorId = illustratorQuery.firstOrNull()?.let {
                        it[IllustratorTable.id].value
                    } ?: IllustratorTable.insert {
                        it[name] = illustrator.name
                    }.let {
                        it[IllustratorTable.id].value
                    }

                    val platformIds = PlatformKeyTable.batchInsert(
                        illustrator.platformKeyMap.toList(),
                        ignore = true,
                    ) { (platform, key) ->
                        this[PlatformKeyTable.platform] = platform
                        this[PlatformKeyTable.key] = key
                    }.mapNotNull {
                        it.getOrNull(PlatformKeyTable.id)?.value
                    }

                    Illustrator2PlatformKeysTable.batchInsert(
                        platformIds,
                        ignore = true,
                    ) {
                        this[Illustrator2PlatformKeysTable.illustrator] = newIllustratorId
                        this[Illustrator2PlatformKeysTable.platformkey] = it
                    }
                }

                // TODO:: use insertReturning?
                TagTable.batchInsert(
                    pic.tags,
                    ignore = true,
                ) {
                    this[TagTable.name] = it
                }

                newTagIds.addAll(
                    TagTable.select(
                        TagTable.id,
                    ).where {
                        TagTable.name inList pic.tags
                    }.map {
                        it[TagTable.id].value
                    },
                )
            }

            val newPicId = PicTable.insert {
                it[PicTable.filename] = pic.filename
                it[PicTable.hash] = hash
                it[PicTable.platform] = Platform.byName(pic.platform) ?: Platform.Other
                it[PicTable.storeKey] = pic.storeKey
                it[PicTable.link] = Platform.byName(pic.platform)?.buildLink(key = pic.filename) ?: ""
            }[PicTable.id].value

            newIllustratorId?.let { newIllustratorId ->
                Pic2IllustratorTable.insert {
                    it[picId] = newPicId
                    it[illustratorId] = newIllustratorId
                }
            }

            Pic2TagsTable.batchInsert(
                newTagIds,
                ignore = true,
            ) {
                this[Pic2TagsTable.picId] = newPicId
                this[Pic2TagsTable.tagId] = it
            }
        }
    }

    suspend fun updatePic(
        pic: PicUpdate,
    ) = transaction(db) {
        PicEntity.findSingleByAndUpdate(PicTable.id eq pic.id.toInt()) { picEntity ->

            var newIllustratorId: Int? = null
            val newTagIds = mutableListOf<Int>()
            transaction(MetadataDB.db) {
                pic.illustrator?.let { illustrator ->
                    val illustratorQuery = PlatformKeyTable.join(
                        otherTable = Illustrator2PlatformKeysTable,
                        joinType = JoinType.LEFT,
                        onColumn = PlatformKeyTable.id,
                        otherColumn = Illustrator2PlatformKeysTable.platformkey,
                    ).join(
                        otherTable = IllustratorTable,
                        joinType = JoinType.LEFT,
                        onColumn = Illustrator2PlatformKeysTable.illustrator,
                        otherColumn = IllustratorTable.id,
                    ).selectAll().apply {
                        illustrator.platformKeyMap.forEach { platform, key ->
                            orWhere {
                                (PlatformKeyTable.platform eq platform) and (PlatformKeyTable.key eq key)
                            }
                        }
                    }

                    // create
                    newIllustratorId = illustratorQuery.firstOrNull()?.let {
                        it[IllustratorTable.id].value
                    } ?: IllustratorTable.insert {
                        it[name] = illustrator.name
                    }.let {
                        it[IllustratorTable.id].value
                    }

                    val platformIds = PlatformKeyTable.batchInsert(
                        illustrator.platformKeyMap.toList(),
                        ignore = true,
                    ) { (platform, key) ->
                        this[PlatformKeyTable.platform] = platform
                        this[PlatformKeyTable.key] = key
                    }.mapNotNull {
                        it.getOrNull(PlatformKeyTable.id)?.value
                    }

                    Illustrator2PlatformKeysTable.batchInsert(
                        platformIds,
                        ignore = true,
                    ) {
                        this[Illustrator2PlatformKeysTable.illustrator] = newIllustratorId
                        this[Illustrator2PlatformKeysTable.platformkey] = it
                    }
                }

                // TODO:: use insertReturning?
                TagTable.batchInsert(
                    pic.tags,
                    ignore = true,
                ) {
                    this[TagTable.name] = it
                }

                newTagIds.addAll(
                    TagTable.select(
                        TagTable.id,
                    ).where {
                        TagTable.name inList pic.tags
                    }.map {
                        it[TagTable.id].value
                    },
                )
            }

            newIllustratorId?.let { newIllustratorId ->
                Pic2IllustratorTable.upsert(
                    keys = arrayOf(Pic2IllustratorTable.picId),
                ) {
                    it[picId] = picEntity.id
                    it[illustratorId] = newIllustratorId
                }
            }

            Pic2TagsTable.deleteWhere {
                (Pic2TagsTable.picId eq picEntity.id) and (Pic2TagsTable.tagId notInList newTagIds)
            }

            Pic2TagsTable.batchInsert(
                newTagIds,
                ignore = true,
            ) {
                this[Pic2TagsTable.picId] = picEntity.id
                this[Pic2TagsTable.tagId] = it
            }
        }
    }

    suspend fun selectPic(
        id: Int,
    ): PicSelect? = transaction(db) {
        PicTable.selectAll().where {
            PicTable.id eq id
        }.firstOrNull()?.let {
            PicSelect(
                id = it[PicTable.id].value,
                filename = it[PicTable.filename],
                platform = it[PicTable.platform].name,
                storeKey = it[PicTable.storeKey],
            )
        }
    }

    suspend fun selectPic(
        platform: Platform,
        key: String,
    ): PicSelect? = transaction(db) {
        PicTable.selectAll().where {
            (PicTable.platform eq platform) and (PicTable.filename eq key)
        }.firstOrNull()?.let {
            PicSelect(
                id = it[PicTable.id].value,
                filename = it[PicTable.filename],
                platform = it[PicTable.platform].name,
                storeKey = it[PicTable.storeKey],
            )
        }
    }

    suspend fun selectPic(
        hash: String,
    ): PicSelect? = transaction(db) {
        PicTable.selectAll().where {
            PicTable.hash eq hash
        }.firstOrNull()?.let {
            PicSelect(
                id = it[PicTable.id].value,
                filename = it[PicTable.filename],
                platform = it[PicTable.platform].name,
                storeKey = it[PicTable.storeKey],
            )
        }
    }

    companion object {
        val dbs = mutableListOf<StorageDB>()

        fun byNameNoEx(
            nameNoEx: String,
        ) = dbs.find { it.nameNoEx == nameNoEx }

        fun random() = dbs.randomOrNull()

        private data class AttachedStorage(
            val label: String,
            val alias: String,
        )

        private data class MutablePic(
            val id: String,
            val storageLabel: String,
            val filename: String,
            val storeKey: String,
            val illustratorId: Int?,
            val illustratorName: String?,
            val platformKeyMap: MutableMap<Platform, String> = linkedMapOf(),
            val tags: MutableSet<String> = linkedSetOf(),
        )

        private fun MutablePic.toPic(): Pic = Pic(
            id = id,
            filename = filename,
            illustrator = illustratorId?.let { illustratorId ->
                illustratorName?.let { illustratorName ->
                    Illustrator(
                        id = illustratorId,
                        name = illustratorName,
                        platformKeyMap = platformKeyMap.toMap(),
                    )
                }
            },
            tags = tags.toList(),
            storeKey = storeKey,
        )

        private fun Map<Pair<String, Int>, MutablePic>.toPicsByStorage(): Map<String, List<Pic>> {
            val picsByStorage = linkedMapOf<String, MutableList<Pic>>()
            values.forEach { pic ->
                picsByStorage.getOrPut(pic.storageLabel) { mutableListOf() } += pic.toPic()
            }
            return picsByStorage.mapValues { (_, pics) -> pics.toList() }
        }

        private data class PreparedSql(
            val sql: String,
            val args: List<Pair<IColumnType<*>, Any?>> = emptyList(),
        )

        private fun stringArg(value: String): Pair<IColumnType<*>, Any?> = TextColumnType() to value

        private fun intArg(value: Int): Pair<IColumnType<*>, Any?> = IntegerColumnType() to value

        private fun placeholders(count: Int) = List(count) { "?" }.joinToString(
            prefix = "(",
            postfix = ")",
        )

        private fun storageDbPath(storageLabel: String): String = File(
            ServerAppDirs.current.data,
            "databases/storage/$storageLabel.db",
        ).path

        private fun sqliteLiteral(value: String): String =
            "'${value.replace("'", "''")}'"

        private fun attachSql(storage: AttachedStorage) = PreparedSql(
            sql = """
                ATTACH DATABASE ?
                AS ${storage.alias}
            """.trimIndent(),
            args = listOf(
                stringArg(storageDbPath(storage.label)),
            ),
        )

        private fun detachSql(storage: AttachedStorage) = "DETACH DATABASE ${storage.alias}"

        private fun illustratorFilterSql(
            illustratorFilter: OhMyRouting.Manage.Pic.IllustratorFilter,
        ): PreparedSql? = when (illustratorFilter) {
            OhMyRouting.Manage.Pic.IllustratorFilter.Any -> null

            OhMyRouting.Manage.Pic.IllustratorFilter.None -> PreparedSql(
                sql = "pic2illustrator.illustrator_id IS NULL",
            )

            is OhMyRouting.Manage.Pic.IllustratorFilter.Ids -> {
                val illustratorIds = illustratorFilter.ids.toSet()
                if (illustratorIds.isEmpty()) {
                    null
                } else {
                    PreparedSql(
                        sql = "pic2illustrator.illustrator_id IN ${placeholders(illustratorIds.size)}",
                        args = illustratorIds.map(::intArg),
                    )
                }
            }
        }

        private fun tagFilterSql(
            storage: AttachedStorage,
            tagFilter: OhMyRouting.Manage.Pic.TagFilter,
        ): PreparedSql? = when (tagFilter) {
            OhMyRouting.Manage.Pic.TagFilter.Any -> null

            OhMyRouting.Manage.Pic.TagFilter.None -> PreparedSql(
                sql = """
                    NOT EXISTS (
                        SELECT 1
                        FROM ${storage.alias}.pics2tags AS pic_tag
                        WHERE pic_tag.pic_id = pic.id
                    )
                """.trimIndent(),
            )

            is OhMyRouting.Manage.Pic.TagFilter.All -> {
                val (requiredNames, excludedNames) = tagFilter.splitTagNames()
                val conditions = mutableListOf<String>()
                val args = mutableListOf<Pair<IColumnType<*>, Any?>>()

                if (requiredNames.isNotEmpty()) {
                    conditions += """
                        (
                            SELECT COUNT(DISTINCT tag.name)
                            FROM ${storage.alias}.pics2tags AS pic_tag
                            JOIN tag
                                ON tag.id = pic_tag.tag_id
                            WHERE pic_tag.pic_id = pic.id
                                AND tag.name IN ${placeholders(requiredNames.size)}
                        ) = ?
                    """.trimIndent()
                    args += requiredNames.map(::stringArg)
                    args += intArg(requiredNames.size)
                }

                if (excludedNames.isNotEmpty()) {
                    conditions += """
                        NOT EXISTS (
                            SELECT 1
                            FROM ${storage.alias}.pics2tags AS pic_tag
                            JOIN tag
                                ON tag.id = pic_tag.tag_id
                            WHERE pic_tag.pic_id = pic.id
                                AND tag.name IN ${placeholders(excludedNames.size)}
                        )
                    """.trimIndent()
                    args += excludedNames.map(::stringArg)
                }

                conditions.takeIf { it.isNotEmpty() }?.let {
                    PreparedSql(
                        sql = it.joinToString(
                            prefix = "(",
                            separator = "\nAND\n",
                            postfix = ")",
                        ),
                        args = args,
                    )
                }
            }
        }

        private fun candidateSql(
            storage: AttachedStorage,
            illustratorFilter: OhMyRouting.Manage.Pic.IllustratorFilter,
            tagFilter: OhMyRouting.Manage.Pic.TagFilter,
        ): PreparedSql {
            val args = mutableListOf(stringArg(storage.label))
            val conditions = buildList {
                illustratorFilterSql(
                    illustratorFilter = illustratorFilter,
                )?.let { illustratorFilter ->
                    add(illustratorFilter.sql)
                    args += illustratorFilter.args
                }

                tagFilterSql(
                    storage = storage,
                    tagFilter = tagFilter,
                )?.let { tagFilter ->
                    add(tagFilter.sql)
                    args += tagFilter.args
                }
            }
            val where = if (conditions.isEmpty()) {
                ""
            } else {
                conditions.joinToString(
                    prefix = "\nWHERE\n",
                    separator = "\nAND\n",
                )
            }

            return PreparedSql(
                sql = """
                    SELECT
                        ? AS storage_label,
                        pic.id AS pic_id,
                        pic.filename AS filename,
                        pic.store_key AS store_key,
                        pic2illustrator.illustrator_id AS illustrator_id
                    FROM ${storage.alias}.pic
                    LEFT JOIN ${storage.alias}.pic2illustrator
                        ON pic2illustrator.pic_id = pic.id
                    $where
                """.trimIndent(),
                args = args,
            )
        }

        private fun pickedTagsSql(
            storage: AttachedStorage,
        ) = PreparedSql(
            sql = """
                SELECT
                    ? AS storage_label,
                    pics2tags.pic_id AS pic_id,
                    tag.name AS tag_name
                FROM ${storage.alias}.pics2tags
                JOIN picked
                    ON picked.storage_label = ?
                        AND picked.pic_id = pics2tags.pic_id
                LEFT JOIN tag
                    ON tag.id = pics2tags.tag_id
            """.trimIndent(),
            args = listOf(
                stringArg(storage.label),
                stringArg(storage.label),
            ),
        )

        private fun picSelectionSql(
            storages: List<AttachedStorage>,
            count: Int,
            page: Int,
            randomOrder: Boolean,
            illustratorFilter: OhMyRouting.Manage.Pic.IllustratorFilter,
            tagFilter: OhMyRouting.Manage.Pic.TagFilter,
        ): PreparedSql {
            val candidates = storages.map { storage ->
                candidateSql(
                    storage = storage,
                    illustratorFilter = illustratorFilter,
                    tagFilter = tagFilter,
                )
            }
            val pickedTags = storages.map(::pickedTagsSql)
            val offset = (page.coerceAtLeast(1) - 1) * count
            val args = candidates.flatMap { it.args } +
                intArg(count) +
                (if (randomOrder) emptyList() else listOf(intArg(offset))) +
                pickedTags.flatMap { it.args }

            val pickedSql = if (randomOrder) {
                """
                    picked AS (
                        SELECT
                            storage_label,
                            pic_id,
                            filename,
                            store_key,
                            illustrator_id,
                            random() AS sort_key
                        FROM candidate
                        ORDER BY sort_key
                        LIMIT ?
                    ),
                """.trimIndent()
            } else {
                """
                    picked AS (
                        SELECT
                            storage_label,
                            pic_id,
                            filename,
                            store_key,
                            illustrator_id
                        FROM candidate
                        ORDER BY storage_label, pic_id
                        LIMIT ?
                        OFFSET ?
                    ),
                """.trimIndent()
            }

            val pickedOrder = if (randomOrder) {
                "picked.sort_key"
            } else {
                "picked.storage_label, picked.pic_id"
            }

            return PreparedSql(
                sql = """
                    WITH candidate AS (
                        ${
                    candidates.joinToString(
                        separator = "\nUNION ALL\n",
                    ) { it.sql }
                }
                    ),
                    $pickedSql
                    picked_tags AS (
                        ${
                    pickedTags.joinToString(
                        separator = "\nUNION ALL\n",
                    ) { it.sql }
                }
                    )
                    SELECT
                        picked.storage_label,
                        picked.pic_id,
                        picked.filename,
                        picked.store_key,
                        picked.illustrator_id,
                        illustrator.name AS illustrator_name,
                        platform_key.platform AS platform,
                        platform_key.key AS platform_key,
                        picked_tags.tag_name
                    FROM picked
                    LEFT JOIN illustrator
                        ON illustrator.id = picked.illustrator_id
                    LEFT JOIN illustrator2platform_keys
                        ON illustrator2platform_keys.illustrator = illustrator.id
                    LEFT JOIN platform_key
                        ON platform_key.id = illustrator2platform_keys.platform_key
                    LEFT JOIN picked_tags
                        ON picked_tags.storage_label = picked.storage_label
                            AND picked_tags.pic_id = picked.pic_id
                    ORDER BY $pickedOrder
                """.trimIndent(),
                args = args,
            )
        }

        private suspend fun queryPics(
            storageLabels: Set<String>,
            count: Int,
            page: Int,
            randomOrder: Boolean,
            illustratorFilter: OhMyRouting.Manage.Pic.IllustratorFilter,
            tagFilter: OhMyRouting.Manage.Pic.TagFilter = OhMyRouting.Manage.Pic.TagFilter.Any,
        ): Map<String, List<Pic>> {
            if (count <= 0) return emptyMap()

            val storageLabels = storageLabels.filter { it.isNotEmpty() }
            val storages = if (storageLabels.isEmpty()) {
                dbs.toList()
            } else {
                storageLabels.mapNotNull { byNameNoEx(it) }
            }
            if (storages.isEmpty()) return emptyMap()

            val attachedStorages = storages.mapIndexed { index, storage ->
                AttachedStorage(
                    label = storage.nameNoEx,
                    alias = "storage_$index",
                )
            }

            return transaction(MetadataDB.db) {
                val attached = mutableListOf<AttachedStorage>()

                try {
                    attachedStorages.forEach { storage ->
                        attachSql(storage).let { preparedSql ->
                            exec(
                                stmt = preparedSql.sql,
                                args = preparedSql.args,
                            )
                        }
                        attached += storage
                    }

                    val preparedSql = picSelectionSql(
                        storages = attachedStorages,
                        count = count,
                        page = page,
                        randomOrder = randomOrder,
                        illustratorFilter = illustratorFilter,
                        tagFilter = tagFilter,
                    )

                    exec(
                        stmt = preparedSql.sql,
                        args = preparedSql.args,
                        explicitStatementType = StatementType.SELECT,
                    ) { resultSet ->
                        val result = linkedMapOf<Pair<String, Int>, MutablePic>()

                        while (resultSet.next()) {
                            val storageLabel = resultSet.getString("storage_label")
                            val picId = resultSet.getInt("pic_id")
                            val key = storageLabel to picId
                            val pic = result.getOrPut(key) {
                                MutablePic(
                                    id = picId.toString(),
                                    storageLabel = storageLabel,
                                    filename = resultSet.getString("filename"),
                                    storeKey = resultSet.getString("store_key"),
                                    illustratorId = (resultSet.getObject("illustrator_id") as? Number)?.toInt(),
                                    illustratorName = resultSet.getString("illustrator_name"),
                                )
                            }

                            (resultSet.getObject("platform") as? Number)?.toInt()?.let { ordinal ->
                                Platform.entries.getOrNull(ordinal)?.let { platform ->
                                    resultSet.getString("platform_key")?.let { platformKey ->
                                        pic.platformKeyMap[platform] = platformKey
                                    }
                                }
                            }

                            resultSet.getString("tag_name")?.let { pic.tags += it }
                        }

                        result.toPicsByStorage()
                    }.orEmpty()
                } finally {
                    attached.asReversed().forEach { storage ->
                        runCatching {
                            exec(detachSql(storage))
                        }
                    }
                }
            }
        }

        suspend fun randomPic(
            storageLabels: Set<String>,
            count: Int,
            illustratorFilter: OhMyRouting.Manage.Pic.IllustratorFilter,
            tagFilter: OhMyRouting.Manage.Pic.TagFilter = OhMyRouting.Manage.Pic.TagFilter.Any,
        ): Map<String, Set<Pic>> = queryPics(
            storageLabels = storageLabels,
            count = count,
            page = 0,
            randomOrder = true,
            illustratorFilter = illustratorFilter,
            tagFilter = tagFilter,
        ).mapValues { (_, pics) -> pics.toSet() }

        suspend fun listPic(
            storageLabels: Set<String>,
            count: Int,
            page: Int,
            illustratorFilter: OhMyRouting.Manage.Pic.IllustratorFilter,
            tagFilter: OhMyRouting.Manage.Pic.TagFilter = OhMyRouting.Manage.Pic.TagFilter.Any,
        ): Map<String, List<Pic>> = queryPics(
            storageLabels = storageLabels,
            count = count,
            page = page,
            randomOrder = false,
            illustratorFilter = illustratorFilter,
            tagFilter = tagFilter,
        )

        suspend fun backup() {
            dbs.forEach { db ->
                (db.db.connector().connection as Connection).use { connection ->
                    connection.createStatement().use { statement ->
                        val sql = "VACUUM INTO ${sqliteLiteral(
                            File(
                                ServerAppDirs.current.data,
                                "databases/${db.nameNoEx}.db.bak",
                            ).path,
                        )}"
                        statement.executeUpdate(sql)
                    }
                }
            }
        }
    }
}


data class TagFilterNames(
    val required: Set<String>,
    val excluded: Set<String>,
)

fun OhMyRouting.Manage.Pic.TagFilter.All.splitTagNames(): TagFilterNames {
    val required = linkedSetOf<String>()
    val excluded = linkedSetOf<String>()

    names.forEach { rawName ->
        if (rawName.isBlank()) {
            return@forEach
        }

        if (rawName.startsWith("!")) {
            val excludedName = rawName.removePrefix("!")
            if (excludedName.isNotBlank()) {
                excluded += excludedName
            }
        } else {
            required += rawName
        }
    }

    return TagFilterNames(
        required = required,
        excluded = excluded,
    )
}
