package me.mikun.mikunpic.view.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.size.Size
import kotlinx.coroutines.launch
import me.mikun.mikunpic.client.Client
import me.mikun.mikunpic.component.act.PicCardPopup
import me.mikun.mikunpic.component.image.SizeCachedImage
import me.mikun.mikunpic.dto.data.api.OhMyRouting
import me.mikun.mikunpic.viewmodel.EditTableTagViewModel
import me.mikun.mikunpic.viewmodel.ManageViewModel

private enum class EditMode {
    None,
    Remove,
    Add,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTableTag(
    viewModel: EditTableTagViewModel = viewModel { EditTableTagViewModel() },
    manageViewModel: ManageViewModel = viewModel { ManageViewModel() },
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

    val scope = rememberCoroutineScope()

    val tags by viewModel.tags.collectAsState()
    val imagePage by viewModel.imagePage.collectAsState()
    val canLoadNextImagePage by viewModel.canLoadNextImagePage.collectAsState()
    val isImagePageLoading by viewModel.isImagePageLoading.collectAsState()

    var editMode by remember { mutableStateOf(EditMode.None) }

    val tagToRemove = remember { mutableStateListOf<String>() }

    val currentStorageLabel by manageViewModel.currentStorageLabel.collectAsState()
    LaunchedEffect(currentStorageLabel) {
        viewModel.updateTags()
        viewModel.updateImageShowing(
            currentStorageLabel,
            page = 1,
        )
    }

    LaunchedEffect(editMode) {
        viewModel.flashTagsSelected()
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row {
            when (editMode) {
                EditMode.None -> {
                    FilledIconButton(
                        enabled = !isImagePageLoading && imagePage > 1,
                        onClick = {
                            viewModel.previousImagePage(currentStorageLabel)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous page",
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            tagToRemove.clear()
                            editMode = EditMode.Remove
                        },
                    ) {
                        Text("-")
                    }

                    FilledTonalButton(
                        onClick = {
                            editMode = EditMode.Add
                        },
                    ) {
                        Text("+")
                    }

                    FilledIconButton(
                        enabled = !isImagePageLoading && canLoadNextImagePage,
                        onClick = {
                            viewModel.nextImagePage(currentStorageLabel)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next page",
                        )
                    }
                }

                EditMode.Remove -> {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                tagToRemove.forEach {
                                    Client.deleteTag(
                                        it,
                                    )
                                }
                                viewModel.updateTags()
                            }
                            editMode = EditMode.None
                        },
                    ) {
                        Text("OK")
                    }
                }

                EditMode.Add -> {
                    val tagToAdd = rememberTextFieldState()
                    TextField(
                        tagToAdd,
                    )

                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                Client.createTag(
                                    tagToAdd.text.toString(),
                                )
                                viewModel.updateTags()
                            }
                            editMode = EditMode.None
                        },
                    ) {
                        Text("OK")
                    }
                }

                else -> {
                }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            maxLines = 3,
        ) {
            when (editMode) {
                EditMode.Remove -> {
                    tags.forEach { tag ->
                        ElevatedFilterChip(
                            tagToRemove.contains(tag),
                            onClick = {
                                tagToRemove.remove(tag) || tagToRemove.add(tag)
                            },
                            label = {
                                Text(tag)
                            },
                        )
                    }
                }

                else -> {
                    val tagsSelected = viewModel.tagsSelected.collectAsState().value

                    tags.forEach { tag ->
                        ElevatedFilterChip(
                            tagsSelected.contains(tag),
                            onClick = {
                                viewModel.toggleTagsSelected(tag)
                                viewModel.updateImageShowing(
                                    currentStorageLabel,
                                    page = 1,
                                )
                            },
                            label = {
                                Text(tag)
                            },
                        )
                    }
                }
            }
        }

        val imagesShowing = viewModel.imageShowing.collectAsState().value
        val imageScrollState = rememberScrollState()
        val imageRows = remember(imagesShowing) {
            List(2) { row ->
                imagesShowing.filterIndexed { index, _ ->
                    index % 2 == row
                }
            }
        }

        LaunchedEffect(imagesShowing) {
            imageScrollState.scrollTo(0)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .horizontalScroll(imageScrollState)
                .padding(
                    horizontal = 12.dp,
                    vertical = 8.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            imageRows.forEach { rowImages ->
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowImages.forEach { (storageLabel, picId) ->
                        val picUrl = remember(storageLabel, picId) {
                            Client.buildPicLink(
                                id = picId,
                                thumbnail = OhMyRouting.Pic.Thumbnail.Thumb,
                                storageLabel = storageLabel,
                            )
                        }

                        var imageAspectRatio by remember(picUrl) { mutableStateOf(1f) }
                        var isAspectRatioLocked by remember(picUrl) { mutableStateOf(false) }

                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(
                                    ratio = imageAspectRatio,
                                    matchHeightConstraintsFirst = true,
                                )
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            SizeCachedImage(
                                data = picUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable {
                                        showPicCardPopup = true
                                        picUrlToPopup = picUrl
                                    },
                                onSuccess = {
                                    if (!isAspectRatioLocked) {
                                        val intrinsicSize = it.painter.intrinsicSize
                                        if (
                                            intrinsicSize.width.isFinite() &&
                                            intrinsicSize.height.isFinite() &&
                                            intrinsicSize.width > 0f &&
                                            intrinsicSize.height > 0f
                                        ) {
                                            imageAspectRatio =
                                                intrinsicSize.width / intrinsicSize.height
                                            isAspectRatioLocked = true
                                        }
                                    }
                                },
                                contentScale = ContentScale.Fit,
                                sizeChangeDebounceMillis = 250L,
                                requestBuilder = {
                                    size(Size.ORIGINAL)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
