package me.mikun.mikunpic.component.act

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.mikun.mikunpic.client.Client
import me.mikun.mikunpic.component.card.AcrylicCard
import me.mikun.mikunpic.component.image.SizeCachedImage
import me.mikun.mikunpic.dto.data.Illustrator
import me.mikun.mikunpic.dto.data.api.OhMyRouting
import me.mikun.mikunpic.dto.data.api.OhMyRouting.Manage.Pic.Random.IllustratorFilter
import kotlin.collections.buildMap

@Composable
fun IllustratorCardPopup(
    show: Boolean,
    onDismissRequest: () -> Unit,
    illustrator: Illustrator,
) {
    var showPicCardPopup by remember { mutableStateOf(false) }
    var picUrlToPopup by remember { mutableStateOf<String?>(null) }
    if (showPicCardPopup && picUrlToPopup != null) {
        PicCardPopup(
            show = showPicCardPopup,
            onDismissRequest = {
                showPicCardPopup = false
                picUrlToPopup = null
            },
            picUrl = picUrlToPopup!!,
        )
    }

    val images by produceState(emptyList()) {
        value = buildMap {
            val label2Pics = Client.listPic(
                count = Int.MAX_VALUE,
                illustratorFilter = illustrator.id?.let { IllustratorFilter.Ids(listOf(it)) }
                    ?: IllustratorFilter.Any,
            )?.label2Pics
            label2Pics?.forEach { (label, pics) ->
                pics.forEach {
                    put(it.id, label)
                }
            }
        }.toList()
    }

    if (show) {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            AcrylicCard(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.85f),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Profile(illustrator.name)
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        HorizontalDivider()
                    }

                    items(images) { (picId, label) ->
                        val picUrl = Client.buildPicLink(
                            id = picId,
                            thumbnail = OhMyRouting.Pic.Thumbnail.Large,
                            storageLabel = label,
                        )

                        SizeCachedImage(
                            picUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    showPicCardPopup = true
                                    picUrlToPopup = picUrl
                                },
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Profile(
    name: String,
) {
    Text(
        text = name,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}
