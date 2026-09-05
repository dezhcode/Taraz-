package com.example.utils

object NumberToPersianWords {
    private val yekan = arrayOf("صفر", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه")
    private val dahgan = arrayOf("", "ده", "بیست", "سی", "چهل", "پنجاه", "شصت", "هفتاد", "هشتاد", "نود")
    private val dahha = arrayOf("ده", "یازده", "دوازده", "سیزده", "چهارده", "پانزده", "شانزده", "هفده", "هجده", "نوزده")
    private val sadgan = arrayOf("", "صد", "دویست", "سیصد", "چهارصد", "پانصد", "ششصد", "هفتصد", "هشتصد", "نهصد")
    private val base = arrayOf("", "هزار", "میلیون", "میلیارد", "تریلیون", "کوادریلیون", "کوینتیلیون")

    fun convert(number: Long): String {
        if (number == 0L) return yekan[0]
        if (number < 0) return "منفی " + convert(-number)
        
        var num = number
        val parts = mutableListOf<String>()
        var count = 0
        
        while (num > 0) {
            val part = (num % 1000).toInt()
            if (part != 0) {
                val partStr = convertLessThanOneThousand(part)
                parts.add(0, partStr + if (base[count].isNotEmpty()) " ${base[count]}" else "")
            }
            num /= 1000
            count++
        }
        
        return parts.joinToString(" و ")
    }

    private fun convertLessThanOneThousand(number: Int): String {
        val str = mutableListOf<String>()
        val h = number / 100
        val t = (number % 100) / 10
        val u = number % 10

        if (h > 0) str.add(sadgan[h])
        if (t == 1) {
            str.add(dahha[u])
        } else {
            if (t > 1) str.add(dahgan[t])
            if (u > 0) str.add(yekan[u])
        }
        
        return str.joinToString(" و ")
    }
}

fun toPersianDigits(text: String): String {
    return text.replace('0', '۰')
        .replace('1', '۱')
        .replace('2', '۲')
        .replace('3', '۳')
        .replace('4', '۴')
        .replace('5', '۵')
        .replace('6', '۶')
        .replace('7', '۷')
        .replace('8', '۸')
        .replace('9', '۹')
}
