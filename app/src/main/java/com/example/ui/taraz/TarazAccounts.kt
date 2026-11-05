package com.example.ui.taraz

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AccountType
import com.example.data.BankCard
import com.example.data.isCash
import com.example.data.maskedNumber
import com.example.ui.FinanceViewModel

@Composable
fun TarazAccountsScreen(
    vm: FinanceViewModel,
    onBack: () -> Unit,
    onAccountClick: (BankCard) -> Unit,
    onAddAccount: () -> Unit,
    onTransfer: () -> Unit
) {
    val cards by vm.cards.collectAsStateWithLifecycle()
    val total by vm.totalBalance.collectAsStateWithLifecycle()
    val senders by vm.bankSenders.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(Tz.Ground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        TzTopBar(
            "حساب‌ها",
            onBack = onBack,
            subtitle = if (cards.isEmpty()) "هنوز حسابی نساخته‌اید" else "${TzFormat.fa(cards.size)} حساب · روی هر حساب بزنید تا جزئیاتش را ببینید"
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                    Text("موجودی کل", style = tzText(13.sp, color = Tz.Muted))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(TzFormat.money(total), style = tzText(40.sp, FontWeight.ExtraLight, letterSpacing = (-1.5).sp))
                        Spacer(Modifier.width(6.dp))
                        Text("تومان", style = tzText(14.sp), modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
            }
            if (cards.isEmpty()) {
                item {
                    EmptyNote(
                        "حسابی ندارید",
                        "کارت بانکی، پول نقد یا هر جایی که پول دارید را به‌عنوان حساب اضافه کنید تا موجودی کل درست باشد."
                    )
                }
            }
            items(cards, key = { it.id }) { card ->
                val smsOn = senders.any { it.isEnabled && card.bankName.contains(it.bankName.replace("بانک", "").trim()) }
                AccountRow(card, smsOn) { onAccountClick(card) }
            }
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("افزودن حساب", onClick = onAddAccount, icon = Icons.Filled.Add)
            if (cards.size >= 2) {
                SecondaryButton("انتقال بین حساب‌ها", onClick = onTransfer, icon = Icons.Filled.SwapHoriz, height = 50.dp)
            }
        }
    }
}

@Composable
private fun AccountRow(card: BankCard, smsOn: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(20.dp), ambientColor = Color(0x140E1116), spotColor = Color(0x140E1116))
            .clip(RoundedCornerShape(20.dp))
            .background(Tz.Surface)
            .tzClick(onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AccountChip(card.bankName, card.isCash)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(card.bankName, style = tzText(15.sp, FontWeight.SemiBold), maxLines = 1)
                if (card.maskedNumber.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Text(TzFormat.fa(card.maskedNumber), style = tzText(12.sp, color = Tz.Muted))
                }
            }
            when {
                card.isCash -> TzTag("پول نقد", Tz.Field, Tz.Muted)
                card.accountType == AccountType.BANK && smsOn -> TzTag("پیامک خودکار روشن", Tz.GreenTint, Tz.GreenText)
                else -> TzTag("ثبت دستی", Tz.Field, Tz.Muted)
            }
        }
        Text(TzFormat.money(card.balance), style = tzText(15.sp, FontWeight.SemiBold, if (card.balance < 0) Tz.Amber else Tz.Ink))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color(0xFF8E95A2), modifier = Modifier.size(20.dp))
    }
}

