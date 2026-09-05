        if (parsed != null && parsed.amount > 0) {
            val finalAmount = parsed.amount / 10L
            val finalBalance = parsed.balance / 10L
            val finalParsed = parsed.copy(amount = finalAmount, balance = finalBalance)
            
            val isExpense = finalParsed.transactionType != "deposit"
            val pendingTx = PendingTransaction(
                senderNumber = sender,
                amount = finalAmount,
                date = finalParsed.date,
                isExpense = isExpense,
                card = finalParsed.cardNumber,
                title = "تراکنش پیامکی ${bankSender.bankName}",
                smsBody = body,
                smsBodyHash = hash,
                status = "در انتظار بررسی"
            )
            pendingTransactionDao.insertPendingTransaction(pendingTx)
            finalParsed
