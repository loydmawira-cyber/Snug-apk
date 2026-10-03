package com.example.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val bitmapCache = LruCache<String, ImageBitmap>(60)

private fun decodeDataUri(uri: String): ImageBitmap? = try {
    val bytes = Base64.decode(uri.substringAfter("base64,"), Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
} catch (e: Exception) {
    null
}

/**
 * Shows a normal image link with Coil, and also handles photos saved inside the profile
 * as "data:image/...;base64,..." (which Coil cannot load by itself).
 */
@Composable
fun SnugImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val str = model as? String
    if (str != null && str.startsWith("data:image")) {
        val bitmap by produceState<ImageBitmap?>(initialValue = bitmapCache.get(str), key1 = str) {
            if (value == null) {
                val decoded = withContext(Dispatchers.Default) { decodeDataUri(str) }
                if (decoded != null) bitmapCache.put(str, decoded)
                value = decoded
            }
        }
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale
            )
        } else {
            Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant))
        }
    } else {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    }
}
