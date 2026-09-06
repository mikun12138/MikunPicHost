package me.mikun.mikunpic.view.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme.typography
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
import androidx.compose.ui.unit.dp
import me.mikun.mikunpic.client.Client
import me.mikun.mikunpic.component.act.IllustratorCardPopup
import me.mikun.mikunpic.component.act.PicCardPopup
import me.mikun.mikunpic.component.card.AcrylicCard
import me.mikun.mikunpic.component.image.SizeCachedImage
import me.mikun.mikunpic.dto.data.Illustrator
import me.mikun.mikunpic.dto.data.api.OhMyRouting

@Composable
fun EditTableIllustrator() {
    var showIllustratorCardPopup by remember { mutableStateOf(false) }
    var illustratorToPopup by remember { mutableStateOf<Illustrator?>(null) }
    if (showIllustratorCardPopup && illustratorToPopup != null) {
        IllustratorCardPopup(
            show = showIllustratorCardPopup,
            onDismissRequest = {
                showIllustratorCardPopup = false
                illustratorToPopup = null
            },
            illustrator = illustratorToPopup!!,
        )
    }

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

    val illustratorCount = 20

    val illustrators by produceState(emptyList()) {
        value = Client.searchIllustrator(
            count = illustratorCount,
        )?.illustrators ?: emptyList()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
    ) {
        LazyHorizontalGrid(
            rows = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = illustrators,
                key = { it.id ?: it.name },
            ) {
                IllustratorCard(
                    it,
                    onClick = {
                        showIllustratorCardPopup = true
                        illustratorToPopup = it
                    },
                    onCLickPic = { picUrl ->
                        showPicCardPopup = true
                        picUrlToPopup = picUrl
                    },
                )
            }
        }
    }
}

@Composable
private fun IllustratorCard(
    illustrator: Illustrator?,
    onClick: () -> Unit,
    onCLickPic: (String) -> Unit,
) {
    val picPreviewCount = 5
    val images by produceState(emptyList(), illustrator?.id) {
        illustrator?.let {
            value = buildMap {
                val label2Pics = Client.randomPic(
                    count = picPreviewCount,
                    illustratorFilter = illustrator.id?.let { OhMyRouting.Manage.Pic.IllustratorFilter.Ids(listOf(it)) }
                        ?: OhMyRouting.Manage.Pic.IllustratorFilter.Any,
                )?.label2Pics
                label2Pics?.forEach { (label, pics) ->
                    pics.forEach {
                        put(it.id, label)
                    }
                }
            }.toList()
        }
    }

    AcrylicCard(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                8.dp,
                alignment = Alignment.CenterVertically,
            ),
        ) {
            Box(
                contentAlignment = Alignment.BottomCenter,
            ) {
                Text(
                    text = illustrator?.name ?: "Loading...",
                    style = typography.headlineMedium,
                )
            }

            HorizontalDivider()

            Box(
                contentAlignment = Alignment.Center,
            ) {
                LazyRow(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(
                        images,
                    ) { (id, label) ->
                        val picUrl = Client.buildPicLink(
                            id = id,
                            thumbnail = OhMyRouting.Pic.Thumbnail.Thumb,
                            storageLabel = label,
                        )

                        SizeCachedImage(
                            data = picUrl,
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onCLickPic(picUrl)
                                },
                        )
                    }
                }
            }
        }
    }
}
