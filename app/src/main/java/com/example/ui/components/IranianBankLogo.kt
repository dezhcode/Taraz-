package com.example.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.example.R

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.BlendMode

private fun getSvgFilenameForBank(bankName: String): String? {
    val name = bankName.trim()
    return when {
        name.contains("انصار") -> "ansar-05.svg"
        name.contains("آینده") -> "ayande-03.svg"
        name.contains("دی") -> "day-12.svg"
        name.contains("اقتصاد نوین") || name.contains("اقتصادنوین") -> "eghtesad-04.svg"
        name.contains("توسعه") && name.contains("اعتباری") -> "tose-11.svg"
        name.contains("گردشگری") -> "gardeshgari-20.svg"
        name.contains("قوامین") -> "ghavvamin-31.svg"
        name.contains("کارآفرین") -> "karafarin-06.svg"
        name.contains("کشاورزی") -> "keshavarzi-07.svg"
        name.contains("مسکن") -> "maskan-26.svg"
        name.contains("مهر اقتصاد") -> "mehreghtesad-14.svg"
        name.contains("مهر ایران") || name.contains("مهر ایرانیان") -> "mehriran-15.svg"
        name.contains("ملی") -> "melli-22.svg"
        name.contains("ملت") -> "mellat-02.svg"
        name.contains("پارسیان") -> "pasargad-21.svg"
        name.contains("پاسارگاد") -> "pasargad-10.svg"
        name.contains("پست") -> "post-09.svg"
        name.contains("رفاه") -> "refahkargaran-28.svg"
        name.contains("صنعت") && name.contains("معدن") -> "sanatmadan-16.svg"
        name.contains("صادرات") -> "saderat-29.svg"
        name.contains("سامان") -> "saman-01.svg"
        name.contains("سرمایه") -> "sarmaye-17.svg"
        name.contains("سپه") -> "sepah-24.svg"
        name.contains("شهر") -> "shahr-25.svg"
        name.contains("سینا") -> "sina-23.svg"
        name.contains("تجارت") -> "tejarat-13.svg"
        name.contains("حکمت") -> "hekmat-30.svg"
        name.contains("توسعه صادرات") -> "tosesaderat-18.svg"
        name.contains("توسعه تعاون") -> "tosetaavon-19.svg"
        name.contains("رسالت") || name.contains("resalat", ignoreCase = true) -> "resalat.svg"
        name.contains("ایران زمین") || name.contains("ایران‌زمین") -> "iran_zamin.svg"
        name.contains("خاور میانه") || name.contains("خاورمیانه") -> "khavar_mianeh.svg"
        name.contains("نور") -> "noor.svg"
        name.contains("ملل") -> "melall.svg"
        name.contains("کاسپین") || name.contains("caspian", ignoreCase = true) -> "caspian.svg"
        name.contains("بلو") || name.contains("blu", ignoreCase = true) -> "blu.svg"
        else -> null
    }
}

@Composable
fun IranianBankLogo(
    bankName: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    hasCircleBg: Boolean = false,
    colorFilter: ColorFilter? = null
) {
    val name = bankName.trim()
    val baseModifier = if (hasCircleBg) {
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
    } else {
        modifier.size(size)
    }

    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
    }
    
    // لوگوهای کامل‌تر LogoS اول، بعد لوگوهای قدیمی
    val assetFile = remember(name) {
        com.example.ui.layer.BankLogos.assetFor(name) ?: getSvgFilenameForBank(name)?.let { "banks/$it" }
    }

    Box(
        modifier = baseModifier,
        contentAlignment = Alignment.Center
    ) {
        if (assetFile != null) {
            val assetPath = "file:///android_asset/$assetFile"
            val imageModifier = if (hasCircleBg) Modifier.size(size * 0.85f) else Modifier.fillMaxSize()
            
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(assetPath)
                    .crossfade(true)
                    .build(),
                contentDescription = "$bankName logo",
                imageLoader = imageLoader,
                modifier = imageModifier,
                colorFilter = colorFilter
            )
        } else {
            // Fallback for banks without SVG assets (e.g. Blu, Resalat)
            val canvasSizeModifier = if (hasCircleBg) Modifier.size(size * 0.85f) else Modifier.size(size)
            Canvas(modifier = canvasSizeModifier) {
                val w = this.size.width
                val h = this.size.height
                val cx = w / 2f
                val cy = h / 2f
                val r = minOf(w, h) / 2f
                // We could apply a tint to the fallback logo but it's complex, we'll ignore for now or wrap in layer
                drawFallbackLogo(name, cx, cy, r)
            }
        }
    }
}


