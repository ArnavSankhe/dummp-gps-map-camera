package com.example.gpsmapcamera.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.widget.FrameLayout
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.drawToBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gpsmapcamera.camera.CameraXController
import com.example.gpsmapcamera.data.OverlayConfig
import com.example.gpsmapcamera.data.OverlayRepository
import com.example.gpsmapcamera.util.compositeOverlayOnBitmap
import com.example.gpsmapcamera.util.cropCenterToAspect
import com.example.gpsmapcamera.util.decodeCapturedBitmap
import com.example.gpsmapcamera.util.loadBitmapFromUri
import com.example.gpsmapcamera.util.loadThumbnailBitmap
import com.example.gpsmapcamera.util.saveBitmapToGallery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import androidx.compose.runtime.withFrameNanos
import kotlin.coroutines.resume
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed class Screen {
    data object Home : Screen()
    data object Configure : Screen()
    data object Camera : Screen()
}

@Composable
fun GpsMapCameraApp() {
    val context = LocalContext.current
    val repository = remember { OverlayRepository(context) }
    val config by repository.configFlow.collectAsStateWithLifecycle(initialValue = OverlayConfig())
    var currentScreenKey by rememberSaveable { mutableStateOf("home") }
    val currentScreen = when (currentScreenKey) {
        "configure" -> Screen.Configure
        "camera" -> Screen.Camera
        else -> Screen.Home
    }
    val scope = rememberCoroutineScope()

    when (currentScreen) {
        Screen.Home -> HomeScreen(
            config = config,
            onConfigure = { currentScreenKey = "configure" },
            onOpenCamera = { currentScreenKey = "camera" }
        )
        Screen.Configure -> ConfigureScreen(
            config = config,
            onBack = { currentScreenKey = "home" },
            onSave = { updated ->
                scope.launch {
                    repository.save(updated)
                    currentScreenKey = "home"
                }
            }
        )
        Screen.Camera -> CameraScreen(
            config = config,
            onBack = { currentScreenKey = "home" }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    config: OverlayConfig,
    onConfigure: () -> Unit,
    onOpenCamera: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("GPS Map Camera") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Overlay preview", fontWeight = FontWeight.SemiBold)
            OverlayPreview(config)

            Button(onClick = onConfigure, modifier = Modifier.fillMaxWidth()) {
                Text("Configure Overlay")
            }
            Button(onClick = onOpenCamera, modifier = Modifier.fillMaxWidth()) {
                Text("Open Camera")
            }
        }
    }
}

