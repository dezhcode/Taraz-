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
    onSegment: (Int) -> Unit = {}
) {
    var ownSegment by rememberSaveable { mutableIntStateOf(0) }
    val current = segment ?: ownSegment
    val loanLeft = loans.filter { !it.isSettled }.sumOf { (it.totalAmount - it.paidAmount).coerceAtLeast(0) }
    val iOwePeople = balances.values.filter { it < 0 }.sumOf { -it }
    val pendingCheques = cheques.filter { it.status == com.example.data.ChequeStatus.PENDING }
    val chequesOut = pendingCheques.filter { it.issued }.sumOf { it.amount }
    val chequesIn = pendingCheques.filter { !it.issued }.sumOf { it.amount }
    val owedToMe = balances.values.filter { it > 0 }.sum() + chequesIn

    Column(
        modifier.fillMaxSize().background(L.Bg)
            .verticalScroll(rememberScrollState()).statusBarsPadding().padding(bottom = NavClearance)
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("قرض و قسط", style = lt(26.sp, FontWeight.Bold))
            Text("وام‌ها، خریدهای قسطی، آدم‌ها و چک‌ها، یک جا", style = lt(13.sp, color = L.Muted))
        }

        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f).cardSurface(18.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("باید بپردازم", style = lt(12.sp, color = L.Muted))
                FitText(money(loanLeft + iOwePeople + chequesOut + creditUsed), lt(20.sp, FontWeight.Bold, L.RedText))
                Text("تومان", style = lt(11.sp, color = L.Muted))
                Text(
                    listOfNotNull(
                        if (loanLeft > 0) "وام ${millions(loanLeft)}" else null,
                        if (iOwePeople > 0) "اشخاص ${millions(iOwePeople)}" else null,
                        if (chequesOut > 0) "چک ${millions(chequesOut)}" else null,
                        if (creditUsed > 0) "اعتباری ${millions(creditUsed)}" else null
                    ).joinToString("\n").ifEmpty { "بدهی‌ای ندارید" },
                    style = lt(11.sp, color = L.Muted, lineHeight = 17.sp), modifier = Modifier.padding(top = 4.dp)
                )
            }
            Column(Modifier.weight(1f).cardSurface(18.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("طلب دارم", style = lt(12.sp, color = L.Muted))
                FitText(money(owedToMe), lt(20.sp, FontWeight.Bold, L.Green))
                Text("تومان", style = lt(11.sp, color = L.Muted))
                Text(
                    "از ${fa(balances.values.count { it > 0 })} نفر" + if (chequesIn > 0) "\nچک ${millions(chequesIn)}" else "",
                    style = lt(11.sp, color = L.Muted, lineHeight = 17.sp), modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        LSegmented(
            listOf("اقساط", "اشخاص", "چک‌ها"), current, { ownSegment = it; onSegment(it) },
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp)
        )

        when (current) {
            0 -> LoansSection(loans, onAddLoan, onPay, onOpenLoan)
            1 -> PeopleSection(persons, balances, onOpenPerson, onAddPerson, onAddEntry, onSplit)
            else -> ChequesSection(cheques, onAddCheque, onOpenCheque)
        }
    }
}

