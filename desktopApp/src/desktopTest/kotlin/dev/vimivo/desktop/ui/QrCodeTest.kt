package dev.vimivo.desktop.ui

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class QrCodeTest {
    @Test
    fun theMatrixDecodesBackToTheSameLink() {
        val url = "http://192.168.1.20:8080/?t=Zx9_-AbC123"
        val matrix = assertNotNull(encodeQr(url))
        val scale = 8
        val size = matrix.width * scale
        val pixels = IntArray(size * size) { i ->
            if (matrix[(i % size) / scale, (i / size) / scale]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(size, size, pixels)))
        assertEquals(url, QRCodeReader().decode(bitmap).text)
    }
}
