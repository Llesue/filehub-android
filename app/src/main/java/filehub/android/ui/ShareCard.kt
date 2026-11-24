package filehub.android.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import filehub.android.BuildConfig
import filehub.android.R
import filehub.android.core.model.FileKind
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@Composable
fun shareExpiryLabel(expiresAt: Long?): String {
    if (expiresAt == null) return stringResource(R.string.share_card_expiry_never)
    val formatted = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(expiresAt * 1000))
    return stringResource(R.string.share_card_expiry_at, formatted)
}

data class ShareCardSpec(
    val url: String,
    val title: String,
    val kind: FileKind,
    val iconColor: Int,
    val hasPassword: Boolean,
    val expiryText: String,
    val faviconUrl: String?,
    val headerTitle: String,
    val headerSubtitle: String,
    val passwordText: String,
    val footerText: String,
)

/**
 * Draws the 1080x1440 share card. Kept deterministic and light-on-light so the
 * image stays readable after being forwarded through any messenger.
 */
object ShareCardRenderer {
    private const val WIDTH = 1080
    private const val HEIGHT = 1440

    suspend fun render(context: Context, spec: ShareCardSpec): Bitmap = withContext(Dispatchers.IO) {
        val favicon = loadFavicon(spec.faviconUrl)
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(0xF7, 0xF8, 0xFA))

        val panel = RectF(36f, 36f, WIDTH - 36f, HEIGHT - 36f)
        val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            setShadowLayer(18f, 0f, 8f, 0x22000000)
        }
        canvas.drawRoundRect(panel, 36f, 36f, panelPaint)

        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1A73E8.toInt() }
        canvas.drawRoundRect(RectF(36f, 36f, WIDTH - 36f, 58f), 24f, 24f, accent)

        drawHeader(canvas, context, spec, favicon)
        drawTitle(canvas, context, spec)
        drawQr(canvas, spec.url)
        drawMeta(canvas, spec)
        bitmap
    }

    private fun drawHeader(
        canvas: Canvas,
        context: Context,
        spec: ShareCardSpec,
        favicon: Bitmap?,
    ) {
        val iconRect = RectF(104f, 104f, 200f, 200f)
        if (favicon != null) {
            val path = Path().apply { addRoundRect(iconRect, 24f, 24f, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(path)
            canvas.drawBitmap(
                favicon,
                Rect(0, 0, favicon.width, favicon.height),
                iconRect,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true },
            )
            canvas.restore()
        } else {
            canvas.drawRoundRect(
                iconRect,
                24f,
                24f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1A71E4.toInt() },
            )
            val logo = BitmapFactory.decodeResource(
                context.resources,
                R.drawable.ic_launcher_foreground,
            )
            if (logo != null) {
                canvas.drawBitmap(
                    logo,
                    Rect(0, 0, logo.width, logo.height),
                    iconRect,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true },
                )
            }
        }
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF202124.toInt()
            textSize = 46f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(spec.headerTitle, 228f, 156f, title)
        val subtitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF5F6368.toInt()
            textSize = 28f
        }
        canvas.drawText(spec.headerSubtitle, 228f, 196f, subtitle)

        val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE3E5E8.toInt() }
        canvas.drawRect(104f, 244f, WIDTH - 104f, 246f, divider)
    }

    private fun drawTitle(canvas: Canvas, context: Context, spec: ShareCardSpec) {
        val iconRect = RectF(104f, 294f, 168f, 358f)
        val kindIcon = ContextCompat.getDrawable(context, fileIconRes(spec.kind))
        kindIcon?.setTint(spec.iconColor)
        kindIcon?.setBounds(
            iconRect.left.toInt(),
            iconRect.top.toInt(),
            iconRect.right.toInt(),
            iconRect.bottom.toInt(),
        )
        kindIcon?.draw(canvas)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF202124.toInt()
            textSize = 46f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val maxWidth = WIDTH - 104f - 228f
        canvas.drawText(ellipsize(spec.title, paint, maxWidth), 228f, 350f, paint)
    }

    private fun drawQr(canvas: Canvas, url: String) {
        val box = RectF(150f, 390f, WIDTH - 150f, 1170f)
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE3E5E8.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRoundRect(box, 32f, 32f, boxPaint)
        canvas.drawRoundRect(box, 32f, 32f, border)

        val matrix = runCatching {
            QRCodeWriter().encode(
                url,
                BarcodeFormat.QR_CODE,
                640,
                640,
                mapOf(EncodeHintType.MARGIN to 1),
            )
        }.getOrNull() ?: return
        val qrSize = 620f
        val module = qrSize / matrix.width
        val left = (WIDTH - qrSize) / 2f
        val top = 470f
        val paint = Paint().apply { color = 0xFF202124.toInt() }
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) {
                    canvas.drawRect(
                        left + x * module,
                        top + y * module,
                        left + (x + 1) * module,
                        top + (y + 1) * module,
                        paint,
                    )
                }
            }
        }
    }

    private fun drawMeta(canvas: Canvas, spec: ShareCardSpec) {
        val urlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF5F6368.toInt()
            textSize = 30f
            textAlign = Paint.Align.CENTER
        }
        val shortUrl = shortUrl(spec.url)
        canvas.drawText(
            ellipsize(shortUrl, urlPaint, WIDTH - 240f),
            WIDTH / 2f,
            1205f,
            urlPaint,
        )

        val passwordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1A73E8.toInt()
            textSize = 30f
        }
        val expiryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF5F6368.toInt()
            textSize = 30f
        }
        val passwordWidth = if (spec.hasPassword) {
            passwordPaint.measureText(spec.passwordText) + 48f
        } else {
            0f
        }
        val expiryWidth = expiryPaint.measureText(spec.expiryText)
        val gap = if (spec.hasPassword) 20f else 0f
        val total = passwordWidth + gap + expiryWidth
        var x = (WIDTH - total) / 2f
        if (spec.hasPassword) {
            val pill = RectF(x, 1238f, x + passwordWidth, 1298f)
            canvas.drawRoundRect(
                pill,
                30f,
                30f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8F0FE.toInt() },
            )
            canvas.drawText(
                spec.passwordText,
                x + 24f,
                1282f,
                passwordPaint,
            )
            x = pill.right + gap
        }
        canvas.drawText(spec.expiryText, x, 1282f, expiryPaint)

        val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF9AA0A6.toInt()
            textSize = 30f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(spec.footerText, WIDTH / 2f, 1355f, footer)
    }

    private fun shortUrl(url: String): String {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return url
        val host = uri.host ?: return url
        return "$host${uri.path.orEmpty()}"
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        val available = maxWidth - paint.measureText(ellipsis)
        if (available <= 0) return ellipsis
        val count = paint.breakText(text, true, available, null)
        return text.take(count) + ellipsis
    }

    private fun loadFavicon(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            val client = OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .build()
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bytes = response.body.bytes()
                if (bytes.size > 512 * 1024) return@use null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }.getOrNull()
    }
}

