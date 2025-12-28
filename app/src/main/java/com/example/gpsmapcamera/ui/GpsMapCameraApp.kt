package com.example.gpsmapcamera.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
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
    val mapBitmap = rememberBitmapFromUri(config.mapUri)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(Color(0xAA111111), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF2B2B2B), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (mapBitmap != null) {
                    Image(
                        bitmap = mapBitmap.asImageBitmap(),
                        contentDescription = "Map thumbnail",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("No map", color = Color.White, fontSize = 12.sp)
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = { Toast.makeText(context, "Checked in", Toast.LENGTH_SHORT).show() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2F6FED)),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("Check In", fontSize = 12.sp)
                }
                Text(config.title.ifBlank { "Location title" }, color = Color.White)
                Text(config.address.ifBlank { "Address line" }, color = Color.White)
                Text(config.latLong.ifBlank { "Lat/Long" }, color = Color.White)
                Text(config.dateTime.ifBlank { "Date/Time" }, color = Color.White)
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

    var title by rememberSaveable(config.title) { mutableStateOf(config.title) }
    var address by rememberSaveable(config.address) { mutableStateOf(config.address) }
    var latLong by rememberSaveable(config.latLong) { mutableStateOf(config.latLong) }
    var dateTime by rememberSaveable(config.dateTime) { mutableStateOf(config.dateTime) }
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
                value = title,
                onValueChange = { title = it },
                label = { Text("Location title") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Address line") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = latLong,
                onValueChange = { latLong = it },
                label = { Text("Lat/Long") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = dateTime,
                onValueChange = { dateTime = it },
                label = { Text("Date/Time") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(onClick = { launcher.launch(arrayOf("image/*")) }) {
                Text("Upload Map Thumbnail")
            }

            MapThumbnailPreview(mapUri)

            Button(
                onClick = {
                    onSave(
                        OverlayConfig(
                            title = title,
                            address = address,
                            latLong = latLong,
                            dateTime = dateTime,
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
    val mapBitmap = rememberBitmapFromUri(mapUri)
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
                modifier = Modifier.fillMaxSize()
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
        androidx.camera.view.PreviewView(context).apply {
            scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
        }
    }

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
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
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
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(160.dp)
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
                                                loadBitmapFromUri(context, Uri.parse(config.mapUri))
                                            } else {
                                                null
                                            }
                                            val composed = renderOverlayOnBitmap(captured, config, mapBitmap)
                                            val filename = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                            saveBitmapToGallery(context, composed, "GPS_${filename}.jpg")
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
                        .padding(bottom = 24.dp)
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
    modifier: Modifier = Modifier
) {
    val mapBitmap = rememberBitmapFromUri(config.mapUri)

    Box(
        modifier = modifier
            .background(Color(0xAA111111))
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF2B2B2B), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (mapBitmap != null) {
                    Image(
                        bitmap = mapBitmap.asImageBitmap(),
                        contentDescription = "Map thumbnail",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("No map selected", color = Color.White, fontSize = 12.sp)
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = onCheckIn,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2F6FED)),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("Check In", fontSize = 12.sp)
                }
                Text(config.title.ifBlank { "Location title" }, color = Color.White)
                Text(config.address.ifBlank { "Address line" }, color = Color.White)
                Text(config.latLong.ifBlank { "Lat/Long" }, color = Color.White)
                Text(config.dateTime.ifBlank { "Date/Time" }, color = Color.White)
            }
        }
    }
}

@Composable
private fun rememberBitmapFromUri(uriString: String): android.graphics.Bitmap? {
    val context = LocalContext.current
    var bitmap by remember(uriString) { mutableStateOf<android.graphics.Bitmap?>(null) }
    
    LaunchedEffect(uriString) {
        if (uriString.isNotBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    bitmap = loadBitmapFromUri(context, Uri.parse(uriString))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            bitmap = null
        }
    }
    return bitmap
}