private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFallbackLogo(
    name: String,
    cx: Float,
    cy: Float,
    r: Float
) {
    val w = this.size.width
    val h = this.size.height
    when {
        name.contains("بلو") || name.contains("Blu", ignoreCase = true) -> {
            val cyanColor = Color(0xFF06B6D4)
            val navyColor = Color(0xFF0891B2)
            drawCircle(color = Color.White, radius = r)
            drawCircle(color = cyanColor.copy(alpha = 0.7f), radius = r * 0.38f, center = Offset(cx - r * 0.15f, cy))
            drawCircle(color = navyColor.copy(alpha = 0.8f), radius = r * 0.38f, center = Offset(cx + r * 0.15f, cy))
        }
        name.contains("رسالت") || name.contains("Resalat", ignoreCase = true) -> {
            val bgTeal = Color(0xFF0EA5E9)
            val goldColor = Color(0xFFF59E0B)
            drawCircle(color = bgTeal, radius = r)
            drawCircle(color = Color.White.copy(alpha = 0.2f), radius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.1f))
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx - r * 0.45f, cy - r * 0.15f)
                cubicTo(cx - r * 0.75f, cy - r * 0.45f, cx - r * 0.75f, cy + r * 0.45f, cx, cy)
                cubicTo(cx + r * 0.75f, cy - r * 0.45f, cx + r * 0.75f, cy + r * 0.45f, cx + r * 0.45f, cy - r * 0.15f)
                close()
            }
            drawPath(path = path, color = goldColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.14f))
        }
        name.contains("مرکزی") || name.contains("Markazi", ignoreCase = true) || name.contains("Central", ignoreCase = true) -> {
            val bgBlue = Color(0xFF0F172A)
            val goldColor = Color(0xFFCA8A04)
            drawCircle(color = bgBlue, radius = r)
            drawCircle(color = Color.White.copy(alpha = 0.25f), radius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.1f))
            drawCircle(color = goldColor, radius = r * 0.45f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.08f))
            drawCircle(color = goldColor, radius = r * 0.22f)
            for (i in 0 until 8) {
                val angle = (i * 45) * (Math.PI / 180f)
                val x1 = cx + (r * 0.25f * Math.cos(angle)).toFloat()
                val y1 = cy + (r * 0.25f * Math.sin(angle)).toFloat()
                val x2 = cx + (r * 0.42f * Math.cos(angle)).toFloat()
                val y2 = cy + (r * 0.42f * Math.sin(angle)).toFloat()
                drawLine(color = goldColor, start = Offset(x1, y1), end = Offset(x2, y2), strokeWidth = r * 0.07f)
            }
        }
        name.contains("کاسپین") || name.contains("Caspian", ignoreCase = true) -> {
            val bgTeal = Color(0xFF047857)
            val goldColor = Color(0xFFF59E0B)
            drawCircle(color = bgTeal, radius = r)
            drawCircle(color = Color.White.copy(alpha = 0.25f), radius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.1f))
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx - r * 0.55f, cy + r * 0.1f)
                quadraticTo(cx - r * 0.2f, cy - r * 0.4f, cx, cy)
                quadraticTo(cx + r * 0.2f, cy + r * 0.4f, cx + r * 0.55f, cy - r * 0.1f)
                quadraticTo(cx + r * 0.2f, cy + r * 0.25f, cx, cy - r * 0.05f)
                quadraticTo(cx - r * 0.2f, cy - r * 0.25f, cx - r * 0.55f, cy + r * 0.1f)
                close()
            }
            drawPath(path = path, color = goldColor)
        }
        else -> {
            val bgSlate = Color(0xFF475569)
            drawCircle(color = bgSlate, radius = r)
            drawCircle(color = Color.White.copy(alpha = 0.25f), radius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.1f))
            drawRect(color = Color.White, topLeft = Offset(cx - r * 0.45f, cy + r * 0.25f), size = Size(r * 0.9f, r * 0.12f))
            drawRect(color = Color.White, topLeft = Offset(cx - r * 0.45f, cy - r * 0.4f), size = Size(r * 0.9f, r * 0.12f))
            drawRect(color = Color.White, topLeft = Offset(cx - r * 0.32f, cy - r * 0.25f), size = Size(r * 0.12f, r * 0.45f))
            drawRect(color = Color.White, topLeft = Offset(cx - r * 0.06f, cy - r * 0.25f), size = Size(r * 0.12f, r * 0.45f))
            drawRect(color = Color.White, topLeft = Offset(cx + r * 0.2f, cy - r * 0.25f), size = Size(r * 0.12f, r * 0.45f))
        }
    }
}
