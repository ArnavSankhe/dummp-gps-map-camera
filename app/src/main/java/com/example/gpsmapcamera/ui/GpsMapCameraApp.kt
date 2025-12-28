package com.example.gpsmapcamera.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gpsmapcamera.camera.CameraXController
import com.example.gpsmapcamera.data.OverlayConfig
import com.example.gpsmapcamera.data.OverlayRepository
import com.example.gpsmapcamera.util.decodeCapturedBitmap
import com.example.gpsmapcamera.util.loadBitmapFromUri
import com.example.gpsmapcamera.util.loadThumbnailBitmap
import com.example.gpsmapcamera.util.renderOverlayOnBitmap
import com.example.gpsmapcamera.util.saveBitmapToGallery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    val scope = rememberCoroutineScope()

    when (currentScreen) {
        Screen.Home -> HomeScreen(
            config = config,
            onConfigure = { currentScreen = Screen.Configure },
            onOpenCamera = { currentScreen = Screen.Camera }
        )
        Screen.Configure -> ConfigureScreen(
            config = config,
            onBack = { currentScreen = Screen.Home },
            onSave = { updated ->
                scope.launch {
                    repository.save(updated)
                    currentScreen = Screen.Home
                }
            }
        )
        Screen.Camera -> CameraScreen(
            config = config,
            onBack = { currentScreen = Screen.Home }
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
            val overlayHeight = maxHeight * overlayHeightRatio
            val overlayBottomPadding = maxHeight * overlayBottomPaddingRatio
            val leftColumnWidth = maxWidth * leftColumnWidthRatio
            val thumbSize = leftColumnWidth
            val checkInHeight = overlayHeight * 0.22f
            val columnGap = overlayHeight * 0.06f
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
                                            val composed = renderOverlayOnBitmap(captured, config, mapBitmap)
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
