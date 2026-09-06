package me.mikun.mikunpic.view.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedToggleButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TonalToggleButton
import androidx.compose.material3.rememberContainedSearchBarState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import io.ktor.http.URLBuilder
import io.ktor.http.path
import io.ktor.http.takeFrom
import kotlinx.coroutines.launch
import me.mikun.mikunpic.LocalConfig
import me.mikun.mikunpic.client.Client
import me.mikun.mikunpic.component.act.IllustratorCardPopup
import me.mikun.mikunpic.component.act.TagCardPopup
import me.mikun.mikunpic.component.card.AcrylicCard
import me.mikun.mikunpic.dto.data.Illustrator
import me.mikun.mikunpic.dto.data.Pic
import me.mikun.mikunpic.dto.data.api.OhMyRouting
import me.mikun.mikunpic.viewmodel.ManageViewModel
import kotlin.collections.emptyList

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditTablePic(
    manageViewModel: ManageViewModel = viewModel { ManageViewModel() },
) {
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
    var showTagCardPopup by remember { mutableStateOf(false) }
    var tagToPopup by remember { mutableStateOf<String?>(null) }
    if (showTagCardPopup && tagToPopup != null) {
        TagCardPopup(
            show = showTagCardPopup,
            onDismissRequest = {
                showTagCardPopup = false
                tagToPopup = null
            },
            tag = tagToPopup!!,
        )
    }

    val scope = rememberCoroutineScope()

    val localPlatformContext = LocalPlatformContext.current

    var picOnTable by remember { mutableStateOf<Pic?>(null) }

    var storageLabelOnTable by remember { mutableStateOf("") }

    val currentStorageLabel = manageViewModel.currentStorageLabel.collectAsState().value

    fun Map<String, Set<Pic>>.firstPicWithStorage(): Pair<String, Pic>? {
        for ((storageLabel, pics) in this) {
            val pic = pics.firstOrNull()
            if (pic != null) {
                return storageLabel to pic
            }
        }

        return null
    }

    LaunchedEffect(currentStorageLabel) {
        val (storageLabel, pic) = Client.randomPic(
            count = 1,
            storageLabels = listOf(currentStorageLabel),
        )?.label2Pics?.firstPicWithStorage() ?: ("" to null)
        storageLabelOnTable = storageLabel
        picOnTable = pic
    }

    val editingTags =
        remember(picOnTable) { picOnTable?.tags?.toMutableStateList() ?: mutableStateListOf() }
    val isEdited by remember(
        picOnTable,
        editingTags,
    ) {
        derivedStateOf {
            picOnTable != null && picOnTable?.tags?.toSet() != editingTags.toSet()
        }
    }

    var showBottomSheetTag by remember { mutableStateOf(false) }

    var headerSelectionIndex by remember { mutableStateOf(0) }
    val onHeaderSelectionsClicked = listOf(
        {
            headerSelectionIndex = 0
            scope.launch {
                val (storageLabel, pic) = Client.randomPic(
                    count = 1,
                    storageLabels = listOf(currentStorageLabel),
                )?.label2Pics?.firstPicWithStorage() ?: ("" to null)
                storageLabelOnTable = storageLabel
                picOnTable = pic
            }
            Unit
        },
        {
            headerSelectionIndex = 1
            scope.launch {
                val (storageLabel, pic) = Client.randomPic(
                    count = 1,
                    storageLabels = listOf(currentStorageLabel),
                    illustratorFilter = OhMyRouting.Manage.Pic.IllustratorFilter.None,
                )?.label2Pics?.firstPicWithStorage() ?: ("" to null)
                storageLabelOnTable = storageLabel
                picOnTable = pic
            }
            Unit
        },
        {
            headerSelectionIndex = 2
            scope.launch {
                val (storageLabel, pic) = Client.randomPic(
                    count = 1,
                    storageLabels = listOf(currentStorageLabel),
                    tagFilter = OhMyRouting.Manage.Pic.TagFilter.None,
                )?.label2Pics?.firstPicWithStorage() ?: ("" to null)
                storageLabelOnTable = storageLabel
                picOnTable = pic
            }
            Unit
        },
    )
    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HeaderSelection(
                headerSelectionIndex,
                onSelectionRandom = onHeaderSelectionsClicked[0],
                onSelectionNoAuthor = onHeaderSelectionsClicked[1],
                onSelectionNoTag = onHeaderSelectionsClicked[2],
            )

            val currentPic = picOnTable
            if (currentPic == null) {
                AcrylicCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No picture",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                val picUrl = URLBuilder().apply {
                    takeFrom(LocalConfig.current.server)
                    path("pic", "id", currentPic.id)
                    parameters.append("storage_label", storageLabelOnTable)
                }.buildString()
                val imageRequest = remember(
                    localPlatformContext,
                    picUrl,
                    storageLabelOnTable,
                    currentPic.id,
                ) {
                    ImageRequest.Builder(localPlatformContext)
                        .data(picUrl)
                        .size(Size.ORIGINAL)
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .memoryCacheKey("$storageLabelOnTable:${currentPic.id}")
                        .crossfade(true)
                        .build()
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    val openTagEditor: () -> Unit = {
                        showBottomSheetTag = true
                    }
                    val applyEdit: () -> Unit = {
                        if (isEdited) {
                            val updatedPic = currentPic.copy(
                                illustrator = currentPic.illustrator,
                                tags = editingTags.toList(),
                            )
                            picOnTable = updatedPic

                            scope.launch {
                                Client.updatePic(
                                    storageLabel = storageLabelOnTable,
                                    updatedPic.update(),
                                )
                            }
                        }
                    }
                    val onNext: () -> Unit = {
                        applyEdit()
                        onHeaderSelectionsClicked[headerSelectionIndex]()
                    }

                    if (maxWidth < 720.dp) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            PicPreviewPanel(
                                model = imageRequest,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.62f),
                            )

                            PicDetailsPanel(
                                pic = currentPic,
                                storageLabel = storageLabelOnTable,
                                tags = editingTags,
                                onIllustratorChipClicked = {
                                    showIllustratorCardPopup = true
                                    illustratorToPopup = it
                                },
                                onTagChipClicked = {
                                    showTagCardPopup = true
                                    tagToPopup = it
                                },
                                isEdited = isEdited,
                                onEditTags = openTagEditor,
                                onApply = applyEdit,
                                onNext = onNext,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.38f),
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            PicPreviewPanel(
                                model = imageRequest,
                                modifier = Modifier
                                    .weight(0.68f)
                                    .fillMaxHeight(),
                            )

                            PicDetailsPanel(
                                pic = currentPic,
                                storageLabel = storageLabelOnTable,
                                tags = editingTags,
                                onIllustratorChipClicked = {
                                    showIllustratorCardPopup = true
                                    illustratorToPopup = currentPic.illustrator
                                },
                                onTagChipClicked = {
                                    showTagCardPopup = true
                                    tagToPopup = it
                                },
                                isEdited = isEdited,
                                onEditTags = openTagEditor,
                                onApply = applyEdit,
                                onNext = onNext,
                                modifier = Modifier
                                    .weight(0.32f)
                                    .fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }

        SearchBottomSheet(
            showBottomSheetTag,
            onCloseSheet = {
                showBottomSheetTag = false
            },
        ) {
            EditPicTagsSheet(
                onEditTag = {
                    editingTags.apply {
                        remove(it) || add(it)
                    }
                },
                picTags = picOnTable?.tags,
                editContextTags = editingTags,
            )
        }
    }
}

