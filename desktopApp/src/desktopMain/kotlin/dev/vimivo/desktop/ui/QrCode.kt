package dev.vimivo.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Encodes [text] as a QR matrix, or null if it does not fit. The matrix includes a one-module quiet zone. */
internal fun encodeQr(text: String): BitMatrix? = try {
    QRCodeWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        0,
        0,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1),
    )
} catch (_: WriterException) {
    null
}

/** A QR code drawn as black modules on white, so scanners work in light and dark themes. */
@Composable
internal fun QrCode(text: String, size: Dp = 180.dp, modifier: Modifier = Modifier) {
    val matrix = remember(text) { encodeQr(text) } ?: return
    Canvas(
        modifier
            .size(size)
            .background(Color.White)
            .padding(4.dp)
            .semantics { contentDescription = "QR code for the link above" },
    ) {
        val module = this.size.width / matrix.width
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) {
                    drawRect(Color.Black, Offset(x * module, y * module), Size(module + 0.5f, module + 0.5f))
                }
            }
        }
    }
}
