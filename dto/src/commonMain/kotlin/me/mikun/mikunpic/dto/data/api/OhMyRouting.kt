package me.mikun.mikunpic.dto.data.api

import io.ktor.resources.Resource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.mikun.mikunpic.dto.data.MikunPicConfig

interface OhMyRouting {
    val parent: Any

    @Resource("/")
    companion object

    @Resource("/random")
    class Random(
        @SerialName("illustrator_id")
        val illustratorIds: List<Int>? = null,
        @SerialName("tag")
        val tags: List<String>? = null,
    ) : OhMyRouting {
        override val parent = OhMyRouting.Companion
    }

    @Resource("/pic")
    class Pic : OhMyRouting {
        override val parent = OhMyRouting.Companion

        enum class Thumbnail {
            Thumb,
            Small,
            Medium,
            Large,
            Orig,
        }

        @Resource("/id/{id}")
        data class Id(
            @SerialName("id")
            val id: String,
            @SerialName("thumbnail")
            val thumbnail: Thumbnail = Thumbnail.Orig,
            @SerialName("storage_label")
            val storageLabel: String,
        ) : OhMyRouting {
            override val parent = Pic()
        }

        @Resource("/{platform}/{key}")
        class PlatformKey(
            val platform: String,
            val key: String,
            val thumbnail: Thumbnail = Thumbnail.Orig,
        ) : OhMyRouting {
            override val parent = Pic()
        }
    }

    @Resource("/manage")
    class Manage : OhMyRouting {
        override val parent = OhMyRouting.Companion

        @Resource("/storage")
        class Storage : OhMyRouting {
            override val parent = Manage()

            @Resource("/list")
            class List : OhMyRouting {
                override val parent = Storage()

                @Serializable
                data class Response(
                    @SerialName("storages")
                    val storages: kotlin.collections.List<me.mikun.mikunpic.dto.data.Storage>,
                )
            }

            @Resource("/add")
            class Add : OhMyRouting {
                override val parent = Storage()

                @Serializable
                data class Body(
                    @SerialName("storage")
                    val storage: me.mikun.mikunpic.dto.data.Storage,
                )
            }

            @Resource("/edit")
            class Edit : OhMyRouting {
                override val parent = Storage()

                @Serializable
                data class Body(
                    @SerialName("storage")
                    val storage: me.mikun.mikunpic.dto.data.Storage,
                )
            }

            @Resource("delete")
            class Delete : OhMyRouting {
                override val parent = Storage()

                @Serializable
                data class Body(
                    @SerialName("storage_label")
                    val storageLabel: String,
                )
            }

            @Resource("/sync")
            class Sync : OhMyRouting {
                override val parent = Manage()

                @Serializable
                data class Body(
                    @SerialName("storage_label")
                    val storageLabel: String,
                    @SerialName("sync_rule_text")
                    val syncRuleText: String,
                )
            }
        }

        @Resource("/config")
        class Config : OhMyRouting {
            override val parent = Manage()

            @Serializable
            data class Body(
                @SerialName("config")
                val mikunPicConfig: MikunPicConfig,
            )
        }

        @Resource("/pic")
        class Pic : OhMyRouting {
            override val parent = Manage()

            @Resource("/upload")
            class Upload : OhMyRouting {
                override val parent = Pic()
            }

            @Resource("/list")
            class List : OhMyRouting {
                override val parent = Pic()

                @Serializable
                data class Body(
                    @SerialName("count")
                    val count: Int,
                    @SerialName("illustrator")
                    val illustrator: IllustratorFilter = IllustratorFilter.Any,
                    @SerialName("tag")
                    val tag: TagFilter = TagFilter.Any,
                    @SerialName("storage_label")
                    val storageLabels: kotlin.collections.List<String> = emptyList(),
                    @SerialName("page")
                    val page: Int = 1,
                )

                @Serializable
                data class Response(
                    @SerialName("pics_by_storage")
                    val label2Pics: Map<String, kotlin.collections.List<me.mikun.mikunpic.dto.data.Pic>> = emptyMap(),
                )
            }


            @Resource("/random")
            class Random : OhMyRouting {
                override val parent = Pic()

                @Serializable
                data class Body(
                    @SerialName("count")
                    val count: Int,
                    @SerialName("illustrator")
                    val illustrator: IllustratorFilter = IllustratorFilter.Any,
                    @SerialName("tag")
                    val tag: TagFilter = TagFilter.Any,
                    @SerialName("storage_label")
                    val storageLabels: kotlin.collections.List<String> = emptyList(),
                )

                @Serializable
                data class Response(
                    @SerialName("pics_by_storage")
                    val label2Pics: Map<String, Set<me.mikun.mikunpic.dto.data.Pic>> = emptyMap(),
                )
            }

            @Serializable
            sealed interface IllustratorFilter {
                @Serializable
                @SerialName("any")
                data object Any : IllustratorFilter

                @Serializable
                @SerialName("none")
                data object None : IllustratorFilter

                @Serializable
                @SerialName("ids")
                data class Ids(
                    @SerialName("ids")
                    val ids: kotlin.collections.List<Int>,
                ) : IllustratorFilter
            }

            @Serializable
            sealed interface TagFilter {
                @Serializable
                @SerialName("any")
                data object Any : TagFilter

                @Serializable
                @SerialName("none")
                data object None : TagFilter

                @Serializable
                @SerialName("all")
                data class All(
                    @SerialName("names")
                    val names: kotlin.collections.List<String>,
                ) : TagFilter
            }

            @Resource("/update")
            class Update : OhMyRouting {
                override val parent = Pic()

                @Serializable
                data class Body(
                    @SerialName("storage_label")
                    val storageLabel: String,
                    @SerialName("pic")
                    val pic: me.mikun.mikunpic.dto.data.PicUpdate,
                )
            }
        }

        @Resource("/illustrator")
        class Illustrator : OhMyRouting {
            override val parent = Manage()

            @Resource("/create")
            class Create : OhMyRouting {
                override val parent = Illustrator()

                @Serializable
                data class Body(
                    @SerialName("illustrator")
                    val illustrator: me.mikun.mikunpic.dto.data.Illustrator,
                )
            }

            @Resource("/search")
            class Search(
                val count: Int,
                val keyword: String? = null,
                val page: Int = 0,
            ) : OhMyRouting {
                override val parent = Illustrator()

                @Serializable
                data class Response(
                    @SerialName("illustrators")
                    val illustrators: List<me.mikun.mikunpic.dto.data.Illustrator>,
                )
            }
        }

        @Resource("/tag")
        class Tag : OhMyRouting {
            override val parent = Manage()

            @Resource("/create")
            class Create : OhMyRouting {
                override val parent = Tag()

                @Serializable
                data class Body(
                    @SerialName("name")
                    val name: String,
                )
            }

            @Resource("/delete")
            class Delete : OhMyRouting {
                override val parent = Tag()

                @Serializable
                data class Body(
                    @SerialName("name")
                    val name: String,
                )
            }

            @Resource("/search")
            class Search(
                val count: Int,
                val keyword: String,
                val page: Int = 0,
            ) : OhMyRouting {
                override val parent = Tag()

                @Serializable
                data class Response(
                    @SerialName("tags")
                    val tags: List<String>,
                )
            }
        }

        @Resource("/backup")
        class Backup : OhMyRouting {
            override val parent = Manage()
        }
    }
}
