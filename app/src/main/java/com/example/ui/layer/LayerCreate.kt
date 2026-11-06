package com.example.ui.layer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * «ساختن مورد جدید» از دل هر انتخابگر: هر جا باید حساب، دسته یا شخص انتخاب شود و موردِ
 * دلخواه ثبت نشده، یک گزینهٔ «+ … جدید» هست که برگهٔ ساختن را روی همان صفحه باز می‌کند.
 * MainActivity این‌ها را فراهم می‌کند تا لازم نباشد هر برگه جداگانه پارامتر بگیرد.
 */
class Creators(
    val addAccount: () -> Unit,
    val addCategory: (isIncome: Boolean) -> Unit,
    val addPerson: () -> Unit
)

val LocalCreators = staticCompositionLocalOf<Creators?> { null }

/** چیپ «+ … جدید» در انتهای فهرست انتخاب */
@Composable
fun AddNewChip(label: String, onClick: () -> Unit) {
    LChip("+ $label", false, onClick = onClick)
}

@Composable
fun AddAccountChip() {
    LocalCreators.current?.let { c -> AddNewChip("حساب جدید") { c.addAccount() } }
}

@Composable
fun AddCategoryChip(isIncome: Boolean) {
    LocalCreators.current?.let { c -> AddNewChip("دستهٔ جدید") { c.addCategory(isIncome) } }
}

@Composable
fun AddPersonChip() {
    LocalCreators.current?.let { c -> AddNewChip("شخص جدید") { c.addPerson() } }
}

/**
 * وقتی موردی تازه به فهرست اضافه شد (مثلاً همین حالا از «+ حساب جدید» ساخته شد)،
 * [onNew] با آن صدا زده می‌شود تا انتخابگر همان را برگزیند.
 */
@Composable
fun <T> WatchNew(items: List<T>, key: (T) -> Any, onNew: (T) -> Unit) {
    var known by remember { mutableStateOf(items.map(key).toSet()) }
    LaunchedEffect(items) {
        val fresh = items.filter { key(it) !in known }
        if (fresh.isNotEmpty()) onNew(fresh.last())
        known = items.map(key).toSet()
    }
}
