package com.example.ui.taraz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Category
import com.example.data.Transaction
import com.example.data.isTransfer
import com.example.ui.CategoryIcons

/** Icon for a transaction's category: the user's own category first, then a guess by name. */
fun categoryIcon(tx: Transaction, categories: List<Category>): ImageVector {
    if (tx.isTransfer) return Icons.Filled.SwapHoriz
    val own = categories.firstOrNull { it.name == tx.category }
    if (own != null) return CategoryIcons.vectorFor(own.iconKey)
    val key = when {
        tx.category.contains("قسط") || tx.category.contains("وام") -> "loan"
        tx.category.contains("غذا") || tx.category.contains("خوراک") || tx.category.contains("رستوران") -> "food"
        tx.category.contains("حقوق") -> "wage"
        tx.category.contains("حمل") || tx.category.contains("تاکسی") -> "transport"
        tx.category.contains("قبض") || tx.category.contains("قبوض") -> "bill"
        tx.category.contains("پوشاک") -> "clothing"
        tx.category.contains("تفریح") -> "fun"
        tx.category.contains("خرید") -> "shopping"
        tx.category.contains("سلامت") || tx.category.contains("دارو") -> "health"
        else -> null
    }
    return if (key != null) CategoryIcons.vectorFor(key) else Icons.Filled.Category
}

/** One transaction as a white card row. */
@Composable
fun TxCardRow(
    tx: Transaction,
    categories: List<Category>,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val transfer = tx.isTransfer
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Tz.RowShape)
            .background(Tz.Surface)
            .then(if (onClick != null) Modifier.tzClick(onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconWell(
            categoryIcon(tx, categories),
            background = when {
                transfer -> Tz.CobaltTint
                !tx.isExpense -> Tz.GreenTint
                else -> Tz.Field
            },
            tint = when {
                transfer -> Tz.Cobalt
                !tx.isExpense -> Tz.Green
                else -> Tz.Ink
            }
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(tx.title.ifBlank { tx.category }, style = tzText(14.sp, FontWeight.Medium), maxLines = 1)
            Text(
                if (transfer) "انتقال · در جمع هزینه نیست" else "${tx.category} · ${tx.bankName}",
                style = tzText(12.sp, color = Tz.Muted),
                maxLines = 1
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (transfer) TzFormat.money(tx.amount) else TzFormat.signed(tx.amount, tx.isExpense),
            style = tzText(14.sp, FontWeight.SemiBold, if (transfer) Tz.Muted else if (tx.isExpense) Tz.Ink else Tz.Green)
        )
    }
}

/**
 * A row on a timeline: a time or date on the reading side, a rail with a node,
 * and the content. [isLast] stops the rail at the node.
 */
@Composable
fun TimelineRow(
    label: String,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    node: @Composable () -> Unit = { TimelineDot() },
    content: @Composable () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 20.dp)
    ) {
        Text(
            label,
            style = tzText(12.sp, color = Tz.Muted, lineHeight = 17.sp),
            modifier = Modifier.width(48.dp).padding(top = 16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .width(18.dp)
                .fillMaxHeight()
                .drawBehind {
                    val x = size.width / 2f
                    val top = if (isFirst) 26.dp.toPx() else 0f
                    val bottom = if (isLast) 26.dp.toPx() else size.height
                    if (bottom > top) {
                        drawLine(Tz.Rail, Offset(x, top), Offset(x, bottom), strokeWidth = 2.dp.toPx())
                    }
                },
            contentAlignment = Alignment.TopCenter
        ) {
            Box(Modifier.padding(top = 20.dp)) { node() }
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).padding(bottom = 10.dp)) { content() }
    }
}

@Composable
fun TimelineDot(color: Color = Tz.Ink, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun TimelineRing(color: Color = Tz.AmberDot, fill: Color = Tz.Ground) {
    Box(
        Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(fill)
            .border(2.5.dp, color, CircleShape)
    )
}

/** Ask for a number (daily budget, …) in a simple dialog. */
@Composable
fun AmountDialog(
    title: String,
    initial: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
    help: String? = null
) {
    var text by remember { mutableStateOf(if (initial > 0) initial.toString() else "") }
    val value = TzFormat.digitsToLatin(text).toLongOrNull() ?: 0L
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Tz.Surface,
        title = { Text(title, style = tzText(18.sp, FontWeight.Bold)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (help != null) Text(help, style = tzText(13.sp, color = Tz.Muted, lineHeight = 22.sp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = TzFormat.digitsToLatin(it).take(12) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = tzText(18.sp, FontWeight.SemiBold),
                    label = { Text("مبلغ به تومان", style = tzText(13.sp)) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (value > 0) Text(TzFormat.money(value) + " تومان", style = tzText(13.sp, color = Tz.Muted))
            }
        },
        confirmButton = {
            TextAction("ذخیره", onClick = { onConfirm(value) })
        },
        dismissButton = {
            TextAction("انصراف", onClick = onDismiss, color = Tz.Muted)
        }
    )
}

/** Shared placeholder for empty lists: says what is missing and what to do. */
@Composable
fun EmptyNote(title: String, hint: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(Tz.RowShape)
            .background(Tz.Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, style = tzText(14.sp, FontWeight.SemiBold))
        Text(hint, style = tzText(12.sp, color = Tz.Muted, lineHeight = 20.sp))
    }
}

/** Placeholder for a bank logo: the account's colour with its first word. */
@Composable
fun AccountChip(name: String, isCash: Boolean, width: Dp = 54.dp, height: Dp = 36.dp) {
    Box(
        Modifier
            .size(width, height)
            .clip(RoundedCornerShape(8.dp))
            .background(accountColor(name, isCash))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        if (isCash) {
            androidx.compose.material3.Icon(Icons.Filled.Payments, null, tint = Color.White, modifier = Modifier.size(16.dp))
        } else {
            Text(name.replace("بانک", "").trim().split(" ").firstOrNull().orEmpty(), style = tzText(11.sp, FontWeight.Bold, Color.White), maxLines = 1)
        }
    }
}

fun Modifier.tzClick(onClick: () -> Unit): Modifier =
    this.clickable(role = Role.Button, onClick = onClick)
