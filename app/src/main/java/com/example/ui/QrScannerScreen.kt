package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.crypto.CryptoService
import com.example.crypto.NodeVerificationReport
import com.example.ui.theme.AlertCritical
import com.example.ui.theme.VerifiedGreen
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var isTorchOn by remember { mutableStateOf(false) }
    var useFrontCamera by remember { mutableStateOf(false) }
    var cameraControlInstance by remember { mutableStateOf<Camera?>(null) }
    var showManualInputDialog by remember { mutableStateOf(false) }
    var manualInputText by remember { mutableStateOf("") }

    val sampleNodes = remember { CryptoService.getSampleVerifiableNodes() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("qr_scanner_screen"),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF101418))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("qr_scanner_back_btn")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Supply Chain Node Scanner",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF1B5E20), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "ECDSA P-256",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Text(
                        text = "CameraX Live Viewfinder & Cryptographic Verification",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                }

                // Torch toggle
                IconButton(
                    onClick = {
                        cameraControlInstance?.let { cam ->
                            val newState = !isTorchOn
                            cam.cameraControl.enableTorch(newState)
                            isTorchOn = newState
                        }
                    },
                    modifier = Modifier.testTag("qr_scanner_torch_btn")
                ) {
                    Icon(
                        if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Flashlight",
                        tint = if (isTorchOn) Color(0xFFFFD54F) else Color.White
                    )
                }

                // Camera flip
                IconButton(
                    onClick = { useFrontCamera = !useFrontCamera },
                    modifier = Modifier.testTag("qr_scanner_flip_btn")
                ) {
                    Icon(
                        Icons.Default.FlipCameraAndroid,
                        contentDescription = "Switch Camera",
                        tint = Color.White
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            if (hasCameraPermission) {
                // Live CameraX Preview
                CameraPreviewView(
                    modifier = Modifier.fillMaxSize(),
                    useFrontCamera = useFrontCamera,
                    onCameraReady = { camera -> cameraControlInstance = camera },
                    onQrCodeDetected = { qrString ->
                        viewModel.verifyNodeQrPayload(qrString)
                    }
                )

                // High-tech Scanning Reticle and Laser Sweep Overlay
                ScannerOverlay(
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Permission Fallback Card
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Camera Permission Required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Camera access is needed to scan node QR codes and verify physical supply chain checkpoints via digital signatures.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.testTag("grant_camera_permission_btn")
                    ) {
                        Text("Grant Camera Access")
                    }
                }
            }

            // Bottom Floating Bar: Quick Test Node Presets & Manual Input
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xE6101418))
                    .padding(vertical = 12.dp, horizontal = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "QUICK TEST PRESETS (EMULATOR / LAB)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Cyan,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    TextButton(
                        onClick = { showManualInputDialog = true },
                        modifier = Modifier.testTag("manual_qr_input_btn")
                    ) {
                        Icon(
                            Icons.Default.QrCode,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Paste / Custom QR", color = Color.White, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Horizontal scroll list of realistic supply chain node QR presets
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sampleNodes.forEach { (label, nodePayload) ->
                        val isTampered = label.contains("Tampered", ignoreCase = true)
                        FilterChip(
                            selected = false,
                            onClick = {
                                val json = CryptoService.serializeNodePayloadToJson(nodePayload)
                                viewModel.verifyNodeQrPayload(json)
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isTampered) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isTampered) Icons.Default.Warning else Icons.Default.Security,
                                    contentDescription = null,
                                    tint = if (isTampered) AlertCritical else VerifiedGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = if (isTampered) Color(0x33D32F2F) else Color(0xFF1E2630),
                                labelColor = if (isTampered) Color(0xFFFF8A80) else Color.White
                            ),
                            modifier = Modifier.testTag("preset_${nodePayload.eventId}")
                        )
                    }
                }
            }
        }
    }

    // Modal Sheet showing Comprehensive Cryptographic Verification Report
    uiState.scannedNodeReport?.let { report ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.clearScannedNodeReport() },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.testTag("node_verification_modal")
        ) {
            NodeVerificationSheetContent(
                report = report,
                rawQr = uiState.lastScannedRawQr ?: "",
                onDismiss = { viewModel.clearScannedNodeReport() },
                onImportToLedger = {
                    viewModel.importVerifiedNodeToLedger(report)
                },
                onCopyPayload = {
                    clipboardManager.setText(AnnotatedString(uiState.lastScannedRawQr ?: ""))
                }
            )
        }
    }

    // Manual QR Input Dialog
    if (showManualInputDialog) {
        AlertDialog(
            onDismissRequest = { showManualInputDialog = false },
            title = {
                Text(
                    text = "Verify Node QR Code / Payload",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter raw JSON payload, farmtrace:// URI, or pipe-delimited node string:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manualInputText,
                        onValueChange = { manualInputText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .testTag("manual_qr_text_field"),
                        placeholder = { Text("{\"type\":\"FARMTRACE_NODE\", ...}") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (manualInputText.isNotBlank()) {
                            viewModel.verifyNodeQrPayload(manualInputText)
                            showManualInputDialog = false
                            manualInputText = ""
                        }
                    },
                    modifier = Modifier.testTag("confirm_manual_qr_btn")
                ) {
                    Text("Verify Signature")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualInputDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CameraPreviewView(
    modifier: Modifier = Modifier,
    useFrontCamera: Boolean,
    onCameraReady: (Camera) -> Unit,
    onQrCodeDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var lastScannedText by remember { mutableStateOf<String?>(null) }
    var lastScannedTime by remember { mutableStateOf(0L) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    analyzeQrCode(imageProxy) { detectedText ->
                        val now = System.currentTimeMillis()
                        // 2-second debounce to avoid duplicate spam while pointing at QR
                        if (detectedText != lastScannedText || (now - lastScannedTime) > 2000L) {
                            lastScannedText = detectedText
                            lastScannedTime = now
                            previewView.post {
                                onQrCodeDetected(detectedText)
                            }
                        }
                    }
                }

                val cameraSelector = if (useFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                try {
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                    onCameraReady(camera)
                } catch (exc: Exception) {
                    Log.e("QrScanner", "Camera binding failed", exc)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }
}

private fun analyzeQrCode(imageProxy: ImageProxy, onDetected: (String) -> Unit) {
    val image = imageProxy.image
    if (image == null) {
        imageProxy.close()
        return
    }

    try {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)

        val width = imageProxy.width
        val height = imageProxy.height

        val source = PlanarYUVLuminanceSource(
            data,
            width,
            height,
            0,
            0,
            width,
            height,
            false
        )
        val bitmap = BinaryBitmap(HybridBinarizer(source))

        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true
        )
        val reader = MultiFormatReader().apply { setHints(hints) }
        val result = reader.decodeWithState(bitmap)
        if (result != null && result.text.isNotBlank()) {
            onDetected(result.text)
        }
    } catch (_: Exception) {
        // Normal for frames without valid QR
    } finally {
        imageProxy.close()
    }
}

@Composable
private fun ScannerOverlay(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "LaserSweep")
    val laserYRatio by transition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LaserY"
    )

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        val boxSize = (canvasWidth * 0.72f).coerceAtMost(320.dp.toPx())
        val left = (canvasWidth - boxSize) / 2f
        val top = (canvasHeight - boxSize) / 2f - 40.dp.toPx()
        val right = left + boxSize
        val bottom = top + boxSize

        // Draw translucent dark veil outside reticle
        drawRect(
            color = Color(0x88000000),
            topLeft = Offset(0f, 0f),
            size = Size(canvasWidth, canvasHeight)
        )

        // Clear reticle center
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(boxSize, boxSize),
            cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
            blendMode = BlendMode.Clear
        )

        // Bounding box border
        drawRoundRect(
            color = Color(0x6600E5FF),
            topLeft = Offset(left, top),
            size = Size(boxSize, boxSize),
            cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
            style = Stroke(width = 2.dp.toPx())
        )

        // High-tech corner brackets
        val cornerLength = 28.dp.toPx()
        val cornerWidth = 4.dp.toPx()
        val bracketColor = Color(0xFF00E5FF)

        // Top Left
        drawLine(bracketColor, Offset(left, top), Offset(left + cornerLength, top), cornerWidth)
        drawLine(bracketColor, Offset(left, top), Offset(left, top + cornerLength), cornerWidth)

        // Top Right
        drawLine(bracketColor, Offset(right, top), Offset(right - cornerLength, top), cornerWidth)
        drawLine(bracketColor, Offset(right, top), Offset(right, top + cornerLength), cornerWidth)

        // Bottom Left
        drawLine(bracketColor, Offset(left, bottom), Offset(left + cornerLength, bottom), cornerWidth)
        drawLine(bracketColor, Offset(left, bottom), Offset(left, bottom - cornerLength), cornerWidth)

        // Bottom Right
        drawLine(bracketColor, Offset(right, bottom), Offset(right - cornerLength, bottom), cornerWidth)
        drawLine(bracketColor, Offset(right, bottom), Offset(right, bottom - cornerLength), cornerWidth)

        // Moving animated laser line
        val laserY = top + (boxSize * laserYRatio)
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFF00E5FF),
                    Color(0xFF76FF03),
                    Color(0xFF00E5FF),
                    Color.Transparent
                )
            ),
            start = Offset(left + 8.dp.toPx(), laserY),
            end = Offset(right - 8.dp.toPx(), laserY),
            strokeWidth = 3.dp.toPx()
        )
    }
}

