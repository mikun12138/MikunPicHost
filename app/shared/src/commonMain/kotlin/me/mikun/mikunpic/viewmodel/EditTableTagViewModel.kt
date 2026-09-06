package me.mikun.mikunpic.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.mikun.mikunpic.client.Client
import me.mikun.mikunpic.dto.data.api.OhMyRouting.Manage.Pic.Random.TagFilter

class EditTableTagViewModel : ViewModel() {
    private companion object {
        const val IMAGE_PAGE_SIZE = 20
    }

    private val _tags = MutableStateFlow<List<String>>(emptyList())
    val tags = _tags.asStateFlow()

    private val _tagsSelected = MutableStateFlow<List<String>>(emptyList())
    val tagsSelected = _tagsSelected.asStateFlow()

    private val _imagePage = MutableStateFlow(1)
    val imagePage = _imagePage.asStateFlow()

    private val _canLoadNextImagePage = MutableStateFlow(false)
    val canLoadNextImagePage = _canLoadNextImagePage.asStateFlow()

    private val _isImagePageLoading = MutableStateFlow(false)
    val isImagePageLoading = _isImagePageLoading.asStateFlow()

    private val _imageShowing = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val imageShowing = _imageShowing.asStateFlow()

    private var updateImageShowingJob: Job? = null
    private var imageRequestId = 0

    fun updateTags() {
        viewModelScope.launch {
            try {
                _tags.value = Client.searchTag(
                    count = Int.MAX_VALUE,
                )?.let {
                    it.tags
                } ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleTagsSelected(tag: String) {
        _tagsSelected.update { selected ->
            if (tag in selected) {
                selected - tag
            } else {
                selected + tag
            }
        }
    }

    fun flashTagsSelected() {
        _tagsSelected.update { selected ->
            selected.filter { it in tags.value }
        }
    }

    fun updateImageShowing(
        storageLabel: String,
        page: Int = imagePage.value,
    ) {
        val imagePage = page.coerceAtLeast(1)
        val selectedTags = tagsSelected.value
        val requestId = ++imageRequestId

        updateImageShowingJob?.cancel()
        _imagePage.value = imagePage
        _canLoadNextImagePage.value = false
        _isImagePageLoading.value = true
        _imageShowing.value = emptyList()

        updateImageShowingJob = viewModelScope.launch {
            try {
                val images = Client.listPic(
                    count = IMAGE_PAGE_SIZE,
                    page = imagePage,
                    tagFilter = selectedTags.takeIf { it.isNotEmpty() }?.let {
                        TagFilter.All(it)
                    } ?: TagFilter.Any,
                    storageLabels = listOf(storageLabel),
                )?.label2Pics.orEmpty().flatMap { (storageLabel, pics) ->
                    pics.map { storageLabel to it.id }
                }

                if (requestId != imageRequestId) {
                    return@launch
                }

                _imageShowing.value = images
                _canLoadNextImagePage.value = images.size >= IMAGE_PAGE_SIZE
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (requestId == imageRequestId) {
                    _isImagePageLoading.value = false
                }
            }
        }
    }

    fun previousImagePage(
        storageLabel: String,
    ) {
        updateImageShowing(
            storageLabel = storageLabel,
            page = imagePage.value - 1,
        )
    }

    fun nextImagePage(
        storageLabel: String,
    ) {
        updateImageShowing(
            storageLabel = storageLabel,
            page = imagePage.value + 1,
        )
    }
}
