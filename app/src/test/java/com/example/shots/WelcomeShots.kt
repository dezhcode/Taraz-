package com.example.shots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.layer.LayerPrivacyScreen
import com.example.ui.layer.LayerTheme
import com.example.ui.layer.TarazSplash
import com.example.ui.layer.WelcomeIntro
import com.example.ui.layer.WelcomeStart
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** تصویر اسپلش، صفحهٔ شروع و صفحه‌های معرفی (حالت تیره) */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class WelcomeShots {
    @get:Rule val compose = createComposeRule()

    private fun shot(name: String, dark: Boolean = true, content: @Composable () -> Unit) {
        LayerTheme.dark = dark
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { content() }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(800)
        compose.onRoot().captureRoboImage("build/tourshots/0_welcome_$name.png")
    }

    @Test fun splash() = shot("1_splash") { TarazSplash(animate = false) }
    @Test fun start() = shot("2_start") { WelcomeStart(name = "", onNameChange = {}, onStart = {}) }
    @Test fun startLight() = shot("2b_start_light", dark = false) { WelcomeStart(name = "", onNameChange = {}, onStart = {}) }
    @Test fun intro1() = shot("3_intro_1") { WelcomeIntro(onFinish = {}, initialPage = 0) }
    @Test fun intro2() = shot("3_intro_2") { WelcomeIntro(onFinish = {}, initialPage = 1) }
    @Test fun intro3() = shot("3_intro_3") { WelcomeIntro(onFinish = {}, initialPage = 2) }
    @Test fun intro4() = shot("3_intro_4") { WelcomeIntro(onFinish = {}, initialPage = 3) }
    @Test fun privacy() = shot("4_privacy") { LayerPrivacyScreen(onBack = {}) }
    @Test fun intro5() = shot("3_intro_5") { WelcomeIntro(onFinish = {}, initialPage = 4) }
}
