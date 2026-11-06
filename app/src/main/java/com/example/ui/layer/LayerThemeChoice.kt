package com.example.ui.layer

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** ذخیرهٔ انتخاب روشن/تیره روی گوشی */
object ThemePrefs {
    private const val FILE = "taraz_prefs"
    private const val KEY = "theme_mode"

    /** null یعنی کاربر هنوز انتخاب نکرده (اولین اجرا) */
    fun load(context: Context): Boolean? =
        when (context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)) {
            "dark" -> true
            "light" -> false
            else -> null
        }

    fun save(context: Context, dark: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY, if (dark) "dark" else "light").apply()
    }
}

/**
 * اولین اجرا: «تراز روشن باشد یا تیره؟» — با انتخاب هر گزینه، خود صفحه همان لحظه
 * به آن حالت درمی‌آید تا کاربر نتیجه را ببیند.
 */
@Composable
fun LayerThemeChooser(onDone: () -> Unit) {
    val dark = LayerTheme.dark
    Column(
        Modifier.fillMaxSize().background(L.Bg).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 24.dp).padding(bottom = 24.dp)
    ) {
        Column(Modifier.padding(top = 56.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(L.Primary), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = com.example.R.drawable.logo_taraz_mark),
                    contentDescription = "تراز",
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier.size(34.dp)
                )
            }
            Text("تراز را چطور ببینید؟", style = lt(26.sp, FontWeight.Bold), modifier = Modifier.padding(top = 8.dp))
            Text(
                "روشن برای روز و تیره برای شب و چشم راحت‌تر. بعداً از «تنظیمات › ظاهر» هم عوضش می‌کنید.",
                style = lt(14.sp, color = L.Muted, lineHeight = 26.sp)
            )
        }

        Row(
            Modifier.padding(top = 32.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ThemeOption("روشن", isDarkPreview = false, selected = !dark, modifier = Modifier.weight(1f)) { LayerTheme.dark = false }
            ThemeOption("تیره", isDarkPreview = true, selected = dark, modifier = Modifier.weight(1f)) { LayerTheme.dark = true }
        }

        Spacer(Modifier.weight(1f))
        BigButton("ادامه", onClick = onDone)
    }
}

/** پیش‌نمایش کوچک یک صفحهٔ تراز با رنگ‌های ثابت هر حالت (مستقل از حالت فعلی) */
@Composable
private fun ThemeOption(label: String, isDarkPreview: Boolean, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg = if (isDarkPreview) Color(0xFF0B0E13) else Color(0xFFDDE1E8)
    val surface = if (isDarkPreview) Color(0xFF161A22) else Color.White
    val line = if (isDarkPreview) Color(0xFF2E3542) else Color(0xFFE3E6EC)
    val primary = if (isDarkPreview) Color(0xFF3D66FF) else Color(0xFF2250F4)

    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(L.Surface)
            .border(if (selected) 2.5.dp else 1.dp, if (selected) L.Primary else L.Rail, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .semantics { role = Role.RadioButton; this.selected = selected }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // گوشی کوچک
        Column(
            Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)).background(bg).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.width(54.dp).height(8.dp).clip(CircleShape).background(line))
            Column(
                Modifier.fillMaxWidth().height(62.dp).clip(RoundedCornerShape(12.dp)).background(primary).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(Modifier.width(36.dp).height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.6f)))
                Box(Modifier.width(70.dp).height(10.dp).clip(CircleShape).background(Color.White))
            }
            repeat(3) {
                Row(
                    Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(10.dp)).background(surface).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(line))
                    Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(line))
                    Box(Modifier.width(22.dp).height(6.dp).clip(CircleShape).background(if (it == 0) primary else line))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(22.dp).clip(CircleShape)
                    .background(if (selected) L.Primary else L.Surface)
                    .border(2.dp, if (selected) L.Primary else L.Chevron, CircleShape),
                contentAlignment = Alignment.Center
            ) { if (selected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
            Text(label, style = lt(16.sp, FontWeight.Bold), textAlign = TextAlign.Center)
        }
    }
}
