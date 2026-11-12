package com.example.ui.layer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AccountType
import com.example.data.BankCard
import com.example.data.isCash
import kotlin.math.absoluteValue

/** رنگ کارت: رنگ بانک اگر بانک شناخته شد، وگرنه رنگ حساب در فهرست */
fun cardBrandColor(card: BankCard, fallback: Color): Color {
    if (card.isCash) return Color(0xFF0B7A57)
    val asset = BankLogos.assetFor(card.bankName, card.cardNumber)
    return BankLogos.brandColor(asset)?.let { Color(it) } ?: fallback
}

/** ۶۱۰۴۳۳۷۴۱۲۹۰۴۲۱۸ → «۶۱۰۴ ۳۳۷۴ ۱۲۹۰ ۴۲۱۸» یا «•••• •••• •••• ۴۲۱۸» */
private fun cardNumberText(number: String, revealed: Boolean): String {
    val digits = number.filter { it.isDigit() }
    if (digits.length < 4) return ""
    val shown = if (revealed) digits else "•".repeat((digits.length - 4).coerceAtLeast(12)) + digits.takeLast(4)
    return fa(shown.chunked(4).joinToString(" "))
}

/**
 * کارت «دوتکه»: بالا رنگ بانک با لوگوی خطی سفید و کج پشتش، نام بانک و شمارهٔ کارت؛
 * پایین سطح روشن با نام صاحب کارت و موجودی درشت. دکمهٔ چشم شمارهٔ کامل را نشان یا پنهان می‌کند.
 */
@Composable
fun BankCardView(
    card: BankCard,
    fallbackColor: Color,
    revealed: Boolean,
    onToggleReveal: () -> Unit,
    modifier: Modifier = Modifier,
    holderName: String? = null,
    hideBalance: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val asset = remember(card.bankName, card.cardNumber, card.accountType) {
        if (card.isCash) null else BankLogos.assetFor(card.bankName, card.cardNumber)
    }
    val line = remember(asset) { BankLogos.lineAssetFor(asset) }
    val c1 = remember(card.bankName, card.cardNumber, card.accountType, fallbackColor) { cardBrandColor(card, fallbackColor) }
    val c2 = lerp(c1, Color.Black, 0.45f)
    val hasNumber = card.cardNumber.count { it.isDigit() } >= 4

    Column(
        modifier
            .fillMaxWidth()
            .aspectRatio(1.586f)
            .shadow(14.dp, RoundedCornerShape(22.dp), ambientColor = c2.copy(alpha = 0.5f), spotColor = c2.copy(alpha = 0.5f))
            .clip(RoundedCornerShape(22.dp))
            .background(L.Surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        // ── بالا: رنگ بانک
        Box(
            Modifier.fillMaxWidth().weight(0.6f).clipToBounds().background(Brush.linearGradient(listOf(c1, c2)))
        ) {
            // لوگوی خطی بزرگ و کج، کمی بیرون‌زده از گوشه
            if (line != null) {
                Box(
                    Modifier.align(Alignment.TopEnd)
                        .wrapContentSize(Alignment.TopEnd, unbounded = true)
                        .offset(x = (-22).dp, y = (-48).dp)
                        .size(210.dp)
                        .rotate(-18f).graphicsLayer { alpha = 0.32f }
                ) { BankLogo(line, Modifier.fillMaxSize()) }
            } else if (card.isCash) {
                Icon(
                    Icons.Default.Payments, null, tint = Color.White.copy(alpha = 0.22f),
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = (-14).dp, y = (-20).dp).size(150.dp).rotate(-18f)
                )
            }
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (asset != null) {
                        Box(
                            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) { BankLogo(asset, Modifier.fillMaxSize(), card.bankName) }
                    }
                    Text(card.bankName, style = lt(15.sp, FontWeight.Bold, Color.White), maxLines = 1, modifier = Modifier.weight(1f))
                    if (hasNumber) {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).clickable(onClick = onToggleReveal)
                                .semantics { contentDescription = if (revealed) "پنهان کردن شمارهٔ کارت" else "نمایش شمارهٔ کارت" },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Text(
                    when {
                        hasNumber -> cardNumberText(card.cardNumber, revealed)
                        card.isCash -> "وجه نقد"
                        card.accountType == AccountType.OTHER -> "حساب"
                        else -> "بدون شمارهٔ کارت"
                    },
                    style = lt(if (hasNumber) 19.sp else 14.sp, FontWeight.Medium, Color.White, letterSpacing = if (hasNumber) 1.5.sp else 0.sp)
                        .copy(textDirection = TextDirection.Ltr),
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        // ── پایین: صاحب کارت و موجودی
        Row(
            Modifier.fillMaxWidth().weight(0.4f).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (card.isCash) "حساب" else "صاحب کارت", style = lt(11.sp, color = L.Muted))
                Text(
                    // نامی که کاربر برای خود کارت نوشته اول، بعد نام خودش در تراز
                    (card.cardHolderName.takeIf { it.isNotBlank() && it != "کاربر تراز" }
                        ?: holderName?.takeIf { it.isNotBlank() } ?: card.cardHolderName).ifBlank { "—" },
                    style = lt(14.sp, FontWeight.SemiBold), maxLines = 1
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("موجودی", style = lt(11.sp, color = L.Muted))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(if (hideBalance) "••••••" else money(card.balance), style = lt(18.sp, FontWeight.Bold, if (card.balance < 0) L.RedText else L.Ink))
                    Text(" ${com.example.data.Money.label}", style = lt(11.sp, color = L.Muted), modifier = Modifier.padding(bottom = 2.dp))
                }
            }
        }
    }
}

/**
 * کارت‌ها در یک ردیف ورق‌خوردنی: کارت وسط کامل، کارت‌های کناری کمی کوچک‌تر و کم‌رنگ‌تر
 * و لبه‌شان پیدا؛ نقطه‌های زیر نشان می‌دهند کجای فهرستید.
 */
@Composable
fun BankCardPager(
    cards: List<BankCard>,
    holderName: String?,
    hideBalance: Boolean,
    onOpen: (BankCard) -> Unit,
    modifier: Modifier = Modifier
) {
    if (cards.isEmpty()) return
    val pager = rememberPagerState { cards.size }
    val revealed = remember { mutableStateMapOf<Int, Boolean>() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = 32.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val card = cards[page]
            val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
            BankCardView(
                card = card,
                fallbackColor = cardColor(page),
                revealed = revealed[card.id] == true,
                onToggleReveal = { revealed[card.id] = revealed[card.id] != true },
                holderName = holderName,
                hideBalance = hideBalance,
                onClick = { onOpen(card) },
                modifier = Modifier.padding(vertical = 10.dp).graphicsLayer {
                    val scale = 1f - 0.07f * offset
                    scaleX = scale; scaleY = scale
                    alpha = 1f - 0.35f * offset
                }
            )
        }
        if (cards.size > 1) {
            Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                cards.indices.forEach { i ->
                    val on = i == pager.currentPage
                    Box(
                        Modifier.height(6.dp).width(if (on) 18.dp else 6.dp).clip(CircleShape)
                            .background(if (on) L.Primary else L.Line)
                    )
                }
            }
        }
    }
}