@Composable
private fun ColumnScope.PeopleSection(
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
        ) { Text("ثبت قرض یا پس‌دادن", style = lt(14.sp, FontWeight.Bold, if (persons.isNotEmpty()) Color.White else Color(0xB3FFFFFF))) }
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
        ) { Text("تقسیم یک خرج جمعی (شام، سفر، خرید)", style = lt(13.sp, FontWeight.SemiBold, L.PrimaryText)) }
    }

    if (persons.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("هنوز کسی را اضافه نکرده‌اید", style = lt(15.sp, FontWeight.Bold))
            Text(
                "برای هر دوست، عضو خانواده یا فامیل یک حساب باز کنید تا قرض‌ها و پس‌دادن‌ها جدا جدا ثبت شود و همیشه بدانید چه کسی به چه کسی بدهکار است.",
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
                Text("طلب من", style = lt(11.sp, color = L.GreenText))
            }
            balance < 0 -> {
                Text(money(-balance), style = lt(15.sp, FontWeight.Bold, L.RedText))
                Text("بدهی من", style = lt(11.sp, color = L.RedText))
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
fun PersonAvatar(person: Person, size: Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(relationColor(person.relation)), contentAlignment = Alignment.Center) {
        Text(person.name.trim().firstOrNull()?.toString() ?: "؟", style = lt((size.value * 0.42f).sp, FontWeight.Bold, Color.White))
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

    LayerScreen(
        title = person.name,
        subtitle = person.relation + if (person.phone.isNotBlank()) " · ${fa(person.phone)}" else "",
        onBack = onBack,
        trailing = {
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onEdit).semantics { contentDescription = "ویرایش" },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Edit, null, tint = L.Ink, modifier = Modifier.size(20.dp)) }
        },
        bottomBar = {
            BigButton("ثبت قرض یا پس‌دادن", onClick = onAddEntry)
            if (balance != 0L) SoftBigButton("تسویهٔ کامل") { settling = true }
        }
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(when { balance > 0 -> L.GreenSoft; balance < 0 -> L.RedSoft; else -> L.Chip })
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                when {
                    balance > 0 -> "${person.name} به شما بدهکار است"
                    balance < 0 -> "شما به ${person.name} بدهکارید"
                    else -> "حسابتان با ${person.name} صاف است"
                },
                style = lt(13.sp, FontWeight.SemiBold, when { balance > 0 -> L.GreenText; balance < 0 -> L.RedText; else -> L.Muted })
            )
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(money(abs(balance)), style = lt(34.sp, FontWeight.Light, letterSpacing = (-1).sp))
                Text("تومان", style = lt(14.sp), modifier = Modifier.padding(bottom = 6.dp))
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
                ) { Text("فرستادن یادآوری برای ${person.name}", style = lt(13.sp, FontWeight.SemiBold, L.PrimaryText)) }
            }
        }

        SectionTitle("ریز حساب")
        LayerCard(radius = 20.dp, padding = 0.dp) {
            if (mine.isEmpty()) {
                Text("هنوز چیزی ثبت نشده.", style = lt(13.sp, color = L.Muted), modifier = Modifier.padding(16.dp))
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
            "+ یعنی طلب شما بیشتر شد (قرض دادید یا پس دادید)، − یعنی کمتر شد (قرض گرفتید یا پس گرفتید).",
            style = lt(11.sp, color = L.Muted, lineHeight = 19.sp), modifier = Modifier.padding(horizontal = 24.dp)
        )

        Box(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).clickable { askDelete = true },
            contentAlignment = Alignment.Center
        ) { Text("حذف ${person.name} و حسابش", style = lt(14.sp, FontWeight.SemiBold, L.RedText)) }
    }

    if (settling) {
        AccountChoiceSheet(
            title = if (balance > 0) "${person.name} همهٔ ${money(balance)} تومان را داد؟" else "همهٔ ${money(-balance)} تومان را به ${person.name} دادید؟",
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
                "${d.kindLabel} · ${money(d.amount)} تومان" +
                    if (d.transactionId != null) "\nتراکنشی که با آن روی حساب ثبت شد هم پاک می‌شود و موجودی حساب برمی‌گردد." else "",
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
                "${person.name} و ریز حسابش پاک می‌شود. تراکنش‌هایی که روی حساب‌های بانکی ثبت شده‌اند می‌مانند، چون آن پول واقعاً جابه‌جا شده است.",
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
        LChip("بیرون از حساب‌ها (دستی)", selected == null) { onSelect(null) }
        cards.forEach { c -> LChip(c.bankName, selected == c.bankName) { onSelect(c.bankName) } }
    }
}

// ───────────────────────── افزودن/ویرایش شخص ─────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonSheet(person: Person?, existingNames: List<String>, onDismiss: () -> Unit, onSave: (Person) -> Unit) {
    var name by remember { mutableStateOf(person?.name.orEmpty()) }
    var relation by remember { mutableStateOf(person?.relation ?: PersonRelation.FRIEND) }
    var phone by remember { mutableStateOf(person?.phone.orEmpty()) }
    val clean = name.trim()
    val taken = existingNames.any { it.equals(clean, ignoreCase = true) && !it.equals(person?.name, ignoreCase = true) }
    LayerSheet(
        title = if (person == null) "شخص جدید" else "ویرایش ${person.name}",
        onDismiss = onDismiss,
        topGap = 140.dp,
        bottomBar = {
            BigButton(if (person == null) "باز کردن حساب" else "ذخیره", enabled = clean.isNotEmpty() && !taken) {
                onSave((person ?: Person(name = clean)).copy(name = clean, relation = relation, phone = phone.trim()))
            }
        }
    ) {
        LField(
            value = name, onChange = { name = it.take(40) }, label = "نام", placeholder = "مثلاً علی یا مامان",
            hint = if (taken) "کسی با این نام دارید." else null
        )
        Text("نسبت", style = lt(13.sp, FontWeight.SemiBold))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PersonRelation.all.forEach { r -> LChip(r, relation == r) { relation = r } }
        }
        LField(
            value = phone, onChange = { phone = it.filter { c -> c.isDigit() || c == '+' }.take(15) }, label = "شماره (اختیاری)",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
        )
        Spacer(Modifier.height(4.dp))
    }
}

// ───────────────────────── ثبت قرض یا پس‌دادن ─────────────────────────