@Composable
private fun OverlayPreview(config: OverlayConfig) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val mapTargetPx = with(density) { 160.dp.roundToPx() }
    val mapBitmap = rememberBitmapFromUri(config.mapUri, mapTargetPx)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(Color(0xAA111111), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                modifier = Modifier.width(108.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { Toast.makeText(context, "Checked in", Toast.LENGTH_SHORT).show() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2F6FED)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                ) {
                    Text("Check In", fontSize = 11.sp)
                }
                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .background(Color(0xFF2B2B2B), RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (mapBitmap != null) {
                    Image(
                        bitmap = mapBitmap.asImageBitmap(),
                        contentDescription = "Map thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    } else {
                        Text("No map", color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                val textBlock = config.details.takeIf { it.isNotBlank() }
                    ?: "Location title\nAddress line\nLat/Long\nDate/Time"
                AutoSizeTextBlock(
                    text = textBlock,
                    modifier = Modifier.fillMaxSize(),
                    maxLines = 5,
                    baseSize = 13.sp,
                    minSize = 8.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigureScreen(
    config: OverlayConfig,
    onBack: () -> Unit,
    onSave: (OverlayConfig) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var details by rememberSaveable(config.details) { mutableStateOf(config.details) }
    var mapUri by rememberSaveable(config.mapUri) { mutableStateOf(config.mapUri) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                }
                mapUri = uri.toString()
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configure Overlay") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = details,
                onValueChange = { details = it },
                label = { Text("Overlay text (multi-line)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp),
                maxLines = 6
            )

            Button(onClick = { launcher.launch(arrayOf("image/*")) }) {
                Text("Upload Map Thumbnail")
            }

            MapThumbnailPreview(mapUri)

            Button(
                onClick = {
                    onSave(
                        OverlayConfig(
                            details = details,
                            mapUri = mapUri
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun MapThumbnailPreview(mapUri: String) {
    val density = LocalDensity.current
    val mapTargetPx = with(density) { 320.dp.roundToPx() }
    val mapBitmap = rememberBitmapFromUri(mapUri, mapTargetPx)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .border(1.dp, Color(0xFFCCCCCC), RoundedCornerShape(12.dp))
            .background(Color(0xFFF2F2F2), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (mapBitmap != null) {
            Image(
                bitmap = mapBitmap.asImageBitmap(),
                contentDescription = "Selected map",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text("No map selected", color = Color(0xFF666666))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CameraScreen(
    config: OverlayConfig,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val permissionState = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> permissionState.value = granted }
    )

    val controller = remember { CameraXController(context, lifecycleOwner) }
    val compositionContext = rememberCompositionContext()
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val overlayHeightRatio = 0.22f
    val overlayBottomPaddingRatio = 0.03f
    val leftColumnWidthRatio = 0.33f

    LaunchedEffect(permissionState.value) {
        if (permissionState.value) {
            controller.bind(previewView)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Camera") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            val density = LocalDensity.current
            val overlayHeight = maxHeight * overlayHeightRatio
            val overlayBottomPadding = maxHeight * overlayBottomPaddingRatio
            val leftColumnWidth = maxWidth * leftColumnWidthRatio
            val thumbSize = leftColumnWidth
            val checkInHeight = overlayHeight * 0.22f
            val columnGap = overlayHeight * 0.06f
            val previewAspectRatio = (maxWidth.value / maxHeight.value).coerceAtLeast(0.01f)
            val previewWidthPx = with(density) { maxWidth.roundToPx() }
            val previewHeightPx = with(density) { maxHeight.roundToPx() }
            if (!permissionState.value) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Camera permission is required to take photos.")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant Permission")
                    }
                }
            } else {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )

                OverlayPanel(
                    config = config,
                    onCheckIn = {
                        scope.launch { snackbarHostState.showSnackbar("Checked in") }
                    },
                    leftColumnWidth = leftColumnWidth,
                    thumbSize = thumbSize,
                    checkInHeight = checkInHeight,
                    columnGap = columnGap,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(overlayHeight)
                        .padding(bottom = overlayBottomPadding)
                )

                FloatingActionButton(
                    onClick = {
                        val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
                        val outputOptions = androidx.camera.core.ImageCapture.OutputFileOptions
                            .Builder(file)
                            .build()
                        controller.imageCapture.takePicture(
                            outputOptions,
                            controller.executor(),
                            object : androidx.camera.core.ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: androidx.camera.core.ImageCapture.OutputFileResults) {
                                    scope.launch {
                                        val saved = withContext(Dispatchers.IO) {
                                            val captured = decodeCapturedBitmap(file) ?: return@withContext null
                                            val mapBitmap = if (config.mapUri.isNotBlank()) {
                                                loadThumbnailBitmap(context, Uri.parse(config.mapUri), 512, 512)
                                            } else {
                                                null
                                            }
                                            val cropped = cropCenterToAspect(captured, previewAspectRatio)
                                            val overlayHeightPx = (cropped.height * overlayHeightRatio).toInt()
                                            val bottomPaddingPx = (cropped.height * overlayBottomPaddingRatio).toInt()
                                            val overlayBitmap = withContext(Dispatchers.Main) {
                                                renderOverlayBitmap(
                                                    context = context,
                                                    previewWidthDp = maxWidth,
                                                    previewOverlayHeightDp = overlayHeight,
                                                    previewWidthPx = previewWidthPx,
                                                    previewOverlayHeightPx = (previewHeightPx * overlayHeightRatio).toInt(),
                                                    targetWidthPx = cropped.width,
                                                    targetHeightPx = overlayHeightPx,
                                                    parentComposition = compositionContext,
                                                    config = config,
                                                    mapBitmap = mapBitmap,
                                                    leftColumnWidthRatio = leftColumnWidthRatio,
                                                    checkInHeightRatio = 0.22f,
                                                    columnGapRatio = 0.06f
                                                )
                                            }
                                            val yOffset = (cropped.height - overlayBitmap.height - bottomPaddingPx).coerceAtLeast(0)
                                            val composed = compositeOverlayOnBitmap(cropped, overlayBitmap, yOffset)
                                            val filename = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                            val savedUri = saveBitmapToGallery(context, composed, "GPS_${filename}.jpg")
                                            file.delete()
                                            savedUri
                                        }
                                        if (saved != null) {
                                            snackbarHostState.showSnackbar("Saved to gallery")
                                        } else {
                                            snackbarHostState.showSnackbar("Failed to save photo")
                                        }
                                    }
                                }

                                override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Capture failed: ${exception.message}")
                                    }
                                }
                            }
                        )
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = overlayHeight + overlayBottomPadding + 16.dp)
                ) {
                    Text("Capture")
                }
            }
        }
    }
}

@Composable
private fun OverlayPanel(
    config: OverlayConfig,
    onCheckIn: () -> Unit,
    leftColumnWidth: Dp,
    thumbSize: Dp,
    checkInHeight: Dp,
    columnGap: Dp,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val mapTargetPx = with(density) { 220.dp.roundToPx() }
    val mapBitmap = rememberBitmapFromUri(config.mapUri, mapTargetPx)

    Box(
        modifier = modifier
            .background(Color(0xAA111111), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                modifier = Modifier.width(leftColumnWidth),
                verticalArrangement = Arrangement.spacedBy(columnGap)
            ) {
                Button(
                    onClick = onCheckIn,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2F6FED)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(checkInHeight * 0.7f)
                ) {
                    Text("Check In", fontSize = 11.sp)
                }
                Box(
                    modifier = Modifier
                        .size(thumbSize)
                        .background(Color(0xFF2B2B2B), RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (mapBitmap != null) {
                    Image(
                        bitmap = mapBitmap.asImageBitmap(),
                        contentDescription = "Map thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    } else {
                        Text("No map selected", color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                val textBlock = config.details.takeIf { it.isNotBlank() }
                    ?: "Location title\nAddress line\nLat/Long\nDate/Time"
                AutoSizeTextBlock(
                    text = textBlock,
                    modifier = Modifier.fillMaxSize(),
                    maxLines = 5,
                    baseSize = 13.sp,
                    minSize = 8.sp
                )
            }
        }
    }
}

@Composable
private fun rememberBitmapFromUri(uriString: String, targetPx: Int? = null): android.graphics.Bitmap? {
    val context = LocalContext.current
    var bitmap by remember(uriString) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(uriString) {
        val decoded = if (uriString.isNotBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    if (targetPx != null) {
                        loadThumbnailBitmap(context, Uri.parse(uriString), targetPx, targetPx)
                    } else {
                        loadBitmapFromUri(context, Uri.parse(uriString))
                    }
                } catch (_: Exception) {
                    null
                }
            }
        } else {
            null
        }
        bitmap = decoded
    }
    return bitmap
}

@Composable
private fun OverlayPanelContent(
    config: OverlayConfig,
    mapBitmap: android.graphics.Bitmap?,
    leftColumnWidth: Dp,
    thumbSize: Dp,
    checkInHeight: Dp,
    columnGap: Dp,
    onCheckIn: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xAA111111), RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                modifier = Modifier.width(leftColumnWidth),
                verticalArrangement = Arrangement.spacedBy(columnGap)
            ) {
                Button(
                    onClick = onCheckIn,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2F6FED)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(checkInHeight * 0.7f)
                ) {
                    Text("Check In", fontSize = 11.sp)
                }
                Box(
                    modifier = Modifier
                        .size(thumbSize)
                        .background(Color(0xFF2B2B2B), RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (mapBitmap != null) {
                        Image(
                            bitmap = mapBitmap.asImageBitmap(),
                            contentDescription = "Map thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text("No map selected", color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                val textBlock = config.details.takeIf { it.isNotBlank() }
                    ?: "Location title\nAddress line\nLat/Long\nDate/Time"
                AutoSizeTextBlock(
                    text = textBlock,
                    modifier = Modifier.fillMaxSize(),
                    maxLines = 5,
                    baseSize = 13.sp,
                    minSize = 8.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun AutoSizeTextBlock(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int,
    baseSize: TextUnit,
    minSize: TextUnit
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val availableWidth = constraints.maxWidth.coerceAtLeast(1)
        val availableHeight = constraints.maxHeight.coerceAtLeast(1)
        val sizes = remember(baseSize, minSize) {
            val start = baseSize.value.toInt()
            val end = minSize.value.toInt()
            if (start <= end) listOf(baseSize) else (start downTo end).map { it.sp }
        }
        val selectedSize = remember(text, availableWidth, availableHeight, sizes) {
            sizes.firstOrNull { size ->
                val result = measurer.measure(
                    text = text,
                    style = TextStyle(fontSize = size, color = Color.White),
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis,
                    constraints = Constraints(maxWidth = availableWidth)
                )
                result.size.height <= availableHeight
            } ?: sizes.last()
        }
        Text(
            text = text,
            color = Color.White,
            fontSize = selectedSize,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private suspend fun renderOverlayBitmap(
    context: android.content.Context,
    previewWidthDp: Dp,
    previewOverlayHeightDp: Dp,
    previewWidthPx: Int,
    previewOverlayHeightPx: Int,
    targetWidthPx: Int,
    targetHeightPx: Int,
    parentComposition: CompositionContext,
    config: OverlayConfig,
    mapBitmap: android.graphics.Bitmap?,
    leftColumnWidthRatio: Float,
    checkInHeightRatio: Float,
    columnGapRatio: Float
): android.graphics.Bitmap {
    suspend fun awaitAttached(view: View) {
        if (view.isAttachedToWindow) return
        suspendCancellableCoroutine { continuation ->
            val listener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.removeOnAttachStateChangeListener(this)
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }

                override fun onViewDetachedFromWindow(v: View) = Unit
            }
            view.addOnAttachStateChangeListener(listener)
            continuation.invokeOnCancellation {
                view.removeOnAttachStateChangeListener(listener)
            }
        }
    }

    val composeView = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setParentCompositionContext(parentComposition)
    }
    val container = FrameLayout(context).apply {
        addView(
            composeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }
    val activity = context as? androidx.activity.ComponentActivity
        ?: return android.graphics.Bitmap.createBitmap(targetWidthPx, targetHeightPx, android.graphics.Bitmap.Config.ARGB_8888)
    composeView.setContent {
        MaterialTheme {
            val widthDp = previewWidthDp
            val heightDp = previewOverlayHeightDp
            val leftColumnWidth = widthDp * leftColumnWidthRatio
            val thumbSize = leftColumnWidth
            val checkInHeight = heightDp * checkInHeightRatio
            val columnGap = heightDp * columnGapRatio
            OverlayPanelContent(
                config = config,
                mapBitmap = mapBitmap,
                leftColumnWidth = leftColumnWidth,
                thumbSize = thumbSize,
                checkInHeight = checkInHeight,
                columnGap = columnGap,
                onCheckIn = {},
                modifier = Modifier
                    .width(widthDp)
                    .height(heightDp)
            )
        }
    }
    val widthSpec = android.view.View.MeasureSpec.makeMeasureSpec(previewWidthPx, android.view.View.MeasureSpec.EXACTLY)
    val heightSpec = android.view.View.MeasureSpec.makeMeasureSpec(previewOverlayHeightPx, android.view.View.MeasureSpec.EXACTLY)
    val root = activity.window?.decorView as? ViewGroup
        ?: return android.graphics.Bitmap.createBitmap(targetWidthPx, targetHeightPx, android.graphics.Bitmap.Config.ARGB_8888)
    container.visibility = View.INVISIBLE
    root.addView(container, ViewGroup.LayoutParams(previewWidthPx, previewOverlayHeightPx))
    try {
        awaitAttached(container)
        container.measure(widthSpec, heightSpec)
        container.layout(0, 0, previewWidthPx, previewOverlayHeightPx)
        withFrameNanos { }
        val bitmap = container.drawToBitmap(android.graphics.Bitmap.Config.ARGB_8888)
        return android.graphics.Bitmap.createScaledBitmap(bitmap, targetWidthPx, targetHeightPx, true)
    } finally {
        root.removeView(container)
    }
}
