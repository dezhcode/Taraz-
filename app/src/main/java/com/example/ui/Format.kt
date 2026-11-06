package com.example.ui

import java.util.Locale

/** 1234567 → "1,234,567" (لاتین؛ برای نمایش فارسی از fa/money در LayerKit استفاده کنید) */
fun formatNumber(value: Long): String = String.format(Locale.ENGLISH, "%,d", value)
