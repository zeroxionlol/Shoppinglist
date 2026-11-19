package de.zeroxion.einkaufsliste.pantry.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeoSize
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import de.zeroxion.einkaufsliste.R
import de.zeroxion.einkaufsliste.pantry.logic.DateHit
import de.zeroxion.einkaufsliste.pantry.logic.MhdDateParser
import java.io.Closeable
import java.time.LocalDate
import java.util.concurrent.Executors

enum class ScanMode(val hint: String, val target: Size, val guideWidth: Float, val guideHeight: Float) {
    BARCODE("Barcode in den Rahmen halten", Size(1280, 720), 0.82f, 0.20f),
    DATE("Mindesthaltbarkeitsdatum in den Rahmen halten", Size(1920, 1080), 0.86f, 0.12f),
}

/**
 * Full-screen camera: reads a barcode (EAN/UPC) or a best-before date with ML Kit.
 * Everything runs on the phone; [onDate] / [onBarcode] are called once on the first hit.
 */
@Composable
fun ScannerDialog(
    mode: ScanMode,
    onBarcode: (String) -> Unit = {},
    onDate: (DateHit) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
    }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            when {
                granted -> CameraContent(mode, onBarcode, onDate, onDismiss)
                denied -> NoPermission(onDismiss)
                else -> Unit
            }
        }
    }
}

@Composable
private fun NoPermission(onDismiss: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Ohne Kamerazugriff kann die App nicht scannen. Du kannst ihn in den App-Einstellungen erlauben.",
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            onDismiss()
        }) { Text("App-Einstellungen öffnen") }
    }
}

@Composable
private fun CameraContent(
    mode: ScanMode,
    onBarcode: (String) -> Unit,
    onDate: (DateHit) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val currentOnBarcode by rememberUpdatedState(onBarcode)
    val currentOnDate by rememberUpdatedState(onDate)

    var status by remember(mode) { mutableStateOf("") }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    var found by remember(mode) { mutableStateOf(false) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(lifecycleOwner, mode) {
        val executor = Executors.newSingleThreadExecutor()
        val analyzer: FrameAnalyzer = when (mode) {
            ScanMode.BARCODE -> BarcodeAnalyzer { code ->
                found = true
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                currentOnBarcode(code)
            }
            ScanMode.DATE -> DateAnalyzer(
                onStatus = { status = it },
                onHit = { hit ->
                    found = true
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    currentOnDate(hit)
                },
            )
        }
        var provider: ProcessCameraProvider? = null
        var disposed = false
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (disposed) return@addListener
            try {
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(mode.target, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                            )
                            .build(),
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor, analyzer)
                p.unbindAll()
                camera = p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                status = "Kamera nicht verfügbar"
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            provider?.unbindAll()
            analyzer.close()
            executor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        val accent = MaterialTheme.colorScheme.primary
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            val w = size.width * mode.guideWidth
            val h = maxOf(size.height * mode.guideHeight, 96.dp.toPx())
            val topLeft = Offset((size.width - w) / 2, (size.height - h) / 2)
            val radius = CornerRadius(20.dp.toPx())
            drawRect(Color.Black.copy(alpha = 0.5f))
            drawRoundRect(Color.Transparent, topLeft, GeoSize(w, h), radius, blendMode = BlendMode.Clear)
            drawRoundRect(
                if (found) accent else Color.White, topLeft, GeoSize(w, h), radius,
                style = Stroke(width = 3.dp.toPx()),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().safeDrawingPadding().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FilledTonalIconButton(onClick = onDismiss, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Abbrechen")
            }
            if (camera?.cameraInfo?.hasFlashUnit() == true) {
                FilledTonalIconButton(
                    onClick = {
                        torch = !torch
                        camera?.cameraControl?.enableTorch(torch)
                    },
                    modifier = Modifier.size(52.dp),
                    colors = if (torch) {
                        IconButtonDefaults.filledTonalIconButtonColors(containerColor = accent, contentColor = Color.White)
                    } else {
                        IconButtonDefaults.filledTonalIconButtonColors()
                    },
                ) {
                    Icon(painterResource(R.drawable.ic_flash), contentDescription = if (torch) "Licht aus" else "Licht an")
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                mode.hint,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
            if (status.isNotEmpty()) {
                Text(
                    status,
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private interface FrameAnalyzer : ImageAnalysis.Analyzer, Closeable

/** EAN-13/EAN-8/UPC barcodes with ML Kit (offline). */
private class BarcodeAnalyzer(private val onCode: (String) -> Unit) : FrameAnalyzer {
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .build(),
    )

    @Volatile
    private var done = false

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null || done) {
            proxy.close()
            return
        }
        scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
            .addOnSuccessListener { codes ->
                val code = codes.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotBlank) }
                if (code != null && !done) {
                    done = true
                    onCode(code)
                }
            }
            .addOnCompleteListener { proxy.close() }
    }

    override fun close() = scanner.close()
}

/**
 * Best-before date with ML Kit text recognition (offline). Only text in the middle of the
 * picture (where the frame is) counts, and a date has to be read twice the same way.
 */
private class DateAnalyzer(
    private val onStatus: (String) -> Unit,
    private val onHit: (DateHit) -> Unit,
) : FrameAnalyzer {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val history = mutableListOf<LocalDate?>()

    @Volatile
    private var done = false

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null || done) {
            proxy.close()
            return
        }
        val rotation = proxy.imageInfo.rotationDegrees
        val upright = rotation == 90 || rotation == 270
        val width = if (upright) proxy.height else proxy.width
        val height = if (upright) proxy.width else proxy.height
        recognizer.process(InputImage.fromMediaImage(media, rotation))
            .addOnSuccessListener { result ->
                if (done) return@addOnSuccessListener
                val lines = result.textBlocks.flatMap { it.lines }.filter { line ->
                    val box = line.boundingBox ?: return@filter true
                    val cy = box.exactCenterY() / height
                    val cx = box.exactCenterX() / width
                    cy in 0.28f..0.72f && cx in 0.05f..0.95f
                }
                val text = lines.joinToString(" ") { it.text }.replace(Regex("\\s+"), " ").trim()
                val hit = MhdDateParser.parse(text)
                if (MhdDateParser.confirm(history, hit)) {
                    done = true
                    onHit(hit!!)
                } else {
                    onStatus(
                        when {
                            hit != null -> "Gelesen: ${hit.date.format(PantryViewModel.GERMAN_DATE)} – ruhig halten …"
                            text.isNotEmpty() -> "Gelesen: " + text.take(40)
                            else -> "Suche Datum …"
                        },
                    )
                }
            }
            .addOnCompleteListener { proxy.close() }
    }

    override fun close() = recognizer.close()
}
