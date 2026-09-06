package com.example.data

/**
 * Card prefix (BIN/IIN) to bank, so typing a card number can identify its bank.
 *
 * The first six digits of an Iranian card identify the issuer. The names here
 * are spelled the way IranianBankLogo expects them, so a hit gives both a name
 * to fill in and a logo to show.
 *
 * Two caveats worth knowing:
 *  - Several banks were merged into بانک سپه (انصار، قوامین، مهر اقتصاد، حکمت،
 *    کوثر). Their old cards are still in wallets, so their prefixes stay in the
 *    table under their original names rather than being silently relabelled.
 *  - Prefixes change as banks are created and merged. An unknown prefix returns
 *    null and the UI shows nothing — never a guessed bank.
 */
object BankBin {

    private val prefixes: Map<String, String> = mapOf(
        // The large state and commercial banks — the ones most cards belong to.
        "603799" to "بانک ملی",
        "610433" to "بانک ملت",
        "991975" to "بانک ملت",
        "603769" to "بانک صادرات",
        "627353" to "بانک تجارت",
        "585983" to "بانک تجارت",
        "589210" to "بانک سپه",
        "603770" to "بانک کشاورزی",
        "639217" to "بانک کشاورزی",
        "628023" to "بانک مسکن",
        "627760" to "پست بانک",
        "589463" to "بانک رفاه کارگران",
        "627961" to "بانک صنعت و معدن",
        "627648" to "بانک توسعه صادرات",
        "207177" to "بانک توسعه صادرات",
        "502908" to "بانک توسعه تعاون",

        // Private banks
        "621986" to "بانک سامان",
        "622106" to "بانک پارسیان",
        "639194" to "بانک پارسیان",
        "627884" to "بانک پارسیان",
        "502229" to "بانک پاسارگاد",
        "639347" to "بانک پاسارگاد",
        "627412" to "بانک اقتصاد نوین",
        "627488" to "بانک کارآفرین",
        "502910" to "بانک کارآفرین",
        "639346" to "بانک سینا",
        "639607" to "بانک سرمایه",
        "502806" to "بانک شهر",
        "504706" to "بانک شهر",
        "502938" to "بانک دی",
        "505785" to "بانک ایران زمین",
        "636214" to "بانک آینده",
        "505416" to "بانک گردشگری",
        "636949" to "بانک حکمت ایرانیان",
        "639599" to "بانک قوامین",
        "627381" to "بانک انصار",
        "639370" to "بانک مهر اقتصاد",
        "606373" to "بانک قرض الحسنه مهر ایران",
        "606256" to "بانک ملل",
        "504172" to "بانک قرض الحسنه رسالت",
        "507677" to "بانک نور",
        "628157" to "موسسه اعتباری توسعه",
        "505801" to "بانک سپه"
    )

    /** The bank for a card number, or null when the prefix is not one we know. */
    fun bankFor(cardNumber: String): String? {
        val digits = cardNumber.filter { it.isDigit() }
        if (digits.length < 6) return null
        return prefixes[digits.take(6)]
    }
}