@Composable
private fun NodeVerificationSheetContent(
    report: NodeVerificationReport,
    rawQr: String,
    onDismiss: () -> Unit,
    onImportToLedger: () -> Unit,
    onCopyPayload: () -> Unit
) {
    val p = report.payload
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Top Status Header Banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("verification_status_banner"),
            colors = CardDefaults.cardColors(
                containerColor = if (report.isValid) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (report.isValid) Color(0xFF2E7D32) else Color(0xFFC62828),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (report.isValid) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = if (report.isValid) "CRYPTOGRAPHIC SIGNATURE VERIFIED" else "INTEGRITY / SIGNATURE VIOLATION",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (report.isValid) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                    )
                    Text(
                        text = if (report.isValid) {
                            "Authentic supply chain node checkpoint signed with ${report.algorithm}"
                        } else {
                            report.failureReason ?: "Signature or hash mismatch detected"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (report.isValid) Color(0xFF2E7D32) else Color(0xFFC62828),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Cryptographic Verification Breakdown Card
        Text(
            text = "CRYPTOGRAPHIC AUDIT PROOF",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                AuditProofRow(
                    label = "Digital Signature (ECDSA)",
                    status = if (report.signatureValid) "VALID SIGNATURE" else "INVALID SIGNATURE",
                    isSuccess = report.signatureValid,
                    subtext = "Signed with P-256 curve private key via ${report.algorithm}"
                )

                Spacer(modifier = Modifier.height(10.dp))

                AuditProofRow(
                    label = "SHA-256 Hash Integrity",
                    status = if (report.hashValid) "100% MATCH" else "HASH MISMATCH",
                    isSuccess = report.hashValid,
                    subtext = "Recomputed: ${report.computedHash.take(16)}..."
                )

                Spacer(modifier = Modifier.height(10.dp))

                AuditProofRow(
                    label = "Authority Public Key",
                    status = report.keyFingerprint,
                    isSuccess = true,
                    subtext = report.authorityName
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Node & Supply Chain Details
        Text(
            text = "SUPPLY CHAIN NODE ATTRIBUTES",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                DetailItem(label = "Node / Device ID", value = p.deviceId, icon = Icons.Default.Hub)
                DetailItem(label = "Batch / Lot ID", value = p.batchId, icon = Icons.Default.QrCode)
                DetailItem(label = "Event Identifier", value = p.eventId, icon = Icons.Default.Key)
                DetailItem(label = "Supply Chain Stage", value = p.stage, icon = Icons.Default.Security)
                DetailItem(label = "Facility / Operator", value = p.operator, icon = Icons.Default.LocationOn)
                DetailItem(label = "Handoff Timestamp", value = dateFormat.format(Date(p.timestamp)), icon = Icons.Default.PlayArrow)
                DetailItem(label = "GPS Location", value = p.location, icon = Icons.Default.LocationOn)
                DetailItem(label = "Environmental Summary", value = p.environmentalConditions, icon = Icons.Default.Thermostat)
                DetailItem(label = "Parent Chain Hash", value = p.previousHash.take(24) + "...", icon = Icons.Default.Link)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCopyPayload,
                modifier = Modifier
                    .weight(1f)
                    .testTag("copy_qr_payload_btn")
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy Raw QR")
            }

            if (report.isValid) {
                Button(
                    onClick = onImportToLedger,
                    modifier = Modifier
                        .weight(1.4f)
                        .testTag("import_node_to_ledger_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Append To Ledger")
                }
            } else {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dismiss_invalid_node_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AlertCritical
                    )
                ) {
                    Text("Dismiss Anomaly")
                }
            }
        }
    }
}

@Composable
private fun AuditProofRow(
    label: String,
    status: String,
    isSuccess: Boolean,
    subtext: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtext,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }

        Box(
            modifier = Modifier
                .background(
                    if (isSuccess) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                    RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = status,
                color = if (isSuccess) Color(0xFF2E7D32) else Color(0xFFC62828),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun DetailItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            fontFamily = if (value.contains("0x") || value.length > 20) FontFamily.Monospace else FontFamily.Default
        )
    }
}
