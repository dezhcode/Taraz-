package com.example.ui.layer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BankCard
import com.example.data.Debt
import com.example.data.Loan
import com.example.data.Person
import com.example.data.PersonRelation
import com.example.utils.JalaliDate
import kotlin.math.abs

// ───────────────────────── تب «قرض و قسط» ─────────────────────────

/**
 * همهٔ تعهدها یک جا: بالای صفحه جمع «باید بپردازم / طلب دارم»، و زیرش دو بخش
 * وام‌های بانکی (اقساط) و اشخاص (حساب جدا با هر دوست، عضو خانواده یا فامیل).
 */
@Composable
fun LayerObligationsScreen(
    loans: List<Loan>,
    persons: List<Person>,
    balances: Map<Int, Long>,
    onAddLoan: () -> Unit,
    onPay: (Loan) -> Unit,
    onOpenLoan: (Loan) -> Unit,
    onOpenPerson: (Person) -> Unit,
    onAddPerson: () -> Unit,
    onAddEntry: () -> Unit,
    modifier: Modifier = Modifier,
    onSplit: () -> Unit = {},
    cheques: List<com.example.data.Cheque> = emptyList(),
    onAddCheque: () -> Unit = {},
    onOpenCheque: (com.example.data.Cheque) -> Unit = {},
    creditUsed: Long = 0,
    segment: Int? = null,
    onSegment: (Int) -> Unit = {},
    loanPayments: List<com.example.data.LoanPayment> = emptyList()
) {
    var ownSegment by rememberSaveable { mutableIntStateOf(0) }
    val current = segment ?: ownSegment
    val loanLeft = loans.filter { !it.isSettled }.sumOf { (it.totalAmount - it.paidAmount).coerceAtLeast(0) }
    val iOwePeople = balances.values.filter { it < 0 }.sumOf { -it }
    val pendingCheques = cheques.filter { it.status == com.example.data.ChequeStatus.PENDING }
    val chequesOut = pendingCheques.filter { it.issued }.sumOf { it.amount }
    val chequesIn = pendingCheques.filter { !it.issued }.sumOf { it.amount }
    val owedToMe = balances.values.filter { it > 0 }.sum() + chequesIn
    ScreenTour("obligations", Tours.obligations)

    Column(
        modifier.fillMaxSize().background(L.Bg)
            .verticalScroll(rememberScrollState()).statusBarsPadding().padding(bottom = NavClearance)
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("اقساط و تعهدات", style = lt(26.sp, FontWeight.Bold))
            Text("وام‌ها، خریدهای اقساطی، اشخاص و چک‌ها", style = lt(13.sp, color = L.Muted))
        }

        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp).fillMaxWidth().tourTarget("obl.summary"), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f).cardSurface(18.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("بدهکار", style = lt(12.sp, color = L.Muted))
                FitText(money(loanLeft + iOwePeople + chequesOut + creditUsed), lt(20.sp, FontWeight.Bold, L.RedText))
                Text("${com.example.data.Money.label}", style = lt(11.sp, color = L.Muted))
                Text(
                    listOfNotNull(
                        if (loanLeft > 0) "وام ${millions(loanLeft)}" else null,
                        if (iOwePeople > 0) "اشخاص ${millions(iOwePeople)}" else null,
                        if (chequesOut > 0) "چک پرداختی ${millions(chequesOut)}" else null,
                        if (creditUsed > 0) "اعتباری ${millions(creditUsed)}" else null
                    ).joinToString("\n").ifEmpty { "بدهکاری ندارید" },
                    style = lt(11.sp, color = L.Muted, lineHeight = 17.sp), modifier = Modifier.padding(top = 4.dp)
                )
            }
            Column(Modifier.weight(1f).cardSurface(18.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("بستانکار", style = lt(12.sp, color = L.Muted))
                FitText(money(owedToMe), lt(20.sp, FontWeight.Bold, L.Green))
                Text("${com.example.data.Money.label}", style = lt(11.sp, color = L.Muted))
                Text(
                    "از ${fa(balances.values.count { it > 0 })} نفر" + if (chequesIn > 0) "\nچک دریافتی ${millions(chequesIn)}" else "",
                    style = lt(11.sp, color = L.Muted, lineHeight = 17.sp), modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        LSegmented(
            listOf("اقساط", "اشخاص", "چک‌ها"), current, { ownSegment = it; onSegment(it) },
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp).tourTarget("obl.tabs")
        )

        when (current) {
            0 -> LoansSection(loans, onAddLoan, onPay, onOpenLoan, loanPayments)
            1 -> PeopleSection(persons, balances, onOpenPerson, onAddPerson, onAddEntry, onSplit)
            else -> ChequesSection(cheques, onAddCheque, onOpenCheque)
        }
    }
}

