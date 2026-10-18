package com.flightjoy.android.logging

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

class AppLogger(private val maxEntries: Int = 250) {
    private val entries = ConcurrentLinkedDeque<String>()
    private val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun info(tag: String, message: String) = log("INFO", tag, message)
    fun warn(tag: String, message: String) = log("WARN", tag, message)
    fun error(tag: String, message: String) = log("ERROR", tag, message)

    @Synchronized
    private fun log(level: String, tag: String, message: String) {
        val ts = formatter.format(Date())
        val line = "[$ts] [$level] [$tag] $message"
        entries.addLast(line)
        while (entries.size > maxEntries) {
            entries.pollFirst()
        }
    }

    fun getAllLinesText(): String = entries.joinToString(separator = "\n")

    fun clear() {
        entries.clear()
    }
}
