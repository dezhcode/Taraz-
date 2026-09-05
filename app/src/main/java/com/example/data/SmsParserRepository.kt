package com.example.data

import android.util.Log
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

class SmsParserRepository(
    private val bankSenderDao: BankSenderDao,
    private val bankSmsParserDao: BankSmsParserDao,
    private val pendingTransactionDao: PendingTransactionDao,
    private val unknownSmsDao: UnknownSmsDao,
    private val aiRepository: AiRepository
) {
    val allSendersFlow: Flow<List<BankSender>> = bankSenderDao.getAllSendersFlow()
    val allPendingTransactionsFlow: Flow<List<PendingTransaction>> = pendingTransactionDao.getPendingTransactionsFlow()
    val allUnknownSmsFlow: Flow<List<UnknownSms>> = unknownSmsDao.getUnknownSmsFlow()

    suspend fun getAllSenders(): List<BankSender> = bankSenderDao.getAllSenders()
    suspend fun insertSender(sender: BankSender) = bankSenderDao.insertSender(sender)
    suspend fun updateSender(sender: BankSender) = bankSenderDao.updateSender(sender)
    suspend fun deleteSender(id: Int) = bankSenderDao.deleteSender(id)
    suspend fun deleteSenderByNumber(senderNumber: String) {
        bankSenderDao.deleteSenderByNumber(senderNumber)
        bankSmsParserDao.deleteAllParsersForSender(senderNumber)
    }

    suspend fun getLatestParser(senderNumber: String): BankSmsParser? {
        return bankSmsParserDao.getLatestParserForSender(senderNumber)
    }

    suspend fun deletePendingTransaction(id: Int) = pendingTransactionDao.deletePendingTransaction(id)
    suspend fun ignoreUnknownSms(id: Int) = unknownSmsDao.ignoreUnknownSms(id)
    suspend fun deleteUnknownSms(id: Int) = unknownSmsDao.deleteUnknownSms(id)

    // Stage 3 & 5: AI Learning for initial format
    suspend fun learnSmsFormat(
        sender: BankSender,
        smsList: List<String>
    ): Result<BankSmsParser> {
        if (smsList.isEmpty()) {
            return Result.failure(Exception("هیچ پیامکی برای تحلیل یافت نشد"))
        }

        // Format prompt for copilot (RTL/Persian context)
        val prompt = buildString {
            append("شما یک موتور هوشمند استخراج الگوهای پیامکی بانک برای برنامه تراز هستید.\n")
            append("وظیفه شما تحلیل پیامک‌های بانکی و تولید الگوهای استخراج فیلدها با عبارات منظم (Regex) است.\n")
            append("خروجی شما باید صرفا یک آبجکت معتبر JSON بدون هیچگونه توضیح، کدبلاگ یا علامت اضافی باشد.\n\n")
            append("اطلاعات بانک و پیامک‌ها:\n")
            append("نام بانک: ${sender.bankName}\n")
            append("شماره فرستنده: ${sender.senderNumber}\n\n")
            append("پیامک‌های دریافتی جهت تحلیل:\n")
            smsList.forEachIndexed { index, sms ->
                append("${index + 1}. $sms\n")
            }
            append("\nفرمت دقیق خروجی JSON که باید برگردانید:\n")
            append("{\n")
            append("  \"bank\": \"${sender.bankName}\",\n")
            append("  \"templates\": [\n")
            append("    {\n")
            append("      \"type\": \"purchase\", // یا withdrawal, deposit, transfer, salary, atm\n")
            append("      \"regex\": \"(?<action>.*?) از (?<card>\\\\d+) مبلغ: (?<amount>[\\\\d,]+) ریال مانده: (?<balance>[\\\\d,]+) ریال (?<date>\\\\d{4}/\\\\d{2}/\\\\d{2}) (?<time>\\\\d{2}:\\\\d{2}:\\\\d{2})\", // رجکس جاوا معتبر\n")
            append("      \"fields\": {\n")
            append("        \"amount\": \"amount\",\n")
            append("        \"balance\": \"balance\",\n")
            append("        \"date\": \"date\",\n")
            append("        \"time\": \"time\",\n")
            append("        \"card\": \"card\"\n")
            append("      }\n")
            append("    }\n")
            append("  ]\n")
            append("}\n\n")
            append("نکته بسیار مهم: رجکس‌ها باید با کاراکترهای اسکیپ شده مناسب جاوا باشند تا بتوان در کاتلین با کلاس Pattern از آن‌ها استفاده کرد. همچنین تمام فیلدهای مبلغ، مانده، تاریخ، زمان و کارت را با named group مچ کند.")
        }

        val aiResult = aiRepository.chat(prompt)
        val jsonText = aiResult.getOrNull()

        // Attempt to parse AI JSON response
        var parserResult = jsonText?.let { parseAiResponseToParser(sender, it) }

        if (parserResult == null) {
            // Robust local fallback parser generation in case AI response is null or malformed
            Log.w("SmsParserRepository", "AI response failed or invalid. Generating robust local fallback parser...")
            parserResult = generateFallbackParser(sender, smsList)
        }

        return Result.success(parserResult)
    }

    // Stage 5: Save Parser and update status
    suspend fun saveParser(parser: BankSmsParser) {
        bankSmsParserDao.insertParser(parser)
        val sender = bankSenderDao.getSenderByNumber(parser.senderNumber)
        if (sender != null) {
            bankSenderDao.updateSender(
                sender.copy(
                    learningStatus = "آموزش دیده",
                    parserVersion = parser.version,
                    lastUpdated = System.currentTimeMillis()
                )
            )
        }
    }

    // Stage 5 & Unknown Format: Learn New/Unknown Format to update parser
    suspend fun learnNewFormatForUnknownSms(
        sender: BankSender,
        currentParser: BankSmsParser,
        unknownSmsBody: String
    ): Result<BankSmsParser> {
        val nextVersion = currentParser.version + 1

        val prompt = buildString {
            append("شما یک موتور هوشمند استخراج الگوهای پیامکی بانک هستید.\n")
            append("یک پیامک ناشناخته جدید دریافت شده که قالب فعلی قادر به پردازش آن نیست.\n")
            append("وظیفه شما ارتقا و به روزرسانی الگوهای قبلی به نسخه جدید برای مچ کردن پیامک جدید در کنار پیامک‌های قبلی است.\n")
            append("خروجی شما باید صرفا یک آبجکت معتبر JSON نسخه جدید بدون هیچگونه توضیح، کدبلاگ یا علامت اضافی باشد.\n\n")
            append("پیامک ناشناخته جدید:\n")
            append("$unknownSmsBody\n\n")
            append("الگوی فعلی (نسخه ${currentParser.version}):\n")
            append("${currentParser.templatesJson}\n\n")
            append("فرمت دقیق خروجی JSON نسخه جدید (نسخه $nextVersion):\n")
            append("{\n")
            append("  \"bank\": \"${sender.bankName}\",\n")
            append("  \"templates\": [\n")
            append("    // الگوهای قبلی به همراه الگوی جدید اصلاح شده برای پیامک ناشناخته\n")
            append("  ]\n")
            append("}\n")
        }

        val aiResult = aiRepository.chat(prompt)
        val jsonText = aiResult.getOrNull()

        var parserResult = jsonText?.let { parseAiResponseToParser(sender, it, nextVersion) }

        if (parserResult == null) {
            Log.w("SmsParserRepository", "AI update failed. Performing local update fallback...")
            parserResult = generateUpdatedFallbackParser(sender, currentParser, unknownSmsBody, nextVersion)
        }

        return Result.success(parserResult)
    }

    // Offline parsing of incoming messages
    suspend fun processIncomingSms(senderNumber: String, body: String): Boolean {
        val sender = bankSenderDao.getSenderByNumber(senderNumber)
        if (sender == null || !sender.isEnabled) {
            return false
        }

        val hash = md5(body)
        if (pendingTransactionDao.isDuplicate(hash)) {
            Log.i("SmsParserRepository", "SMS duplicate detected. Skipping processing.")
            return true
        }

        val parser = bankSmsParserDao.getLatestParserForSender(senderNumber)
        if (parser == null) {
            // No parser learned yet. Move to unknown
            unknownSmsDao.insertUnknownSms(
                UnknownSms(
                    senderNumber = senderNumber,
                    smsBody = body,
                    timestamp = System.currentTimeMillis()
                )
            )
            return false
        }

        val extracted = parseLocalSms(body, parser)
        if (extracted != null) {
            val amount = extracted["amount"]?.replace(",", "")?.replace("٫", "")?.toLongOrNull() ?: 0L
            val card = extracted["card"] ?: "نامشخص"
            val type = extracted["type"] ?: "purchase"
            val isExpense = type != "deposit" && type != "salary"

            val pTx = PendingTransaction(
                senderNumber = senderNumber,
                amount = amount,
                date = System.currentTimeMillis(),
                isExpense = isExpense,
                card = card,
                title = "تراکنش پیامکی ${sender.bankName}",
                smsBody = body,
                smsBodyHash = hash,
                status = "در انتظار بررسی"
            )
            pendingTransactionDao.insertPendingTransaction(pTx)
            return true
        } else {
            // Move to Unknown format database
            unknownSmsDao.insertUnknownSms(
                UnknownSms(
                    senderNumber = senderNumber,
                    smsBody = body,
                    timestamp = System.currentTimeMillis()
                )
            )
            return false
        }
    }

    private fun parseLocalSms(body: String, parser: BankSmsParser): Map<String, String>? {
        try {
            val templatesArray = JSONArray(parser.templatesJson)
            for (i in 0 until templatesArray.length()) {
                val template = templatesArray.getJSONObject(i)
                val regexStr = template.getString("regex")
                val type = template.optString("type", "purchase")
                val fieldsObj = template.getJSONObject("fields")

                val pattern = java.util.regex.Pattern.compile(regexStr, java.util.regex.Pattern.CASE_INSENSITIVE)
                val matcher = pattern.matcher(body)
                if (matcher.find()) {
                    val result = mutableMapOf<String, String>()
                    result["type"] = type

                    val keys = fieldsObj.keys()
                    while (keys.hasNext()) {
                        val fieldName = keys.next()
                        val groupKey = fieldsObj.getString(fieldName)
                        try {
                            val index = groupKey.toIntOrNull()
                            val value = if (index != null) {
                                matcher.group(index)
                            } else {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    matcher.group(groupKey)
                                } else {
                                    null
                                }
                            }
                            if (value != null) {
                                result[fieldName] = value
                            }
                        } catch (e: Exception) {
                            try {
                                val value = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    matcher.group(fieldName)
                                } else {
                                    null
                                }
                                if (value != null) {
                                    result[fieldName] = value
                                }
                            } catch (ex: Exception) {
                                // Field not found
                            }
                        }
                    }
                    if (result.containsKey("amount")) {
                        return result
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SmsParserRepository", "Error executing local regex parsing", e)
        }
        return null
    }

    private fun parseAiResponseToParser(
        sender: BankSender,
        jsonText: String,
        version: Int = 1
    ): BankSmsParser? {
        try {
            // Extract raw JSON if AI wrapped it in markdown codeblocks
            var cleanedJson = jsonText.trim()
            if (cleanedJson.startsWith("```")) {
                cleanedJson = cleanedJson.substringAfter("```json").substringAfter("```")
                cleanedJson = cleanedJson.substringBeforeLast("```").trim()
            }

            val obj = JSONObject(cleanedJson)
            val templates = obj.getJSONArray("templates")
            
            // Validate regex compilability before saving
            for (i in 0 until templates.length()) {
                val template = templates.getJSONObject(i)
                val regexStr = template.getString("regex")
                java.util.regex.Pattern.compile(regexStr)
            }

            return BankSmsParser(
                senderNumber = sender.senderNumber,
                bankName = sender.bankName,
                version = version,
                templatesJson = templates.toString(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                checksum = md5(templates.toString())
            )
        } catch (e: Exception) {
            Log.e("SmsParserRepository", "Failed to parse AI output JSON or Regex is invalid", e)
            return null
        }
    }

    private fun generateFallbackParser(sender: BankSender, smsList: List<String>): BankSmsParser {
        val templates = JSONArray()

        // Detect templates from the list of messages or use standard resilient patterns
        val purchaseObj = JSONObject().apply {
            put("type", "purchase")
            put("regex", "(?i)(?:برداشت|خريد|پرداخت|انتقال).*?(?:مبلغ|مبلغ:)?\\s*(?<amount>[\\d,٫]+)\\s*(?:ريال|تومان)?.*?کارت\\s*(?<card>\\d+)")
            put("fields", JSONObject().apply {
                put("amount", "amount")
                put("card", "card")
            })
        }
        templates.put(purchaseObj)

        val depositObj = JSONObject().apply {
            put("type", "deposit")
            put("regex", "(?i)(?:واريز|حواله|کارت به کارت).*?(?:مبلغ|مبلغ:)?\\s*(?<amount>[\\d,٫]+)\\s*(?:ريال|تومان)?.*?کارت\\s*(?<card>\\d+)")
            put("fields", JSONObject().apply {
                put("amount", "amount")
                put("card", "card")
            })
        }
        templates.put(depositObj)

        // Resilient universal generic parser to extract numbers
        val genericObj = JSONObject().apply {
            put("type", "purchase")
            put("regex", "(?<amount>[\\d,٫]{4,15})")
            put("fields", JSONObject().apply {
                put("amount", "amount")
            })
        }
        templates.put(genericObj)

        return BankSmsParser(
            senderNumber = sender.senderNumber,
            bankName = sender.bankName,
            version = 1,
            templatesJson = templates.toString(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            checksum = md5(templates.toString())
        )
    }

    private fun generateUpdatedFallbackParser(
        sender: BankSender,
        currentParser: BankSmsParser,
        unknownSmsBody: String,
        nextVersion: Int
    ): BankSmsParser {
        try {
            val templates = JSONArray(currentParser.templatesJson)

            // Inject a highly custom regex for the unknown body to ensure it gets matched successfully!
            val parsedAmount = extractNumbers(unknownSmsBody)
            val parsedCard = extractCardNumber(unknownSmsBody)

            val customObj = JSONObject().apply {
                put("type", if (unknownSmsBody.contains("واریز") || unknownSmsBody.contains("حواله")) "deposit" else "purchase")
                // Escape regex matches for the specific body
                val escapedBody = java.util.regex.Pattern.quote(unknownSmsBody)
                    .replace(parsedAmount, "(?<amount>[\\d,٫]+)")
                    .run {
                        if (parsedCard.isNotEmpty()) replace(parsedCard, "(?<card>\\d+)") else this
                    }
                put("regex", escapedBody)
                put("fields", JSONObject().apply {
                    put("amount", "amount")
                    if (parsedCard.isNotEmpty()) put("card", "card")
                })
            }
            templates.put(customObj)

            return BankSmsParser(
                senderNumber = sender.senderNumber,
                bankName = sender.bankName,
                version = nextVersion,
                templatesJson = templates.toString(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                checksum = md5(templates.toString())
            )
        } catch (e: Exception) {
            return currentParser.copy(version = nextVersion, updatedAt = System.currentTimeMillis())
        }
    }

    private fun extractNumbers(text: String): String {
        val pattern = java.util.regex.Pattern.compile("[\\d,٫]{4,15}")
        val matcher = pattern.matcher(text)
        return if (matcher.find()) matcher.group() else ""
    }

    private fun extractCardNumber(text: String): String {
        val pattern = java.util.regex.Pattern.compile("\\b\\d{4}\\b")
        val matcher = pattern.matcher(text)
        return if (matcher.find()) matcher.group() else ""
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val byteArray = md.digest(input.toByteArray())
        return byteArray.joinToString("") { "%02x".format(Locale.US, it) }
    }
}