@Composable
internal fun ColumnScope.PeopleSection(
    persons: List<Person>,
    balances: Map<Int, Long>,
    onOpenPerson: (Person) -> Unit,
    onAddPerson: () -> Unit,
    onAddEntry: () -> Unit,
    onSplit: () -> Unit
) {
    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.weight(1f).height(44.dp).clip(ButtonShape).background(L.Primary).clickable(enabled = persons.isNotEmpty(), onClick = onAddEntry),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
        ) { Text("ثبت بستانکار / بدهکار", style = lt(14.sp, FontWeight.Bold, if (persons.isNotEmpty()) Color.White else Color(0xB3FFFFFF))) }
        Row(
            Modifier.height(44.dp).clip(ButtonShape).background(L.PrimarySoft).clickable(onClick = onAddPerson).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Add, null, tint = L.PrimaryText, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("شخص جدید", style = lt(14.sp, FontWeight.Bold, L.PrimaryText))
        }
    }

    if (persons.isNotEmpty()) {
        Row(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp).fillMaxWidth().height(42.dp).clip(ButtonShape)
                .background(L.Surface).clickable(onClick = onSplit),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
        ) { Text("تقسیم هزینهٔ گروهی (شام، سفر، خرید)", style = lt(13.sp, FontWeight.SemiBold, L.PrimaryText)) }
    }

    if (persons.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("هنوز شخصی اضافه نشده است", style = lt(15.sp, FontWeight.Bold))
            Text(
                "برای هر یک از دوستان، اعضای خانواده یا بستگان یک حساب ایجاد کنید تا بستانکاری و بدهکاری با هر شخص جداگانه ثبت شود و مانده‌حساب همواره مشخص باشد.",
                style = lt(13.sp, color = L.Muted, lineHeight = 23.sp)
            )
        }
        return
    }

    // آدم‌هایی که حسابشان باز است اول، بزرگ‌ترین مبلغ اول
    val sorted = persons.sortedWith(compareByDescending<Person> { abs(balances[it.id] ?: 0L) }.thenBy { it.name })
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        sorted.forEach { p ->
            val bal = balances[p.id] ?: 0L
            Row(
                Modifier.fillMaxWidth().cardSurface(18.dp).clickable { onOpenPerson(p) }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PersonAvatar(p, 42.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(p.name, style = lt(15.sp, FontWeight.SemiBold), maxLines = 1)
                    Text(p.relation, style = lt(12.sp, color = L.Muted))
                }
                BalanceLabel(bal)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = L.Chevron, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun BalanceLabel(balance: Long) {
    Column(horizontalAlignment = Alignment.End) {
        when {
            balance > 0 -> {
                Text(money(balance), style = lt(15.sp, FontWeight.Bold, L.Green))
                Text("بستانکار", style = lt(11.sp, color = L.GreenText))
            }
            balance < 0 -> {
                Text(money(-balance), style = lt(15.sp, FontWeight.Bold, L.RedText))
                Text("بدهکار", style = lt(11.sp, color = L.RedText))
            }
            else -> Text("تسویه", style = lt(13.sp, FontWeight.SemiBold, L.Muted))
        }
    }
}

private fun relationColor(relation: String): Color = when (relation) {
    PersonRelation.FAMILY -> Color(0xFFB0532F)
    PersonRelation.RELATIVE -> Color(0xFF7A5AA8)
    PersonRelation.FRIEND -> Color(0xFF1D6FA3)
    PersonRelation.COLLEAGUE -> Color(0xFF0E9490)
    else -> Color(0xFF5C6B73)
}

@Composable
fun PersonAvatar(person: Person, size: Dp) = PersonAvatar(person.name, person.relation, person.photo, size)

/** تصویر پروفایل شخص؛ اگر تصویری نگذاشته باشد، حرف اول نام روی رنگ نسبت */
@Composable
fun PersonAvatar(name: String, relation: String, photo: String, size: Dp) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Box(Modifier.size(size).clip(CircleShape).background(relationColor(relation)), contentAlignment = Alignment.Center) {
        if (photo.isNotBlank()) {
            coil.compose.AsyncImage(
                model = com.example.data.Attachments.file(context, photo), contentDescription = "تصویر $name",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(name.trim().firstOrNull()?.toString() ?: "؟", style = lt((size.value * 0.42f).sp, FontWeight.Bold, Color.White))
        }
    }
}

// ───────────────────────── صفحهٔ یک شخص ─────────────────────────

@Composable
fun LayerPersonDetailScreen(
    person: Person,
    balance: Long,
    entries: List<Debt>,
    cards: List<BankCard>,
    onBack: () -> Unit,
    onAddEntry: () -> Unit,
    onSettle: (account: String?) -> Unit,
    onDeleteEntry: (Debt) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShareReminder: (() -> Unit)? = null
) {
    var settling by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    var entryToDelete by remember { mutableStateOf<Debt?>(null) }
    val mine = entries.filter { it.personId == person.id }.sortedByDescending { it.createdAt }

    val context = androidx.compose.ui.platform.LocalContext.current
    LayerScreen(
        title = "پروفایل",
        onBack = onBack,
        trailing = {
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onEdit).semantics { contentDescription = "ویرایش" },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Edit, null, tint = L.Ink, modifier = Modifier.size(20.dp)) }
        },
        bottomBar = {
            BigButton("ثبت بستانکار / بدهکار", onClick = onAddEntry)
            if (balance != 0L) SoftBigButton("تسویهٔ کامل") { settling = true }
        }
    ) {
        // سربرگ پروفایل: تصویر، نام، نسبت و راه‌های تماس
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.clip(CircleShape).clickable(onClick = onEdit)) { PersonAvatar(person, 96.dp) }
            Text(person.name, style = lt(22.sp, FontWeight.Bold))
            Box(
                Modifier.clip(PillShape).background(relationColor(person.relation).copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 3.dp)
            ) { Text(person.relation, style = lt(12.sp, FontWeight.SemiBold, relationColor(person.relation))) }
            if (person.phone.isNotBlank()) {
                Text(
                    fa(person.phone), style = lt(14.sp, color = L.Muted).copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton("تماس", height = 36.dp) {
                        runCatching {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:${person.phone}")))
                        }
                    }
                    SoftButton("پیامک", height = 36.dp) {
                        runCatching {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:${person.phone}")))
                        }
                    }
                }
            } else {
                Text("شمارهٔ تماس ثبت نشده است", style = lt(12.sp, color = L.Muted))
            }
        }
        Spacer(Modifier.height(10.dp))

        Column(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(when { balance > 0 -> L.GreenSoft; balance < 0 -> L.RedSoft; else -> L.Chip })
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                when {
                    balance > 0 -> "${person.name} به شما بدهکار است"
                    balance < 0 -> "شما به ${person.name} بدهکار هستید"
                    else -> "حساب شما با ${person.name} تسویه است"
                },
                style = lt(13.sp, FontWeight.SemiBold, when { balance > 0 -> L.GreenText; balance < 0 -> L.RedText; else -> L.Muted })
            )
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(money(abs(balance)), style = lt(34.sp, FontWeight.Light, letterSpacing = (-1).sp))
                Text("${com.example.data.Money.label}", style = lt(14.sp), modifier = Modifier.padding(bottom = 6.dp))
            }
            val nextDue = mine.filter { !it.settled && !it.isRepayment && it.dueDate != null && it.owedToMe == (balance > 0) }
                .minByOrNull { it.dueDate!! }?.dueDate
            if (balance != 0L && nextDue != null) {
                val days = JalaliDate.daysUntil(nextDue)
                Text(
                    "موعد ${JalaliDate.fromTimestamp(nextDue).formatShort()} · " + when {
                        days < 0 -> "${fa(-days)} روز گذشته"
                        days == 0 -> "امروز"
                        else -> "${fa(days)} روز دیگر"
                    },
                    style = lt(12.sp, FontWeight.SemiBold, if (days < 0) L.RedText else L.Muted)
                )
            }
            if (balance > 0 && onShareReminder != null) {
                Box(
                    Modifier.padding(top = 6.dp).height(36.dp).clip(ButtonShape).background(L.Surface).clickable(onClick = onShareReminder).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) { Text("ارسال یادآوری به ${person.name}", style = lt(13.sp, FontWeight.SemiBold, L.PrimaryText)) }
            }
        }

        SectionTitle("صورت‌حساب")
        LayerCard(radius = 20.dp, padding = 0.dp) {
            if (mine.isEmpty()) {
                Text("هنوز موردی ثبت نشده است.", style = lt(13.sp, color = L.Muted), modifier = Modifier.padding(16.dp))
            } else {
                mine.forEachIndexed { i, d ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                d.kindLabel + if (d.settled) " · تسویه‌شده" else "",
                                style = lt(14.sp, FontWeight.SemiBold, if (d.settled) L.Muted else L.Ink)
                            )
                            Text(
                                JalaliDate.fromTimestamp(d.createdAt).formatShort() +
                                    (d.dueDate?.let { " · موعد ${JalaliDate.fromTimestamp(it).formatShort()}" } ?: "") +
                                    (if (d.note.isNotBlank()) " · ${d.note}" else "") +
                                    (if (d.transactionId != null) " · از حساب" else ""),
                                style = lt(12.sp, color = L.Muted), maxLines = 1
                            )
                        }
                        Text(
                            (if (d.owedToMe) "+" else "−") + money(d.amount),
                            style = lt(14.sp, FontWeight.SemiBold, if (d.settled) L.Muted else if (d.owedToMe) L.Green else L.RedText)
                        )
                        Box(
                            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).clickable { entryToDelete = d }.semantics { contentDescription = "حذف این ردیف" },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Delete, null, tint = L.Faint, modifier = Modifier.size(18.dp)) }
                    }
                    if (i < mine.size - 1) Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(L.Line))
                }
            }
        }
        Text(
            "بستانکار (+): مبلغ پرداختی به این شخص؛ بدهکار (−): مبلغ دریافتی از این شخص.",
            style = lt(11.sp, color = L.Muted, lineHeight = 19.sp), modifier = Modifier.padding(horizontal = 24.dp)
        )

        Box(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).clickable { askDelete = true },
            contentAlignment = Alignment.Center
        ) { Text("حذف ${person.name} و حساب مربوط", style = lt(14.sp, FontWeight.SemiBold, L.RedText)) }
    }

    if (settling) {
        AccountChoiceSheet(
            title = if (balance > 0) "آیا کل مبلغ ${money(balance)} ${com.example.data.Money.label} از ${person.name} دریافت شد؟" else "آیا کل مبلغ ${money(-balance)} ${com.example.data.Money.label} به ${person.name} پرداخت شد؟",
            question = if (balance > 0) "به کدام حساب واریز شد؟" else "از کدام حساب پرداخت شد؟",
            cards = cards,
            confirm = "تسویه شد",
            onDismiss = { settling = false },
            onConfirm = { onSettle(it); settling = false }
        )
    }
    entryToDelete?.let { d ->
        LayerSheet(
            title = "این ردیف حذف شود؟", onDismiss = { entryToDelete = null }, topGap = 320.dp,
            bottomBar = { BigButton("بله، حذف شود", background = L.RedText) { onDeleteEntry(d); entryToDelete = null } }
        ) {
            Text(
                "${d.kindLabel} · ${money(d.amount)} ${com.example.data.Money.label}" +
                    if (d.transactionId != null) "\nتراکنش مرتبط نیز حذف و موجودی حساب اصلاح می‌شود." else "",
                style = lt(13.sp, color = L.Muted, lineHeight = 23.sp)
            )
        }
    }
    if (askDelete) {
        LayerSheet(
            title = "حذف ${person.name}", onDismiss = { askDelete = false }, topGap = 300.dp,
            bottomBar = { BigButton("بله، حذف شود", background = L.RedText) { onDelete(); askDelete = false } }
        ) {
            Text(
                "${person.name} و صورت‌حساب او حذف می‌شود. تراکنش‌هایی که روی حساب‌های بانکی ثبت شده‌اند می‌مانند، زیرا آن مبالغ واقعاً جابه‌جا شده‌اند.",
                style = lt(13.sp, color = L.Muted, lineHeight = 23.sp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountChoiceSheet(
    title: String,
    question: String,
    cards: List<BankCard>,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var account by remember { mutableStateOf<String?>(null) }
    LayerSheet(title = title, onDismiss = onDismiss, topGap = 220.dp, bottomBar = { BigButton(confirm) { onConfirm(account) } }) {
        Text(question, style = lt(14.sp, lineHeight = 24.sp))
        AccountChips(cards, account) { account = it }
        Spacer(Modifier.height(4.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountChips(cards: List<BankCard>, selected: String?, onSelect: (String?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LChip("خارج از حساب‌ها (دستی)", selected == null) { onSelect(null) }
        cards.forEach { c -> LChip(c.bankName, selected == c.bankName) { onSelect(c.bankName) } }
        AddAccountChip()
    }
    WatchNew(cards, { it.id }) { onSelect(it.bankName) }
}

// ───────────────────────── افزودن/ویرایش شخص ─────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonSheet(person: Person?, existingNames: List<String>, onDismiss: () -> Unit, onSave: (Person) -> Unit) {
    var name by remember { mutableStateOf(person?.name.orEmpty()) }
    var relation by remember { mutableStateOf(person?.relation ?: PersonRelation.FRIEND) }
    var phone by remember { mutableStateOf(person?.phone.orEmpty()) }
    var photo by remember { mutableStateOf(person?.photo.orEmpty()) }
    val context = androidx.compose.ui.platform.LocalContext.current
    // تصویرهایی که در همین برگه انتخاب و بعد کنار گذاشته شدند، روی دستگاه نمی‌مانند
    var picked by remember { mutableStateOf(listOf<String>()) }
    fun usePhoto(uri: android.net.Uri?) {
        if (uri == null) return
        com.example.data.Attachments.saveAvatar(context, uri)?.let { photo = it; picked = picked + it }
    }
    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { usePhoto(it) }
    var cameraFile by remember { mutableStateOf<java.io.File?>(null) }
    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { ok ->
        val f = cameraFile
        if (ok && f != null) { usePhoto(android.net.Uri.fromFile(f)); f.delete() }
        cameraFile = null
    }
    val dismiss = {
        picked.forEach { com.example.data.Attachments.delete(context, it) }
        onDismiss()
    }
    val clean = name.trim()
    val taken = existingNames.any { it.equals(clean, ignoreCase = true) && !it.equals(person?.name, ignoreCase = true) }
    LayerSheet(
        title = if (person == null) "شخص جدید" else "ویرایش ${person.name}",
        onDismiss = dismiss,
        topGap = 90.dp,
        bottomBar = {
            BigButton(if (person == null) "ایجاد حساب" else "ذخیره", enabled = clean.isNotEmpty() && !taken) {
                // تصویر قبلی که عوض شد و تصویرهای کنارگذاشته پاک می‌شوند
                picked.filter { it != photo }.forEach { com.example.data.Attachments.delete(context, it) }
                person?.photo?.takeIf { it.isNotBlank() && it != photo }?.let { com.example.data.Attachments.delete(context, it) }
                onSave((person ?: Person(name = clean)).copy(name = clean, relation = relation, phone = phone.trim(), photo = photo))
            }
        }
    ) {
        // تصویر پروفایل
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PersonAvatar(clean.ifBlank { "؟" }, relation, photo, 76.dp)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("تصویر پروفایل", style = lt(13.sp, FontWeight.SemiBold))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LChip("گالری", false) { galleryLauncher.launch("image/*") }
                    LChip("دوربین", false) {
                        val (f, uri) = com.example.data.Attachments.cameraTarget(context)
                        cameraFile = f
                        cameraLauncher.launch(uri)
                    }
                    if (photo.isNotBlank()) LChip("حذف تصویر", false) { photo = "" }
                }
            }
        }
        LField(
            value = name, onChange = { name = it.take(40) }, label = "نام", placeholder = "مثلاً علی رضایی",
            hint = if (taken) "شخصی با این نام وجود دارد." else null
        )
        Text("نسبت", style = lt(13.sp, FontWeight.SemiBold))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PersonRelation.all.forEach { r -> LChip(r, relation == r) { relation = r } }
        }
        LField(
            value = phone, onChange = { phone = it.filter { c -> c.isDigit() || c == '+' }.take(15) }, label = "شمارهٔ تماس (اختیاری)",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
        )
        Spacer(Modifier.height(4.dp))
    }
}

// ───────────────────────── ثبت قرض یا پس‌دادن ─────────────────────────

/**
 * فقط دو حالت رسمی: «بستانکار» (پولی به شخص دادید) و «بدهکار» (پولی از شخص گرفتید).
 * اینکه قرض تازه است یا پس دادن، از مانده‌حساب فعلی با همان شخص معلوم می‌شود.
 */
private enum class EntryKind(val label: String, val owedToMe: Boolean) {
    CREDITOR("بستانکار", true),
    DEBTOR("بدهکار", false)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonEntrySheet(
    persons: List<Person>,
    preselectedId: Int?,
    balances: Map<Int, Long>,
    cards: List<BankCard>,
    onDismiss: () -> Unit,
    onAddPerson: () -> Unit,
    onSave: (personId: Int, amount: Long, owedToMe: Boolean, isRepayment: Boolean, account: String?, note: String, dueDate: Long?) -> Unit
) {
    var dueDays by remember { mutableStateOf<Int?>(null) }
    var personId by remember { mutableStateOf(preselectedId ?: persons.firstOrNull()?.id) }
    // شخصی که همین الان از داخل این شیت ساخته شد، انتخاب شود
    androidx.compose.runtime.LaunchedEffect(preselectedId) { if (preselectedId != null) personId = preselectedId }
    val bal = personId?.let { balances[it] } ?: 0L
    // پیش‌فرض منطقی: اگر طلب دارم احتمالاً دارد پس می‌دهد (بدهکار)، وگرنه بستانکار
    var kind by remember(personId) { mutableStateOf(if (bal > 0) EntryKind.DEBTOR else EntryKind.CREDITOR) }
    // پس دادن یعنی حرکت خلاف مانده‌حساب فعلی (فقط برای برچسب و یادآور موعد)
    val isRepayment = if (kind.owedToMe) bal < 0 else bal > 0
    var amountText by remember { mutableStateOf("") }
    var account by remember { mutableStateOf<String?>(cards.firstOrNull()?.bankName) }
    var note by remember { mutableStateOf("") }
    val amount = parseAmount(amountText)
    val person = persons.firstOrNull { it.id == personId }

    LayerSheet(
        title = "ثبت بستانکار / بدهکار",
        onDismiss = onDismiss,
        topGap = 50.dp,
        bottomBar = {
            BigButton("ثبت", enabled = person != null && amount > 0) {
                onSave(
                    person!!.id, amount, kind.owedToMe, isRepayment, account, note,
                    dueDays?.takeIf { !isRepayment }?.let { startOfDay(System.currentTimeMillis()) + it * DAY_MS }
                )
                onDismiss()
            }
        }
    ) {
        Text("طرف حساب", style = lt(13.sp, FontWeight.SemiBold))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            persons.sortedBy { it.name }.forEach { p -> LChip(p.name, personId == p.id) { personId = p.id } }
            LChip("+ شخص جدید", false, onClick = onAddPerson)
        }
        if (person != null && bal != 0L) {
            Text(
                if (bal > 0) "مانده: ${money(bal)} ${com.example.data.Money.label} بستانکار هستید (${person.name} به شما بدهکار است)."
                else "مانده: ${money(-bal)} ${com.example.data.Money.label} به ${person.name} بدهکار هستید.",
                style = lt(12.sp, color = if (bal > 0) L.GreenText else L.RedText)
            )
        }

        Text("نوع", style = lt(13.sp, FontWeight.SemiBold))
        LSegmented(EntryKind.entries.map { it.label }, kind.ordinal, { kind = EntryKind.entries[it] })
        val who = person?.name ?: "این شخص"
        Text(
            if (kind.owedToMe) "مبلغی به $who پرداخت کرده‌اید؛ به همین میزان بستانکار می‌شوید (یا از بدهی شما کسر می‌شود)."
            else "مبلغی از $who دریافت کرده‌اید؛ به همین میزان بدهکار می‌شوید (یا از بستانکاری شما کسر می‌شود).",
            style = lt(12.sp, color = L.Muted, lineHeight = 20.sp)
        )

        LField(
            value = amountText, onChange = { amountText = groupedInput(it) }, label = "مبلغ (${com.example.data.Money.label})", placeholder = "۰",
            keyboardOptions = moneyKeyboard
        )
        Text(if (kind.owedToMe) "حساب پرداخت‌کننده" else "حساب دریافت‌کننده", style = lt(13.sp, FontWeight.SemiBold))
        AccountChips(cards, account) { account = it }
        Text(
            "با انتخاب حساب، موجودی آن نیز به‌روز می‌شود؛ این مبلغ در هزینه و درآمد ماه منظور نمی‌شود.",
            style = lt(11.sp, color = L.Muted, lineHeight = 19.sp)
        )
        if (!isRepayment) {
            Text(if (kind.owedToMe) "موعد دریافت" else "موعد پرداخت", style = lt(13.sp, FontWeight.SemiBold))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to "بدون موعد", 7 to "یک هفته", 14 to "دو هفته", 30 to "یک ماه", 90 to "سه ماه").forEach { (d, label) ->
                    LChip(label, dueDays == d) { dueDays = d }
                }
            }
            if (dueDays != null) Text("در روز موعد یادآوری ارسال می‌شود.", style = lt(11.sp, color = L.Muted))
        }
        LField(value = note, onChange = { note = it.take(60) }, label = "یادداشت (اختیاری)", placeholder = "مثلاً برای اجاره")
        Spacer(Modifier.height(4.dp))
    }
}

// ───────────────────────── تقسیم خرج جمعی ─────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SplitExpenseSheet(
    persons: List<Person>,
    cards: List<BankCard>,
    categories: List<com.example.data.Category>,
    onDismiss: () -> Unit,
    onSave: (title: String, total: Long, account: String, category: String, includeMe: Boolean, personIds: List<Int>) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var totalText by remember { mutableStateOf("") }
    var account by remember { mutableStateOf(cards.firstOrNull()?.bankName) }
    val expenseCats = categories.filter { !it.isIncome }.sortedBy { it.sortOrder }
    var category by remember { mutableStateOf(expenseCats.firstOrNull()?.name ?: "سایر") }
    var includeMe by remember { mutableStateOf(true) }
    var picked by remember { mutableStateOf(setOf<Int>()) }
    val total = parseAmount(totalText)
    val n = picked.size + if (includeMe) 1 else 0
    val share = if (n > 0) total / n else 0L
    val ok = total > 0 && picked.isNotEmpty() && account != null

    LayerSheet(
        title = "تقسیم هزینهٔ گروهی",
        onDismiss = onDismiss,
        topGap = 40.dp,
        bottomBar = {
            BigButton("ثبت و تقسیم", enabled = ok) {
                onSave(title, total, account!!, category, includeMe, picked.toList())
                onDismiss()
            }
        }
    ) {
        LField(value = title, onChange = { title = it.take(40) }, label = "شرح", placeholder = "مثلاً شام جمعه")
        LField(
            value = totalText, onChange = { totalText = groupedInput(it) }, label = "کل مبلغ (${com.example.data.Money.label})", placeholder = "۰",
            keyboardOptions = moneyKeyboard
        )
        Text("اشخاص سهیم", style = lt(13.sp, FontWeight.SemiBold))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LChip("خودم", includeMe) { includeMe = !includeMe }
            persons.sortedBy { it.name }.forEach { p ->
                val on = p.id in picked
                LChip(p.name, on) { picked = if (on) picked - p.id else picked + p.id }
            }
            AddPersonChip()
        }
        WatchNew(persons, { it.id }) { picked = picked + it.id }
        WatchNew(cards, { it.id }) { account = it.bankName }
        WatchNew(categories, { it.id }) { c -> if (!c.isIncome) category = c.name }
        if (total > 0 && n > 0) {
            Text(
                "سهم هر نفر ${money(share)} ${com.example.data.Money.label}. " +
                    (if (includeMe) "سهم شما به‌عنوان هزینه ثبت می‌شود و " else "") +
                    "سهم ${fa(picked.size)} نفر دیگر به بستانکاری شما افزوده می‌شود.",
                style = lt(12.sp, color = L.PrimaryText, lineHeight = 21.sp)
            )
        }
        Text("حساب پرداخت‌کننده", style = lt(13.sp, FontWeight.SemiBold))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            cards.forEach { c -> LChip(c.bankName, account == c.bankName) { account = c.bankName } }
            AddAccountChip()
        }
        if (includeMe) {
            Text("دستهٔ سهم شما", style = lt(13.sp, FontWeight.SemiBold))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                expenseCats.forEach { c -> LChip(c.name, category == c.name) { category = c.name } }
                AddCategoryChip(false)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
