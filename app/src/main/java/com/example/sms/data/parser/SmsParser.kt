package com.example.sms.data.parser

import com.example.sms.domain.model.ParsedTransactionModel

interface SmsParser {
    /**
     * Parses a bank transaction SMS.
     * Returns a ParsedTransactionModel if successful, or null if the message cannot be parsed.
     */
    fun parse(body: String, bankName: String): ParsedTransactionModel?
}
