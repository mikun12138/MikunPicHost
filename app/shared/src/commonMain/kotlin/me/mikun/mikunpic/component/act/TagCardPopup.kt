package me.mikun.mikunpic.component.act

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import me.mikun.mikunpic.dto.data.api.OhMyRouting
import kotlin.collections.buildMap

@Composable
fun TagCardPopup(
    show: Boolean,
    onDismissRequest: () -> Unit,
    tag: String,
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

    var page by remember { mutableStateOf(1) }
    val picCountPerPage = 20

    val images by produceState(emptyList(), page) {
        value = buildMap {
            val label2Pics = Client.listPic(
                page = page,
                count = picCountPerPage,
                tagFilter = OhMyRouting.Manage.Pic.Random.TagFilter.All(listOf(tag)),
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
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            FilledIconButton(
                                enabled = page > 1,
                                onClick = {
                                    page -= 1
                                },
                                modifier = Modifier.align(Alignment.CenterStart),
                            ) {
                                Icon(Icons.AutoMirrored.Default.KeyboardArrowLeft, null)
                            }

                            Profile(
                                name = tag,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .fillMaxWidth()
                            )

                            FilledIconButton(
                                enabled = images.size >= picCountPerPage,
                                onClick = {
                                    page += 1
                                },
                                modifier = Modifier.align(Alignment.CenterEnd),
                            ) {
                                Icon(Icons.AutoMirrored.Default.KeyboardArrowRight, null)
                            }
                        }
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
    modifier: Modifier = Modifier,
) {
    Text(
        text = name,
        modifier = modifier,
        textAlign = TextAlign.Center,
    )
}
