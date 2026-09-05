package com.example.sms.data.parser

import android.util.Log
import com.example.sms.domain.model.ParsedTransactionModel

class CompositeParser : SmsParser {

    private val parsers = listOf(
        BankSpecificParser(),
        RegexParser(),
        KeywordParser(),
        CopilotParser()
    )

    override fun parse(body: String, bankName: String): ParsedTransactionModel? {
        Log.d("CompositeParser", "Running composite parse for message under bank: $bankName")
        
        for (parser in parsers) {
            val result = parser.parse(body, bankName)
            if (result != null && result.amount > 0) {
                Log.d("CompositeParser", "Parsed successfully with: ${parser::class.simpleName}")
                return result
            }
        }
        
        Log.w("CompositeParser", "All parser strategies failed to extract transaction details.")
        return null
    }
}
