package me.mikun.mikunpic.component.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope.Companion.DefaultFilterQuality
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.Size
import kotlinx.coroutines.delay

@Composable
fun SizeCachedImage(
    data: Any?,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    cacheKey: String? = data?.let {
        when (it) {
            is ImageRequest -> it.memoryCacheKey ?: it.diskCacheKey ?: it.data.toString()
            else -> it.toString()
        }
    },
    diskCacheKey: String? = cacheKey,
    precision: Precision = Precision.EXACT,
    crossfade: Boolean = true,
    placeholder: Painter? = null,
    error: Painter? = null,
    fallback: Painter? = error,
    onLoading: ((AsyncImagePainter.State.Loading) -> Unit)? = null,
    onSuccess: ((AsyncImagePainter.State.Success) -> Unit)? = null,
    onError: ((AsyncImagePainter.State.Error) -> Unit)? = null,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Crop,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = DefaultFilterQuality,
    clipToBounds: Boolean = true,
    sizeChangeDebounceMillis: Long = 120L,
    requestBuilder: ImageRequest.Builder.(IntSize) -> Unit = {},
) {
    val localPlatformContext = LocalPlatformContext.current
    var imageSize by remember(data) { mutableStateOf(IntSize.Zero) }
    var pendingImageSize by remember(data) { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(pendingImageSize, sizeChangeDebounceMillis) {
        if (
            pendingImageSize.width <= 0 ||
            pendingImageSize.height <= 0 ||
            pendingImageSize == imageSize
        ) {
            return@LaunchedEffect
        }

        if (sizeChangeDebounceMillis > 0) {
            delay(sizeChangeDebounceMillis)
        }

        imageSize = pendingImageSize
    }

    val imageRequest = remember(
        localPlatformContext,
        data,
        imageSize,
        cacheKey,
        diskCacheKey,
        precision,
        crossfade,
        requestBuilder,
    ) {
        if (imageSize.width <= 0 || imageSize.height <= 0) {
            null
        } else {
            val builder = when (data) {
                is ImageRequest -> data.newBuilder(localPlatformContext)
                else -> ImageRequest.Builder(localPlatformContext).data(data)
            }

            builder
                .size(Size(imageSize.width, imageSize.height))
                .apply {
                    if (cacheKey != null) {
                        memoryCacheKey("$cacheKey:${imageSize.width}x${imageSize.height}")
                    }
                    if (diskCacheKey != null) {
                        this.diskCacheKey(diskCacheKey)
                    }
                }
                .precision(precision)
                .crossfade(crossfade)
                .apply {
                    requestBuilder(imageSize)
                }
                .build()
        }
    }

    AsyncImage(
        model = imageRequest,
        contentDescription = contentDescription,
        modifier = modifier
            .onSizeChanged {
                if (it.width <= 0 || it.height <= 0) {
                    return@onSizeChanged
                }

                if (imageSize == IntSize.Zero) {
                    imageSize = it
                    pendingImageSize = it
                } else if (it != pendingImageSize) {
                    pendingImageSize = it
                }
            },
        placeholder = placeholder,
        error = error,
        fallback = fallback,
        onLoading = onLoading,
        onSuccess = onSuccess,
        onError = onError,
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter,
        filterQuality = filterQuality,
        clipToBounds = clipToBounds,
    )
}
