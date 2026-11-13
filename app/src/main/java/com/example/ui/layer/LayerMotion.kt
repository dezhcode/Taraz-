package com.example.ui.layer

import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

/** نوع لرزش‌های برنامه؛ کوتاه و کم، نه برای هر چیزی */
enum class Buzz {
    /** دکمه، کلید، انتخاب‌گر، دسته‌بندی */
    TICK,
    /** هر رقم صفحه‌کلید مبلغ */
    KEY,
    /** ثبت موفق */
    CONFIRM,
    /** مبلغ خالی یا نادرست */
    REJECT,
    /** لحظهٔ بزرگ: قسط آخر */
    PARTY
}

/**
 * لرزش لمسی. از «بازخورد لمسی» خود اندروید استفاده می‌کند، پس مجوز جدا نمی‌خواهد و اگر کاربر
 * بازخورد لمسی سیستم را خاموش کرده باشد هم نمی‌لرزد. کلید «لرزش» تنظیمات تراز آن را خاموش می‌کند.
 */
object Haptics {
    private const val PREFS = "fidar_prefs"
    private const val KEY = "haptics_enabled"

    @Volatile var enabled: Boolean = true
        private set

    fun load(context: Context) {
        enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)
    }

    fun set(context: Context, on: Boolean) {
        enabled = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    }

    fun perform(view: View, buzz: Buzz) {
        if (!enabled) return
        val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val c = when (buzz) {
            Buzz.TICK -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) HapticFeedbackConstants.CONTEXT_CLICK else HapticFeedbackConstants.KEYBOARD_TAP
            Buzz.KEY -> HapticFeedbackConstants.KEYBOARD_TAP
            Buzz.CONFIRM, Buzz.PARTY -> if (modern) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
            Buzz.REJECT -> if (modern) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(c)
        // جشن: دو ضربهٔ دیگر با فاصله
        if (buzz == Buzz.PARTY) {
            view.postDelayed({ view.performHapticFeedback(c) }, 160)
            view.postDelayed({ view.performHapticFeedback(c) }, 360)
        }
    }
}

/** تابعی که در هر کامپوزبل لرزش می‌دهد: `val buzz = rememberBuzz(); buzz(Buzz.TICK)` */
@Composable
fun rememberBuzz(): (Buzz) -> Unit {
    val view = LocalView.current
    return remember(view) { { b: Buzz -> Haptics.perform(view, b) } }
}

/** دکمه‌ها هنگام فشار کمی کوچک می‌شوند و با فنر برمی‌گردند */
fun Modifier.pressScale(interaction: InteractionSource, pressed: Float = 0.96f): Modifier = composed {
    val down by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (down) pressed else 1f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "press"
    )
    graphicsLayer { scaleX = s; scaleY = s }
}

/** با هر بار بالا رفتن [trigger] چند بار به چپ و راست می‌لرزد (کادر نادرست) */
fun Modifier.shake(trigger: Int): Modifier = composed {
    val x = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        for (v in listOf(-10f, 9f, -6f, 4f, -2f, 0f)) x.animateTo(v, tween(45))
    }
    graphicsLayer { translationX = x.value * density }
}

/**
 * کاغذرنگی که از وسط بالای صفحه بیرون می‌ریزد؛ یک بار، حدود یک و نیم ثانیه. فقط برای لحظه‌های بزرگ.
 */
@Composable
fun Confetti(modifier: Modifier = Modifier, onDone: () -> Unit = {}) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(1600))
        onDone()
    }
    val colors = remember { listOf(Color(0xFF2BB283), Color(0xFF3D66FF), Color(0xFFF5A524), Color(0xFFF06368), Color(0xFF9AB0FF), Color(0xFFFFE7A8)) }
    // ذره‌ها ثابت‌اند تا هر فریم همان مسیرها کشیده شوند
    val parts = remember {
        val r = java.util.Random(7)
        List(60) {
            val angle = Math.toRadians(200.0 + r.nextDouble() * 140.0)
            val speed = 0.55f + r.nextFloat() * 0.65f
            floatArrayOf(Math.cos(angle).toFloat() * speed, Math.sin(angle).toFloat() * speed, r.nextFloat() * 720f, it.toFloat())
        }
    }
    Canvas(modifier.fillMaxSize()) {
        val p = t.value
        val origin = Offset(size.width / 2, size.height * 0.32f)
        val reach = size.minDimension * 0.75f
        parts.forEach { (vx, vy, spin, i) ->
            // بیرون پرتاب، بعد گرانش پایین می‌کشد
            val x = origin.x + vx * reach * p
            val y = origin.y + vy * reach * p + 900.dp.toPx() * p * p * 0.6f
            val alpha = (1f - p).coerceIn(0f, 1f)
            rotate(spin * p, Offset(x, y)) {
                drawRect(
                    colors[i.toInt() % colors.size].copy(alpha = alpha),
                    topLeft = Offset(x - 4.dp.toPx(), y - 6.dp.toPx()),
                    size = Size(8.dp.toPx() - (i.toInt() % 3).dp.toPx(), 12.dp.toPx() - (i.toInt() % 3 * 2).dp.toPx())
                )
            }
        }
    }
}