@Composable
private fun PicPreviewPanel(
    model: Any,
    modifier: Modifier = Modifier,
) {
    AcrylicCard(
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            PicShowingTable(model)
        }
    }
}

@Composable
private fun PicDetailsPanel(
    pic: Pic,
    storageLabel: String,
    tags: List<String>,
    onIllustratorChipClicked: (Illustrator) -> Unit,
    onTagChipClicked: (String) -> Unit,
    isEdited: Boolean,
    onEditTags: () -> Unit,
    onApply: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    AcrylicCard(
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Picture",
                    style = MaterialTheme.typography.titleMedium,
                )

                HorizontalDivider()

                DetailRow(
                    label = "ID",
                    value = pic.id,
                )

                DetailRow(
                    label = "File",
                    value = pic.filename,
                )

                DetailRow(
                    label = "Storage",
                    value = storageLabel,
                )

                SectionLabel("Illustrator")

                pic.illustrator?.let { illustrator ->
                    ElevatedAssistChip(
                        onClick = {
                            onIllustratorChipClicked(illustrator)
                        },
                        label = {
                            Text(
                                text = illustrator.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        modifier = Modifier.widthIn(max = 220.dp),
                    )
                }

                SectionLabel("Tags")

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (tags.isEmpty()) {
                        Text(
                            text = "No tags",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        tags.forEach { tag ->
                            ElevatedAssistChip(
                                onClick = {
                                    onTagChipClicked(tag)
                                },
                                label = {
                                    ChipText(tag)
                                },
                                modifier = Modifier.widthIn(max = 220.dp),
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = 12.dp),
            )

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(
                    space = 8.dp,
                    alignment = Alignment.End,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ElevatedButton(
                    onClick = onEditTags,
                ) {
                    Text("Edit Tags")
                }

                ElevatedButton(
                    onClick = onApply,
                    enabled = isEdited,
                ) {
                    Text("Apply")
                }

                ElevatedButton(
                    onClick = onNext,
                ) {
                    Text("Next")
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(
    text: String,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ChipText(
    text: String,
) {
    Text(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchBottomSheet(
    showBottomSheet: Boolean,
    onCloseSheet: () -> Unit,
    innerEditSheet: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()

    val bottomSheetState = rememberModalBottomSheetState()
    if (showBottomSheet) {
        ModalBottomSheet(
            modifier = Modifier
                .fillMaxHeight()
                .padding(8.dp),
            onDismissRequest = {
                scope.launch { bottomSheetState.hide() }.invokeOnCompletion {
                    if (!bottomSheetState.isVisible) {
                        onCloseSheet()
                    }
                }
            },
            sheetState = bottomSheetState,
        ) {
            innerEditSheet()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.EditPicTagsSheet(
    onEditTag: (String) -> Unit,
    picTags: List<String>?,
    editContextTags: SnapshotStateList<String>,
) {
    val scope = rememberCoroutineScope()

    val textFieldState = rememberTextFieldState()

    val searchResults = remember { mutableStateListOf<String>() }
    LaunchedEffect(Unit) {
        searchResults.clear()
        searchResults.addAll(
            Client.searchTag(
                count = 100,
            )?.tags ?: emptyList(),
        )
    }

    val scrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()
    val searchBarState = rememberContainedSearchBarState()
    val inputField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            onSearch = {
                scope.launch { searchBarState.animateToCollapsed() }
                scope.launch {
                    searchResults.clear()
                    searchResults.addAll(
                        Client.searchTag(
                            count = 100,
                            keyword = textFieldState.text.toString(),
                        )?.tags ?: emptyList(),
                    )
                }
            },
        )
    }

    AppBarWithSearch(
        scrollBehavior = scrollBehavior,
        state = searchBarState,
        inputField = inputField,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val originalPicTags = picTags ?: emptyList()
        val originalPicTagSet = originalPicTags.toSet()
        val editingTagSet = editContextTags.toSet()

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (originalPicTags.isEmpty() && editContextTags.isEmpty()) {
                    Box(
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Text(
                            text = "No tags",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.widthIn(max = 260.dp),
                        )
                    }
                } else {
                    originalPicTags.intersect(editingTagSet).forEach {
                        ElevatedAssistChip(
                            onClick = { onEditTag(it) },
                            label = { ChipText(it) },
                            modifier = Modifier.widthIn(max = 260.dp),
                        )
                    }

                    (editContextTags - originalPicTagSet).forEach {
                        ElevatedAssistChip(
                            onClick = { onEditTag(it) },
                            label = { ChipText(it) },
                            modifier = Modifier.widthIn(max = 260.dp),
                            colors = AssistChipDefaults.elevatedAssistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                        )
                    }

                    (originalPicTags - editingTagSet).forEach {
                        ElevatedAssistChip(
                            onClick = { onEditTag(it) },
                            label = { ChipText(it) },
                            modifier = Modifier.widthIn(max = 260.dp),
                            colors = AssistChipDefaults.elevatedAssistChipColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                        )
                    }
                }
            }
        }

        HorizontalDivider()

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                (searchResults - editingTagSet - originalPicTagSet).forEach {
                    ElevatedAssistChip(
                        onClick = { onEditTag(it) },
                        label = { ChipText(it) },
                        modifier = Modifier.widthIn(max = 260.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeaderSelection(
    selectedIndex: Int,
    onSelectionRandom: () -> Unit,
    onSelectionNoAuthor: () -> Unit,
    onSelectionNoTag: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            space = 8.dp,
            alignment = Alignment.CenterHorizontally,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val buttons = List<@Composable () -> Unit>(3) { index ->
            {
                when (index) {
                    0 -> {
                        TonalToggleButton(
                            checked = selectedIndex == index,
                            onCheckedChange = {
                                onSelectionRandom()
                            },
                        ) {
                            Text("Random")
                        }
                    }

                    1 -> {
                        TonalToggleButton(
                            checked = selectedIndex == index,
                            onCheckedChange = {
                                onSelectionNoAuthor()
                            },
                        ) {
                            Text("No author")
                        }
                    }

                    2 -> {
                        TonalToggleButton(
                            checked = selectedIndex == index,
                            onCheckedChange = {
                                onSelectionNoTag()
                            },
                        ) {
                            Text("No tag")
                        }
                    }

                    else -> error("")
                }
            }
        }

        buttons.forEach {
            it()
        }
    }
}

@Composable
private fun PicShowingTable(
    model: Any,
) {
    AsyncImage(
        model,
        contentDescription = null,
        modifier = Modifier
            .fillMaxSize(),
        contentScale = ContentScale.Fit,
    )
}