private enum class EntryKind(val label: String, val owedToMe: Boolean, val isRepayment: Boolean, val hint: String) {
    LENT("قرض دادم", true, false, "پول به او دادید؛ طلب شما بیشتر می‌شود."),
    BORROWED("قرض گرفتم", false, false, "از او پول گرفتید؛ بدهی شما بیشتر می‌شود."),
    GOT_BACK("پس گرفتم", false, true, "پولی را که طلب داشتید پس داد."),
    PAID_BACK("پس دادم", true, true, "بدهی‌تان را (همه یا بخشی) پس دادید.")
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
    // پیش‌فرض منطقی: اگر طلب دارم احتمالاً دارد پس می‌دهد، اگر بدهکارم احتمالاً دارم پس می‌دهم
    var kind by remember(personId) { mutableStateOf(when { bal > 0 -> EntryKind.GOT_BACK; bal < 0 -> EntryKind.PAID_BACK; else -> EntryKind.LENT }) }
    var amountText by remember { mutableStateOf("") }
    var account by remember { mutableStateOf<String?>(cards.firstOrNull()?.bankName) }
    var note by remember { mutableStateOf("") }
    val amount = parseAmount(amountText)
    val person = persons.firstOrNull { it.id == personId }

    LayerSheet(
        title = "ثبت قرض یا پس‌دادن",
        onDismiss = onDismiss,
        topGap = 50.dp,
        bottomBar = {
            BigButton("ثبت", enabled = person != null && amount > 0) {
                onSave(
                    person!!.id, amount, kind.owedToMe, kind.isRepayment, account, note,
                    dueDays?.takeIf { !kind.isRepayment }?.let { startOfDay(System.currentTimeMillis()) + it * DAY_MS }
                )
                onDismiss()
            }
        }
    ) {
        Text("برای چه کسی؟", style = lt(13.sp, FontWeight.SemiBold))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            persons.sortedBy { it.name }.forEach { p -> LChip(p.name, personId == p.id) { personId = p.id } }
            LChip("+ شخص جدید", false, onClick = onAddPerson)
        }
        if (person != null && bal != 0L) {
            Text(
                if (bal > 0) "الان ${money(bal)} تومان از ${person.name} طلب دارید." else "الان ${money(-bal)} تومان به ${person.name} بدهکارید.",
                style = lt(12.sp, color = if (bal > 0) L.GreenText else L.RedText)
            )
        }

        Text("چه شد؟", style = lt(13.sp, FontWeight.SemiBold))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EntryKind.entries.forEach { k -> LChip(k.label, kind == k) { kind = k } }
        }
        Text(kind.hint, style = lt(12.sp, color = L.Muted))

        LField(
            value = amountText, onChange = { amountText = groupedInput(it) }, label = "مبلغ (تومان)", placeholder = "۰",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Text(if (kind.owedToMe) "از کدام حساب رفت؟" else "به کدام حساب آمد؟", style = lt(13.sp, FontWeight.SemiBold))
        AccountChips(cards, account) { account = it }
        Text(
            "اگر حساب انتخاب کنید موجودی آن هم تغییر می‌کند، ولی در هزینه و درآمد ماه حساب نمی‌شود.",
            style = lt(11.sp, color = L.Muted, lineHeight = 19.sp)
        )
        if (!kind.isRepayment) {
            Text(if (kind.owedToMe) "کی پس می‌دهد؟" else "کی باید پس بدهید؟", style = lt(13.sp, FontWeight.SemiBold))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to "بدون موعد", 7 to "یک هفته", 14 to "دو هفته", 30 to "یک ماه", 90 to "سه ماه").forEach { (d, label) ->
                    LChip(label, dueDays == d) { dueDays = d }
                }
            }
            if (dueDays != null) Text("روز موعد یادآوری می‌آید.", style = lt(11.sp, color = L.Muted))
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
        title = "تقسیم خرج جمعی",
        onDismiss = onDismiss,
        topGap = 40.dp,
        bottomBar = {
            BigButton("ثبت و تقسیم", enabled = ok) {
                onSave(title, total, account!!, category, includeMe, picked.toList())
                onDismiss()
            }
        }
    ) {
        LField(value = title, onChange = { title = it.take(40) }, label = "برای چه؟", placeholder = "مثلاً شام جمعه")
        LField(
            value = totalText, onChange = { totalText = groupedInput(it) }, label = "کل مبلغ (تومان)", placeholder = "۰",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Text("چه کسانی بودند؟", style = lt(13.sp, FontWeight.SemiBold))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LChip("خودم", includeMe) { includeMe = !includeMe }
            persons.sortedBy { it.name }.forEach { p ->
                val on = p.id in picked
                LChip(p.name, on) { picked = if (on) picked - p.id else picked + p.id }
            }
        }
        if (total > 0 && n > 0) {
            Text(
                "سهم هر نفر ${money(share)} تومان. " +
                    (if (includeMe) "سهم خودتان هزینه ثبت می‌شود و " else "") +
                    "سهم ${fa(picked.size)} نفر دیگر به طلب شما از آن‌ها اضافه می‌شود.",
                style = lt(12.sp, color = L.PrimaryText, lineHeight = 21.sp)
            )
        }
        Text("از کدام حساب پرداخت کردید؟", style = lt(13.sp, FontWeight.SemiBold))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            cards.forEach { c -> LChip(c.bankName, account == c.bankName) { account = c.bankName } }
        }
        if (includeMe) {
            Text("دستهٔ سهم خودتان", style = lt(13.sp, FontWeight.SemiBold))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                expenseCats.forEach { c -> LChip(c.name, category == c.name) { category = c.name } }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