suspend fun saveShareCard(context: Context, bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val name = "filehub-share-${System.currentTimeMillis()}.png"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/FileHub",
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching false
            resolver.openOutputStream(uri)?.use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            } ?: return@runCatching false
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "FileHub",
            ).apply { mkdirs() }
            val file = File(dir, name)
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            true
        }
    }.getOrDefault(false)
}

fun shareCardFile(context: Context, bitmap: Bitmap): Uri? = runCatching {
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val file = File(dir, "filehub-share-${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { output ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
    }
    FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", file)
}.getOrNull()

fun shareCardIntent(context: Context, bitmap: Bitmap): Intent? {
    val uri = shareCardFile(context, bitmap) ?: return null
    return Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

fun copyShareLink(context: Context, link: String) {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return
    if (Build.VERSION.SDK_INT >= 33) {
        manager.setPrimaryClip(
            ClipData.newPlainText(context.getString(R.string.share_qr), link).apply {
                description.extras = android.os.PersistableBundle().apply {
                    putBoolean("android.content.extra.IS_SENSITIVE", true)
                }
            },
        )
    } else {
        manager.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.share_qr), link))
    }
}

@Composable
fun ShareCardDialog(
    spec: ShareCardSpec,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(spec) { mutableStateOf<Bitmap?>(null) }
    var error by remember(spec) { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf(false) }
    val saveLabel = stringResource(R.string.share_card_save)
    val savedLabel = stringResource(R.string.share_card_saved)
    val saveFailedLabel = stringResource(R.string.share_card_save_failed)

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingSave) {
            pendingSave = false
            bitmap?.let { image ->
                scope.launch {
                    val ok = saveShareCard(context, image)
                    Toast.makeText(
                        context,
                        if (ok) savedLabel else saveFailedLabel,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        } else {
            pendingSave = false
        }
    }

    fun save() {
        val image = bitmap ?: return
        if (Build.VERSION.SDK_INT <= 28 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingSave = true
            permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        scope.launch {
            val ok = saveShareCard(context, image)
            Toast.makeText(
                context,
                if (ok) savedLabel else saveFailedLabel,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    LaunchedEffect(spec) {
        error = false
        bitmap = runCatching { ShareCardRenderer.render(context, spec) }.getOrNull()
        if (bitmap == null) error = true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                val image = bitmap
                if (image != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.75f)
                            .pointerInput(image) {
                                detectTapGestures(onLongPress = { save() })
                            }
                            .semantics {
                                customActions = listOf(
                                    CustomAccessibilityAction(saveLabel) {
                                        save()
                                        true
                                    },
                                )
                            },
                    ) {
                        Image(
                            bitmap = image.asImageBitmap(),
                            contentDescription = stringResource(R.string.share_qr),
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.75f),
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.75f),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (error) {
                            Text(
                                text = stringResource(R.string.share_card_failed),
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            CircularProgressIndicator()
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.share_card_long_press_save),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            val image = bitmap ?: return@TextButton
                            val intent = shareCardIntent(context, image) ?: return@TextButton
                            context.startActivity(Intent.createChooser(intent, null))
                        },
                        enabled = bitmap != null,
                    ) {
                        Text(stringResource(R.string.share_card_forward))
                    }
                    TextButton(
                        onClick = { copyShareLink(context, spec.url) },
                    ) {
                        Text(stringResource(R.string.share_copy))
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.share_close))
                    }
                }
            }
        }
    }
}
