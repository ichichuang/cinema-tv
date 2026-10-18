package com.ichichuang.cinema.feature.settings

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * 二维码生成 —— 给设置页的「扫码配置」用。
 *
 * 用 zxing 的 core 包：**纯 Java 实现**，不带任何原生库，
 * 装在 32 位电视上零 ABI 风险，也不会给 APK 增加几十兆。
 * （刻意不用 zxing-android-embedded：那个是为手机相机扫描做的，
 *   我们只需要"画出一张码"，没必要连相机那一整套一起打包。）
 *
 * 尺寸取 512×512：电视上显示约 260dp，512 足够清晰，
 * 一张位图 512×512×4 ≈ 1MB，对内存紧张的电视是安全的量级。
 */
object QrCode {

    fun bitmap(text: String, sizePx: Int = 512): ImageBitmap {
        val hints = mapOf(
            // 静区留 1 格：电视上白底卡片本身就有留白，再留多了码就小了
            EncodeHintType.MARGIN to 1,
            // 纠错等级 M：屏幕显示无污损，够用且码不过密
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)

        // 逐像素铺进 IntArray（比 setPixel 循环快得多，一次性开销也更小）
        val pixels = IntArray(sizePx * sizePx)
        for (y in 0 until sizePx) {
            val row = y * sizePx
            for (x in 0 until sizePx) {
                pixels[row + x] = if (matrix.get(x, y)) ARGB_BLACK else ARGB_WHITE
            }
        }

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
        return bitmap.asImageBitmap()
    }

    /**
     * 标准二维码是"深模块 + 浅底"，**不要反色**：
     * 白底黑码所有手机都能扫，反色码（黑底白码）很多相机不认。
     */
    private const val ARGB_BLACK = 0xFF000000.toInt()
    private const val ARGB_WHITE = 0xFFFFFFFF.toInt()
}
