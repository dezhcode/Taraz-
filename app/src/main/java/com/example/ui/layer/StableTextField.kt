package com.example.ui.layer

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation

/**
 * همان BasicTextField با مقدار String، ولی وقتی والد متن را دوباره قالب‌بندی می‌کند (جداکنندهٔ هزارگان،
 * فاصلهٔ شمارهٔ کارت) مکان‌نما بعد از همان رقمی می‌ماند که کاربر تایپ کرد.
 *
 * پیش‌تر BasicTextField(String) مکان‌نما را روی همان شمارهٔ نویسه نگه می‌داشت: با رقم چهارم «۵۵۸۸»
 * می‌شد «۵٬۵۸۸» ولی مکان‌نما میان رقم‌ها می‌ماند و رقم‌های بعدی جلوی آن نوشته می‌شدند.
 */
@Composable
fun StableTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle.Default,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    cursorBrush: Brush
) {
    // آخرین ویرایش خود کاربر (با مکان‌نمایش) و متنی که برای والد فرستادیم.
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    var sent by remember { mutableStateOf<String?>(null) }

    val shown = when {
        field.text == value -> field
        // والد ویرایش ما را قالب‌بندی کرد یا نپذیرفت: مکان‌نما بعد از همان رقم‌ها
        field.text == sent -> TextFieldValue(
            value, TextRange(CursorKeeper.cursorAfterFormat(field.text, field.selection.end, value))
        )
        // متن از بیرون عوض شد (پاک شدن، پر شدن خودکار): مکان‌نما آخر متن
        else -> TextFieldValue(value, TextRange(value.length))
    }

    BasicTextField(
        value = shown,
        onValueChange = { new ->
            field = new
            if (new.text != shown.text) {
                sent = new.text
                onValueChange(new.text)
            }
        },
        modifier = modifier,
        textStyle = textStyle,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        cursorBrush = cursorBrush
    )
}
