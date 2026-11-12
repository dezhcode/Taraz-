package com.example.ui

import java.util.Locale

/** مبلغ 1234567 → "1,234,567" (یا 12.50 در دفتر دلاری؛ لاتین؛ برای نمایش فارسی از fa/money در LayerKit استفاده کنید) */
fun formatNumber(value: Long): String = com.example.data.Money.formatLatin(value)
