package de.zeroxion.einkaufsliste.pantry.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Small product pictures from Open Food Facts, cached in memory and on disk. */
object ProductImages {
    private const val MAX_PX = 192
    private val memory = LruCache<String, ImageBitmap>(80)

    fun cached(url: String): ImageBitmap? = memory.get(url)

    suspend fun load(context: Context, url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        memory.get(url)?.let { return@withContext it }
        val dir = File(context.cacheDir, "produktbilder").apply { mkdirs() }
        val file = File(dir, sha1(url))
        try {
            if (!file.exists()) {
                val conn = URL(url).openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 8_000
                    conn.readTimeout = 8_000
                    if (conn.responseCode !in 200..299) return@withContext null
                    val bytes = conn.inputStream.use { it.readBytes() }
                    if (bytes.size > 2_000_000) return@withContext null
                    file.writeBytes(bytes)
                } finally {
                    conn.disconnect()
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= MAX_PX && bounds.outHeight / (sample * 2) >= MAX_PX) sample *= 2
            val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return@withContext null.also { file.delete() }
            bitmap.asImageBitmap().also { memory.put(url, it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}

/** Shows the picture at [url], or [fallback] while it loads or if there is none. */
@Composable
fun RemoteImage(url: String, modifier: Modifier = Modifier, fallback: @Composable () -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState(initialValue = ProductImages.cached(url), url) {
        value = ProductImages.cached(url) ?: if (url.isNotEmpty()) ProductImages.load(context, url) else null
    }
    val image = bitmap
    if (image != null) {
        Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        fallback()
    }
}
