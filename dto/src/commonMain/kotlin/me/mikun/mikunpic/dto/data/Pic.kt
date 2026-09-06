package me.mikun.mikunpic.dto.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Pic constructor(
    @SerialName("id")
    val id: String,
    @SerialName("filename")
    val filename: String,
    @SerialName("illustrator")
    val illustrator: Illustrator?,
    @SerialName("tags")
    val tags: List<String> = emptyList(),
    @SerialName("store_key")
    val storeKey: String,
) {
    fun update() = PicUpdate(
        id = this.id,
        illustrator = this.illustrator,
        tags = this.tags,
    )
}

@Serializable
data class PicSelect(
    @SerialName("id")
    val id: Int,
    @SerialName("filename")
    val filename: String,
    @SerialName("platform")
    val platform: String,
    @SerialName("store_key")
    val storeKey: String,
)

@Serializable
data class PicCreate(
    @SerialName("filename")
    val filename: String,
    @SerialName("store_key")
    val storeKey: String,
    @SerialName("platform")
    val platform: String,
    @SerialName("illustrator")
    val illustrator: Illustrator?,
    @SerialName("tags")
    val tags: List<String> = emptyList(),
)

@Serializable
data class PicUpdate(
    @SerialName("id")
    val id: String,
    @SerialName("illustrator")
    val illustrator: Illustrator?,
    @SerialName("tags")
    val tags: List<String> = emptyList(),
)
