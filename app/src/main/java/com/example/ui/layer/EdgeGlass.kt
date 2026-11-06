package com.example.ui.layer

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * سایهٔ نرم لبه‌ها (طرح پیش‌نمایش «Taraz Top Fade»):
 * بالای صفحه و پایین آن (زیر منوی شناور) محتوا اول نرم مات می‌شود، مثل شیشه،
 * و بعد روی یک منحنی نرم ۱۶ پله‌ای در رنگ پس‌زمینه محو می‌شود؛ هیچ لبهٔ تیزی دیده نمی‌شود.
 *
 * ماتی با RenderEffect کار می‌کند و فقط اندروید ۱۲ (API 31) به بالا آن را دارد؛
 * روی گوشی‌های قدیمی‌تر فقط محو شدن رنگ کشیده می‌شود.
 *
 * @param top ارتفاع نوار بالا (معمولاً ارتفاع نوار وضعیت + ۲۶dp)
 * @param bottom ارتفاع نوار پایین؛ صفر یعنی بدون سایهٔ پایین
 * @param solidTop چه کسری از بالای نوار کاملاً هم‌رنگ پس‌زمینه باشد
 */
@Composable
fun Modifier.edgeGlass(
    top: Dp,
    bottom: Dp = 0.dp,
    solidTop: Float = 0.18f,
    solidBottom: Float = 0.18f,
    glassTop: Dp = top + 18.dp,
    glassBottom: Dp = if (bottom > 0.dp) bottom + 12.dp else 0.dp
): Modifier {
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val content = rememberGraphicsLayer()
    val b1 = rememberGraphicsLayer()
    val b2 = rememberGraphicsLayer()
    val b3 = rememberGraphicsLayer()
    val b4 = rememberGraphicsLayer()
    val bg = L.Bg
    return this.drawWithContent {
        val topPx = top.toPx()
        val bottomPx = bottom.toPx()
        if (canBlur && (topPx > 0f || bottomPx > 0f)) {
            content.record(this, layoutDirection, IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))) {
                this@drawWithContent.drawContent()
            }
            drawLayer(content)
            val layers = listOf(b1, b2, b3, b4)
            if (topPx > 0f) glassBand(content, layers, 0f, glassTop.toPx().coerceAtMost(size.height), fromTop = true)
            if (bottomPx > 0f) {
                val h = glassBottom.toPx().coerceAtMost(size.height)
                glassBand(content, layers, size.height - h, h, fromTop = false)
            }
        } else {
            drawContent()
        }
        if (topPx > 0f) {
            drawRect(fadeBrush(bg, 0f, topPx, solidTop, fromTop = true), topLeft = Offset.Zero, size = Size(size.width, topPx))
        }
        if (bottomPx > 0f) {
            val y = size.height - bottomPx
            drawRect(fadeBrush(bg, y, size.height, solidBottom, fromTop = false), topLeft = Offset(0f, y), size = Size(size.width, bottomPx))
        }
    }
}

/** ضرایب منحنی نرم (همان پیش‌نمایش): پوشش رنگ در ۱۴ نقطهٔ میانی */
private val EASE = floatArrayOf(0.987f, 0.951f, 0.896f, 0.826f, 0.741f, 0.648f, 0.55f, 0.45f, 0.352f, 0.259f, 0.174f, 0.104f, 0.049f, 0.013f)

private fun fadeBrush(bg: Color, start: Float, end: Float, solid: Float, fromTop: Boolean): Brush {
    val stops = ArrayList<Pair<Float, Color>>(18)
    stops += 0f to bg
    stops += solid to bg
    val span = 1f - solid
    EASE.forEachIndexed { i, a ->
        stops += (solid + span * (i + 1) / (EASE.size + 1)) to bg.copy(alpha = a)
    }
    stops += 1f to bg.copy(alpha = 0f)
    val arr = if (fromTop) stops.toTypedArray()
    else stops.map { (f, c) -> (1f - f) to c }.sortedBy { it.first }.toTypedArray()
    return Brush.verticalGradient(*arr, startY = start, endY = end)
}

/**
 * چهار لایهٔ مات با شعاع‌های بیشتر، هر کدام با ماسک خودش؛ نزدیک لبه بیشترین ماتی
 * و به طرف داخل صفحه نرم به صفر می‌رسد (مثل backdrop-filter پله‌ای در پیش‌نمایش).
 */
private fun ContentDrawScope.glassBand(
    content: GraphicsLayer,
    layers: List<GraphicsLayer>,
    y: Float,
    h: Float,
    fromTop: Boolean
) {
    if (h <= 1f) return
    // (شعاع dp، تا کجای نوار کاملاً اعمال شود، کجا به صفر برسد)
    val levels = listOf(
        Triple(1.5f, 0.55f, 1.00f),
        Triple(4f, 0.42f, 0.80f),
        Triple(8f, 0.30f, 0.62f),
        Triple(16f, 0.18f, 0.46f)
    )
    val w = size.width
    levels.forEachIndexed { i, (radiusDp, full, zero) ->
        val layer = layers[i]
        val r = radiusDp.dp.toPx()
        layer.renderEffect = BlurEffect(r, r, TileMode.Clamp)
        layer.record(this, layoutDirection, IntSize(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1))) {
            translate(0f, -y) { drawLayer(content) }
        }
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(0f, y, w, y + h), Paint())
            translate(0f, y) { drawLayer(layer) }
            val mask = if (fromTop) Brush.verticalGradient(
                0f to Color.Black, full to Color.Black, zero to Color.Transparent, 1f to Color.Transparent,
                startY = y, endY = y + h
            ) else Brush.verticalGradient(
                0f to Color.Transparent, (1f - zero) to Color.Transparent, (1f - full) to Color.Black, 1f to Color.Black,
                startY = y, endY = y + h
            )
            drawRect(mask, topLeft = Offset(0f, y), size = Size(w, h), blendMode = BlendMode.DstIn)
            canvas.restore()
        }
    }
}
