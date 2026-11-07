package com.example.shots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.layer.LayerTheme
import com.example.ui.layer.SmsCardUi
import com.example.ui.layer.SmsManageScreen
import com.example.ui.layer.SmsManagedSender
import com.example.ui.layer.SmsSenderUi
import com.example.ui.layer.SmsWizardActions
import com.example.ui.layer.SmsWizardScreen
import com.example.ui.layer.SmsWizardState
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** مرحله‌های راه‌اندازی پیامک‌خوان (طرح ۱) و صفحهٔ مدیریت، با دادهٔ نمونه */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class SmsSetupShots {
    @get:Rule val compose = createComposeRule()

    private fun shot(name: String, dark: Boolean = true, content: @Composable () -> Unit) {
        LayerTheme.dark = dark
        compose.setContent { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { content() } }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)
        compose.onRoot().captureRoboImage("build/tourshots/0_sms_$name.png")
    }

    private val base = SmsWizardState(
        step = 0, permissionGranted = false, readerOn = false, cards = emptyList(), cardNumber = "", cardBankGuess = null,
        cardName = "", senders = emptyList(), sendersLoading = false, showAllSenders = false, selectedNumber = null,
        tab = 0, manualNumber = "", manualBank = "", error = null, summaryCard = "", summarySender = "", pendingCount = 0
    )

    private val melatCard = SmsCardUi("بانک ملت", "1234")
    private val senders = listOf(
        SmsSenderUi("981000123", "بانک ملت", 12, "برداشت: 4,800,000\nمانده: 95,200,000\nکارت: ****1234", true),
        SmsSenderUi("983000567", "بانک سامان", 4, "واریز 250,000,000 ریال به حساب شما\nمانده: 370,000,000", true)
    )

    @Test fun step1On() = shot("1_on") { SmsWizardScreen(base, SmsWizardActions()) }

    @Test fun step1OnLight() = shot("1b_on_light", dark = false) { SmsWizardScreen(base.copy(permissionGranted = true, readerOn = true), SmsWizardActions()) }

    @Test fun step2Card() = shot("2_card") {
        SmsWizardScreen(
            base.copy(step = 1, permissionGranted = true, readerOn = true, cards = listOf(melatCard), cardNumber = "6037 9971 2345 6789", cardBankGuess = "بانک ملی ایران"),
            SmsWizardActions()
        )
    }

    @Test fun step3Sender() = shot("3_sender") {
        SmsWizardScreen(
            base.copy(step = 2, permissionGranted = true, readerOn = true, cards = listOf(melatCard), senders = senders, selectedNumber = "981000123"),
            SmsWizardActions()
        )
    }

    @Test fun step3Manual() = shot("3b_sender_manual") {
        SmsWizardScreen(
            base.copy(step = 2, permissionGranted = true, readerOn = true, tab = 1, manualNumber = "981000123", manualBank = "بانک ملت"),
            SmsWizardActions()
        )
    }

    @Test fun step4Done() = shot("4_done") {
        SmsWizardScreen(
            base.copy(step = 3, permissionGranted = true, readerOn = true, summaryCard = "بانک ملت · ۱۲۳۴", summarySender = "981000123", pendingCount = 3),
            SmsWizardActions()
        )
    }

    @Test fun manage() = shot("5_manage") {
        SmsManageScreen(
            readerOn = true, permissionGranted = true,
            senders = listOf(SmsManagedSender("981000123", "بانک ملت", true), SmsManagedSender("983000567", "بانک سامان", false)),
            cards = listOf(melatCard), pendingCount = 3, unreadCount = 1,
            onBack = {}, onToggleReader = {}, onToggleSender = { _, _ -> }, onRemoveSender = {}, onAddSender = {}, onAddCard = {},
            onOpenReview = {}, onOpenUnread = {}
        )
    }
}
