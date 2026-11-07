package com.example.sms.data.engine

import android.content.Context
import android.util.Log
import com.example.sms.engine.BankSmsEngine
import com.example.sms.engine.PatternDatabase

/**
 * One engine for the whole process. The pattern database is a 600 KB asset; it
 * is read and parsed once, on first use, and every SMS after that only runs
 * regexes. If the asset cannot be read the engine is simply empty and parses
 * nothing, rather than taking the receiver down.
 */
object BankSmsEngineProvider {

    @Volatile
    private var instance: BankSmsEngine? = null

    fun get(context: Context): BankSmsEngine {
        instance?.let { return it }
        return synchronized(this) {
            instance ?: BankSmsEngine(load(context.applicationContext)).also { instance = it }
        }
    }

    private fun load(context: Context): PatternDatabase = try {
        context.assets.open(PatternDatabase.ASSET_PATH).bufferedReader(Charsets.UTF_8).use {
            PatternDatabase.parse(it.readText())
        }
    } catch (e: Exception) {
        Log.e("BankSmsEngineProvider", "Could not read ${PatternDatabase.ASSET_PATH}", e)
        PatternDatabase(emptyList())
    }
}
