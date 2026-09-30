package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.security.MessageDigest
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

@Composable
fun VerifiableQrCode(
    content: String,
    size: Dp = 180.dp,
    modifier: Modifier = Modifier
) {
    val bitMatrix = remember(content) {
        try {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1
            )
            QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 256, 256, hints)
        } catch (e: Exception) {
            null
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        if (bitMatrix != null) {
            Canvas(modifier = Modifier.size(size - 20.dp)) {
                val matrixWidth = bitMatrix.width
                val matrixHeight = bitMatrix.height
                val cellW = this.size.width / matrixWidth
                val cellH = this.size.height / matrixHeight

                for (x in 0 until matrixWidth) {
                    for (y in 0 until matrixHeight) {
                        if (bitMatrix.get(x, y)) {
                            drawRect(
                                color = Color.Black,
                                topLeft = Offset(x * cellW, y * cellH),
                                size = Size(cellW, cellH)
                            )
                        }
                    }
                }
            }
        } else {
            // Fallback to deterministic visual generator if content is too large
            BatchQrCode(batchId = content, size = size)
        }
    }
}


@Composable
fun BatchQrCode(
    batchId: String,
    size: Dp = 160.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Canvas(modifier = Modifier.size(size - 24.dp)) {
            val canvasWidth = this.size.width
            val canvasHeight = this.size.height
            val gridCount = 21
            val cellSize = canvasWidth / gridCount

            // Deterministic bits from batchId SHA-256
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(batchId.toByteArray(Charsets.UTF_8))

            // Finder patterns in three corners
            fun drawFinder(startX: Int, startY: Int) {
                // Outer 7x7
                drawRoundRect(
                    color = Color.Black,
                    topLeft = Offset(startX * cellSize, startY * cellSize),
                    size = Size(7 * cellSize, 7 * cellSize),
                    cornerRadius = CornerRadius(cellSize * 1.2f, cellSize * 1.2f)
                )
                // Inner 5x5 white
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset((startX + 1) * cellSize, (startY + 1) * cellSize),
                    size = Size(5 * cellSize, 5 * cellSize),
                    cornerRadius = CornerRadius(cellSize * 0.8f, cellSize * 0.8f)
                )
                // Center 3x3 black
                drawRoundRect(
                    color = Color.Black,
                    topLeft = Offset((startX + 2) * cellSize, (startY + 2) * cellSize),
                    size = Size(3 * cellSize, 3 * cellSize),
                    cornerRadius = CornerRadius(cellSize * 0.5f, cellSize * 0.5f)
                )
            }

            drawFinder(0, 0)
            drawFinder(14, 0)
            drawFinder(0, 14)

            // Fill data modules
            for (row in 0 until gridCount) {
                for (col in 0 until gridCount) {
                    // Skip finder areas
                    val inTopLeft = row < 8 && col < 8
                    val inTopRight = row < 8 && col >= 13
                    val inBottomLeft = row >= 13 && col < 8
                    if (inTopLeft || inTopRight || inBottomLeft) continue

                    // Timing lines
                    if (row == 6 || col == 6) {
                        if ((row + col) % 2 == 0) {
                            drawRect(
                                color = Color.Black,
                                topLeft = Offset(col * cellSize, row * cellSize),
                                size = Size(cellSize * 0.9f, cellSize * 0.9f)
                            )
                        }
                        continue
                    }

                    // Data matrix based on hash bits + position
                    val byteIndex = (row * gridCount + col) % digest.size
                    val bitIndex = (row + col) % 8
                    val isDark = ((digest[byteIndex].toInt() shr bitIndex) and 1) == 1

                    if (isDark) {
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(col * cellSize, row * cellSize),
                            size = Size(cellSize * 0.9f, cellSize * 0.9f)
                        )
                    }
                }
            }
        }
    }
}
