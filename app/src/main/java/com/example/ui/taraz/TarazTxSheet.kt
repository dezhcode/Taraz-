package com.example.ui.taraz

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BankCard
import com.example.data.Category
import com.example.data.Transaction
import com.example.data.isTransfer
import com.example.utils.JalaliDate

/** Detail of one transaction: change its title, amount, category or account, or delete it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TarazTxSheet(
    tx: Transaction,
    cards: List<BankCard>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit,
    onDelete: (Transaction) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf(tx.title) }
    var amountText by remember { mutableStateOf(tx.amount.toString()) }
    var category by remember { mutableStateOf(tx.category) }
    var account by remember { mutableStateOf<String?>(tx.bankName) }
    var askDelete by remember { mutableStateOf(false) }

    val amount = amountText.toLongOrNull() ?: 0L
    val changed = title.trim() != tx.title || amount != tx.amount || category != tx.category || account != tx.bankName
    val transfer = tx.isTransfer
    val choices = categories.filter { it.isIncome == !tx.isExpense }.map { it.name }.let {
        if (tx.category in it) it else listOf(tx.category) + it
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Tz.Surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("جزئیات تراکنش", style = tzText(18.sp, FontWeight.Bold), modifier = Modifier.weight(1f))
                TextAction("بستن", onClick = onDismiss, color = Tz.Muted)
            }
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TzTag(
                    when {
                        transfer -> "انتقال"
                        tx.isExpense -> "هزینه"
                        else -> "درآمد"
                    },
                    Tz.Field, Tz.Ink
                )
                Text(
                    TzFormat.money(amount),
                    style = tzText(44.sp, FontWeight.ExtraLight, if (!tx.isExpense && !transfer) Tz.Green else Tz.Ink, letterSpacing = (-1.5).sp)
                )
                Text(
                    TzFormat.dayLabel(tx.date) + "، " + TzFormat.time(tx.date) + " · " + JalaliDate.fromTimestamp(tx.date).formatLong(),
                    style = tzText(12.sp, color = Tz.Muted, align = TextAlign.Center)
                )
            }

            if (transfer) {
                Text(
                    "انتقال بین حساب‌های خودتان ویرایش نمی‌شود. اگر اشتباه ثبت شده، حذفش کنید و دوباره ثبت کنید.",
                    style = tzText(13.sp, color = Tz.Muted, lineHeight = 22.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(Tz.RowShape)
                        .background(Tz.Ground)
                        .padding(12.dp)
                )
            } else {
                TzTextField("عنوان", title, { title = it.take(60) }, "مثلاً خرید سوپرمارکت")
                Spacer(Modifier.height(12.dp))
                TzTextField("مبلغ (تومان)", amountText, { amountText = TzFormat.digitsToLatin(it).take(12) }, "تومان", numeric = true)
                Spacer(Modifier.height(12.dp))
                Text("دسته", style = tzText(13.sp, FontWeight.SemiBold))
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
                    items(choices) { name ->
                        val on = name == category
                        Box(
                            Modifier
                                .heightIn(min = 38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (on) Tz.Cobalt else Tz.Field)
                                .tzClick { category = name }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(name, style = tzText(13.sp, FontWeight.SemiBold, if (on) Color.White else Tz.Ink))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(if (tx.isExpense) "از حساب" else "به حساب", style = tzText(13.sp, FontWeight.SemiBold))
                Spacer(Modifier.height(6.dp))
                AccountPicker(cards, account, if (tx.isExpense) "از" else "به", { account = it }, {}, Modifier.fillMaxWidth())
                Spacer(Modifier.height(18.dp))
                PrimaryButton(
                    when {
                        amount <= 0 -> "مبلغ را وارد کنید"
                        changed -> "ذخیرهٔ تغییرات"
                        else -> "تغییری نداده‌اید"
                    },
                    enabled = changed && amount > 0,
                    onClick = {
                        onSave(tx.copy(title = title.trim().ifBlank { category }, amount = amount, category = category, bankName = account ?: tx.bankName))
                    }
                )
            }

            Spacer(Modifier.height(8.dp))
            if (askDelete) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(Tz.RowShape)
                        .background(Tz.RedTint)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "این تراکنش حذف شود؟ " + if (tx.isExpense) "${TzFormat.money(tx.amount)} تومان به موجودی ${tx.bankName} برمی‌گردد."
                        else "${TzFormat.money(tx.amount)} تومان از موجودی ${tx.bankName} کم می‌شود.",
                        style = tzText(13.sp, color = Tz.RedText, lineHeight = 22.sp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Tz.Red)
                                .tzClick { onDelete(tx) },
                            contentAlignment = Alignment.Center
                        ) { Text("بله، حذف شود", style = tzText(14.sp, FontWeight.Bold, Color.White)) }
                        Spacer(Modifier.width(4.dp))
                        TextAction("انصراف", onClick = { askDelete = false }, color = Tz.Ink)
                    }
                }
            } else {
                TextAction("حذف تراکنش", onClick = { askDelete = true }, color = Tz.Red, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
